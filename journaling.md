
## 2026-09-29 - Promemoria: storico completo di ogni tap su Fatto
- Ogni tap su "Fatto" (bottone in app o azione della notifica) aggiunge una riga allo storico reminder_completion invece di ignorare i tap successivi dello stesso giorno: prima c'era una riga per giorno, ora una per tap con data e ora (done_epoch_millis).
- L'ultima volta fatto e il conteggio si ricavano dallo storico, quindi cadenza e UI esistenti continuano a funzionare. Toccando il riepilogo sulla card si apre l'elenco di tutti i tap (stringhe IT/EN).
- Migrazione Room v9->v10 (core/database/.../Migrations.kt): aggiunge la colonna, toglie il vincolo unique dall'indice, non cancella nessuna riga. L'ora dell'ultimo giorno si recupera da last_fired_epoch_millis se cade nello stesso giorno locale, i giorni piu vecchi restano solo con la data. Schema 10.json esportato.
- File: ReminderCompletionEntity, ReminderDao, Mappers, ReminderRepositoryImpl, model Reminder, ReminderRepository, ReminderUseCases (+test), RemindersListScreen/ViewModel, ReminderDoneReceiver, strings, CLAUDE.md. Nuovo MigrationTest (androidTest) e sourceSets assets in core/database/build.gradle.kts.
- Verifica: ./gradlew :app:assembleDebug test ktlintCheck detekt :core:database:assembleDebugAndroidTest -> BUILD SUCCESSFUL (lanciato con heap ridotto e --max-workers=1: con i 4 GB di default la macchina andava in OOM). SQL della migrazione provato con sqlite3 su un DB costruito da 9.json: nessuna riga persa.
- Tentativo fallito: la prima build e morta per mancanza di memoria, la seconda si e fermata su ktlint in MigrationTest (catena di chiamate su una riga), corretto.
- Aperto: MigrationTest compilato ma non eseguito (serve device o emulatore). Backup/export non include promemoria e storico, lacuna gia esistente prima di questo lavoro.
