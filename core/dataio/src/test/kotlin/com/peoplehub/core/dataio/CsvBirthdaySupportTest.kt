package com.peoplehub.core.dataio

import com.peoplehub.core.domain.model.Person
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CsvBirthdaySupportTest {

    private val support = CsvBirthdaySupport()

    @Test
    fun `parses a well-formed CSV with header`() {
        val csv = """
            nome,cognome,data
            Ada,Lovelace,1815-12-10
            Alan,Turing,1912-06-23
            Grace,Hopper,1906-12-09
        """.trimIndent()

        val result = support.parse(csv)

        assertEquals(0, result.errors.size, "no errors expected")
        assertEquals(3, result.people.size)
        assertEquals(LocalDate.of(1815, 12, 10), result.people[0].birthday)
        assertEquals("Ada", result.people[0].firstName)
        assertEquals("Lovelace", result.people[0].lastName)
        assertEquals(LocalDate.of(1906, 12, 9), result.people[2].birthday)
    }

    @Test
    fun `keeps valid rows when one date is malformed`() {
        val csv = """
            nome,cognome,data
            Ada,Lovelace,1815-12-10
            Bad,Row,not-a-date
            Grace,Hopper,1906-12-09
        """.trimIndent()

        val result = support.parse(csv)

        assertEquals(2, result.people.size, "valid rows survive a bad row")
        assertEquals(1, result.errors.size, "the bad row is reported once")
        assertEquals(listOf("Ada", "Grace"), result.people.map(Person::firstName))
    }

    @Test
    fun `imports rows with an empty last name`() {
        val csv = """
            nome,cognome,data_nascita
            doni,,1986-02-13
            amore,,2003-06-02
        """.trimIndent()

        val result = support.parse(csv)

        assertEquals(0, result.errors.size, "an empty last name is allowed")
        assertEquals(2, result.people.size)
        assertEquals("doni", result.people[0].firstName)
        assertEquals("", result.people[0].lastName)
        assertEquals(LocalDate.of(1986, 2, 13), result.people[0].birthday)
    }

    @Test
    fun `parses a quoted field containing a comma`() {
        val csv = """
            nome,cognome,data
            John,"Smith, Jr",1990-05-04
        """.trimIndent()

        val result = support.parse(csv)

        assertEquals(0, result.errors.size)
        assertEquals(1, result.people.size)
        assertEquals("Smith, Jr", result.people[0].lastName)
        assertEquals(LocalDate.of(1990, 5, 4), result.people[0].birthday)
    }

    @Test
    fun `accepts the dd-MM-yyyy date format`() {
        val csv = """
            nome,cognome,data
            Marie,Curie,07/11/1867
        """.trimIndent()

        val result = support.parse(csv)

        assertEquals(0, result.errors.size)
        assertEquals(LocalDate.of(1867, 11, 7), result.people[0].birthday)
    }

    @Test
    fun `export round-trips and omits people without a birthday`() {
        val people = listOf(
            Person(id = 1L, firstName = "Ada", lastName = "Lovelace", birthday = LocalDate.of(1815, 12, 10)),
            Person(id = 2L, firstName = "No", lastName = "Birthday", birthday = null),
            Person(id = 3L, firstName = "Grace", lastName = "Hopper", birthday = LocalDate.of(1906, 12, 9)),
        )

        val csv = support.export(people)
        val result = support.parse(csv)

        assertEquals(0, result.errors.size)
        assertEquals(2, result.people.size, "the birthday-less person is omitted")
        assertEquals(
            listOf("Ada", "Grace"),
            result.people.map(Person::firstName),
        )
        assertEquals(LocalDate.of(1815, 12, 10), result.people[0].birthday)
        assertEquals(LocalDate.of(1906, 12, 9), result.people[1].birthday)
    }

    @Test
    fun `export quotes fields containing a comma`() {
        val people = listOf(
            Person(id = 1L, firstName = "John", lastName = "Smith, Jr", birthday = LocalDate.of(1990, 5, 4)),
        )

        val csv = support.export(people)

        assertTrue(csv.contains("\"Smith, Jr\""), "a comma-bearing field must be quoted")
        val result = support.parse(csv)
        assertEquals("Smith, Jr", result.people[0].lastName)
    }
}
