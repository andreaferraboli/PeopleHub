
## 2026-09-29 - Promemoria: storico completo di ogni tap su Fatto
- Ogni tap su "Fatto" (bottone in app o azione della notifica) aggiunge una riga allo storico reminder_completion invece di ignorare i tap successivi dello stesso giorno: prima c'era una riga per giorno, ora una per tap con data e ora (done_epoch_millis).
- L'ultima volta fatto e il conteggio si ricavano dallo storico, quindi cadenza e UI esistenti continuano a funzionare. Toccando il riepilogo sulla card si apre l'elenco di tutti i tap (stringhe IT/EN).
- Migrazione Room v9->v10 (core/database/.../Migrations.kt): aggiunge la colonna, toglie il vincolo unique dall'indice, non cancella nessuna riga. L'ora dell'ultimo giorno si recupera da last_fired_epoch_millis se cade nello stesso giorno locale, i giorni piu vecchi restano solo con la data. Schema 10.json esportato.
- File: ReminderCompletionEntity, ReminderDao, Mappers, ReminderRepositoryImpl, model Reminder, ReminderRepository, ReminderUseCases (+test), RemindersListScreen/ViewModel, ReminderDoneReceiver, strings, CLAUDE.md. Nuovo MigrationTest (androidTest) e sourceSets assets in core/database/build.gradle.kts.
- Verifica: ./gradlew :app:assembleDebug test ktlintCheck detekt :core:database:assembleDebugAndroidTest -> BUILD SUCCESSFUL (lanciato con heap ridotto e --max-workers=1: con i 4 GB di default la macchina andava in OOM). SQL della migrazione provato con sqlite3 su un DB costruito da 9.json: nessuna riga persa.
- Tentativo fallito: la prima build e morta per mancanza di memoria, la seconda si e fermata su ktlint in MigrationTest (catena di chiamate su una riga), corretto.
- Aperto: MigrationTest compilato ma non eseguito (serve device o emulatore). Backup/export non include promemoria e storico, lacuna gia esistente prima di questo lavoro.

## 2026-10-24 - Redesign "Velluto" di tutta l'app (1.7.0)
- Ridisegno solo visivo dai mockup approvati (artifact JYM37ktWrLnRLDST9LErVc, People-*.dc.html): logica, dati, navigazione e stringhe invariate. Token chiaro/scuro con accento unico, font inclusi (Instrument Serif + Geist, OFL), icone Lucide, oggetti 3D come VectorDrawable generati da tools/scenes, componenti condivisi e shell con barra flottante in vetro. Regole per chi tocca la UI in DESIGN.md.
- Schermate: le 5 tab (Riflessione, La Cerchia, Cronologia, Promemoria, La Cassaforte), dettaglio e modifica persona, uscite e calendario uscite, compleanni, dettaglio e form evento, form promemoria, guida import, lingua, dialog aggiornamento, ritaglio foto, widget.
- File: core/ui (theme/, components/, modifier/, icon/VellutoIcons.kt, res/font, res/drawable/scene_*), app/ (shell, dashboard, settings), feature/* (schermate), tools/scenes, DESIGN.md, app/build.gradle.kts (fallback 1.7.0).
- Non replicato: blur reale solo sulla nav bar e solo da Android 12 (Haze), sotto superficie traslucida; drop-shadow SVG diventa ellisse d'ombra, animazioni interne degli oggetti diventano un galleggiamento dell'oggetto intero.
- Verifica (locale, --max-workers=1, heap 1.5 GB): ktlintCheck, detekt, test (77, 0 falliti), :app:assembleDebug tutti BUILD SUCCESSFUL. Lint verde ovunque tranne feature:reminders per LocalDate.EPOCH in RemindersListViewModel.kt:65 (API 34, minSdk 26), gia presente su master e non toccato: lint non era nei controlli prima.
- Corretto durante la verifica: SuspiciousCompositionLocalModifierRead in core/ui/.../modifier/Entrance.kt (offset letto in measure, lettura una tantum in onAttach documentata).
- Tentativi: screenshot JVM delle 5 tab con Paparazzi 1.3.5 aggiunto in modo temporaneo (non committato): il render esce quasi vuoto perche riseIn parte da alpha 0 e layoutlib non fa avanzare l'animazione; da rifare forzando motion ridotto nel test.
- Interruzioni: il capo filone e stato ucciso due volte (OOM alle 12:45 con 12 agenti, riavvio tmux alle 13:59), ripreso dai commit e dal lavoro non committato.
- PR #2 verso master (squash), release 1.7.0 dal workflow release.yml lanciato a mano con version=1.7.0.
- Aperto: correggere LocalDate.EPOCH (puo rompere i Promemoria su Android < 14); verifica visiva su dispositivo reale, chiaro e scuro, con "rimuovi animazioni" acceso e spento.
