package com.peoplehub.core.dataio

import com.peoplehub.core.domain.model.Person
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val DELIMITER = ','
private const val QUOTE = '"'
private const val EXPECTED_FIELD_COUNT = 3
private const val HEADER_LINE = "nome,cognome,data_nascita"
private val ISO_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val SLASH_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * Imports and exports a simple birthday CSV (`nome,cognome,data`) with no external CSV library.
 *
 * Parsing is RFC-4180-ish: fields may be wrapped in double quotes, an embedded `""` is an escaped
 * quote, and quoted fields may contain commas. Both `yyyy-MM-dd` and `dd/MM/yyyy` date formats are
 * accepted. A malformed row is reported as an error but does not abort the whole file.
 */
class CsvBirthdaySupport
    @Inject
    constructor() {
        /**
         * Parses [csv] into successfully read people plus per-line error messages.
         *
         * A leading header row (one whose first field contains "nome" or "name", case-insensitive) is
         * skipped. Blank lines are ignored. Each remaining row must have exactly three fields
         * (first name, last name, birthday); the birthday must be a valid `yyyy-MM-dd` or `dd/MM/yyyy`
         * date. Imported people have their id left at `0`.
         */
        fun parse(csv: String): CsvParseResult {
            val people = mutableListOf<Person>()
            val errors = mutableListOf<String>()

            val rawLines = csv.split(Regex("\\r\\n|\\r|\\n"))
            var headerConsumed = false

            rawLines.forEachIndexed { index, rawLine ->
                if (rawLine.isBlank()) return@forEachIndexed

                val fields = tokenize(rawLine)

                if (!headerConsumed && isHeader(fields)) {
                    headerConsumed = true
                    return@forEachIndexed
                }
                headerConsumed = true

                val lineNumber = index + 1
                parseRow(fields, lineNumber)
                    .onSuccess(people::add)
                    .onFailure { errors.add(it.message ?: "Malformed row at line $lineNumber") }
            }

            return CsvParseResult(people = people, errors = errors)
        }

        /**
         * Exports [people] as a CSV with a `nome,cognome,data_nascita` header.
         *
         * Only people who have a birthday are included; the birthday is written as `yyyy-MM-dd`. Fields
         * containing a comma, quote or newline are wrapped in double quotes with inner quotes escaped.
         */
        fun export(people: List<Person>): String {
            val builder = StringBuilder()
            builder.append(HEADER_LINE).append('\n')
            people.forEach { person ->
                val birthday = person.birthday ?: return@forEach
                builder
                    .append(encodeField(person.firstName))
                    .append(DELIMITER)
                    .append(encodeField(person.lastName))
                    .append(DELIMITER)
                    .append(birthday.format(ISO_DATE_FORMATTER))
                    .append('\n')
            }
            return builder.toString()
        }

        private fun parseRow(fields: List<String>, lineNumber: Int): Result<Person> =
            runCatching {
                require(fields.size == EXPECTED_FIELD_COUNT) {
                    "Line $lineNumber: expected $EXPECTED_FIELD_COUNT fields but found ${fields.size}"
                }
                val firstName = fields[0].trim()
                val lastName = fields[1].trim()
                val rawDate = fields[2].trim()
                require(firstName.isNotBlank()) { "Line $lineNumber: name is blank" }
                // Last name is optional: many people are tracked by a single name or nickname.
                val birthday =
                    parseDate(rawDate)
                        ?: throw IllegalArgumentException("Line $lineNumber: invalid date '$rawDate'")
                Person(firstName = firstName, lastName = lastName, birthday = birthday)
            }

        private fun parseDate(value: String): LocalDate? =
            runCatching { LocalDate.parse(value, ISO_DATE_FORMATTER) }.getOrNull()
                ?: runCatching { LocalDate.parse(value, SLASH_DATE_FORMATTER) }.getOrNull()

        private fun isHeader(fields: List<String>): Boolean {
            val first = fields.firstOrNull()?.trim()?.lowercase() ?: return false
            return first.contains("nome") || first.contains("name")
        }

        /**
         * Splits a single CSV line into fields, honouring double-quoted sections (which may contain
         * commas) and `""` escaped quotes.
         */
        private fun tokenize(line: String): List<String> {
            val fields = mutableListOf<String>()
            val current = StringBuilder()
            var inQuotes = false
            var index = 0

            while (index < line.length) {
                val char = line[index]
                when {
                    inQuotes && char == QUOTE && line.getOrNull(index + 1) == QUOTE -> {
                        current.append(QUOTE)
                        index++
                    }
                    char == QUOTE -> inQuotes = !inQuotes
                    char == DELIMITER && !inQuotes -> {
                        fields.add(current.toString())
                        current.setLength(0)
                    }
                    else -> current.append(char)
                }
                index++
            }
            fields.add(current.toString())
            return fields
        }

        private fun encodeField(value: String): String =
            if (value.any { it == DELIMITER || it == QUOTE || it == '\n' || it == '\r' }) {
                "$QUOTE${value.replace("$QUOTE", "$QUOTE$QUOTE")}$QUOTE"
            } else {
                value
            }
    }
