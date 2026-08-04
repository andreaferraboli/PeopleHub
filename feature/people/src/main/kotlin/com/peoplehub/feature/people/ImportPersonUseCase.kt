package com.peoplehub.feature.people

import com.peoplehub.core.dataio.PersonJsonImporter
import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.repository.CheckInRepository
import com.peoplehub.core.domain.usecase.UpsertPersonUseCase
import java.time.ZoneId
import javax.inject.Inject

/**
 * Imports a single person profile from an app-schema JSON document: parse and validate via the
 * data-io layer, then persist. Parsing and persistence failures are surfaced as a [Result].
 */
class ImportPersonUseCase
    @Inject
    constructor(
        private val importer: PersonJsonImporter,
        private val upsertPerson: UpsertPersonUseCase,
        private val checkInRepository: CheckInRepository,
    ) {
        /** Parses [json] into a candidate [Person] without persisting it (for a confirmation preview). */
        fun preview(json: String): Result<Person> = importer.parse(json)

        /**
         * Merges [json] onto [existing] without persisting it (for a confirmation preview). Keys absent
         * from the JSON keep their stored value; the id is preserved.
         */
        fun previewMerge(json: String, existing: Person): Result<Person> = importer.merge(json, existing)

        /**
         * Persists an already-previewed [person]. When [sourceJson] is supplied (the freshly-imported
         * new-person flow), the meetup history embedded in that document is restored too, reattached to
         * the newly-inserted person. It is intentionally omitted on merge-updates so re-importing a file
         * onto an existing person never duplicates their history.
         */
        suspend fun confirm(person: Person, sourceJson: String? = null): Result<Person> =
            upsertPerson(person).mapCatching { id ->
                val history = sourceJson?.let(importer::parseCheckIns).orEmpty()
                if (history.isNotEmpty()) {
                    checkInRepository.recordCheckIns(history.map { it.copy(personId = id) }.withFreshOutingIds())
                }
                person
            }

        /**
         * Re-keys the imported history onto outing ids this database has never used.
         *
         * The ids in the file are meaningless here: they were handed out by whichever install exported
         * it, so keeping them would either fuse the imported check-ins into an unrelated local outing
         * that happens to share a number, or — for files written before outings existed, where every id
         * is `0` — collapse the person's whole history into one bogus outing spanning every date in it.
         * Rows that shared an outing in the source still share one here; legacy `0` rows are grouped by
         * day and description, the same rule the v8 migration and the backup import apply.
         */
        private suspend fun List<CheckIn>.withFreshOutingIds(): List<CheckIn> {
            var nextOutingId = checkInRepository.newOutingId()
            val allocated = HashMap<String, Long>()
            return map { checkIn ->
                val groupKey =
                    if (checkIn.outingId > 0L) {
                        "id:${checkIn.outingId}"
                    } else {
                        val day = checkIn.timestamp.atZone(ZoneId.systemDefault()).toLocalDate()
                        "legacy:$day|${checkIn.note.orEmpty()}"
                    }
                checkIn.copy(outingId = allocated.getOrPut(groupKey) { nextOutingId++ })
            }
        }
    }
