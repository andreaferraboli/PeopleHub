# PeopleHub MCP

Server MCP remoto per PeopleHub: da Claude (claude.ai, app desktop e mobile) leggi e modifichi persone, compleanni,
check-in e uscite, eventi e promemoria, gli stessi dati che l'app Android sincronizza su Firestore. Accesso con
l'account Google dell'app (Firebase Auth). Ricalca il connettore di Finance Hub (`finance-hub/mcp`).

- URL del connettore: **`https://people-mcp.2-24-13-98.sslip.io/mcp`**.
- Piattaforma: la VPS. Processo Node (`/usr/bin/node`, un solo file `server.mjs` impacchettato da esbuild) come servizio
  systemd `peoplehub-mcp`, utente di sistema `peoplemcp`, in ascolto su `127.0.0.1:18750`, esposto dal Traefik gia attivo
  (rotta `peoplehub-mcp`, certificato Let's Encrypt automatico).
- Trasporto: Streamable HTTP senza stato. OAuth 2.1 con `@cloudflare/workers-oauth-provider` (PKCE S256, registrazione
  dinamica dei client, refresh con rotazione); stato OAuth in SQLite `/var/lib/peoplehub-mcp/oauth-kv.sqlite`.
- Dati: Firestore di `peoplehub-af26` via API REST con il service account `firebase-adminsdk-fbsvc`, sempre e solo sotto
  `users/{uid}` del token. Contratto dei documenti: `docs/firestore-schema.md` (id UUID, `updatedAt`, `serverUpdatedAt`
  con REQUEST_TIME, `deleted` come tombstone, `updatedBy: "mcp"`; le letture escludono i `deleted`).
- Niente assistente AI, niente basi di conoscenza, nessuna chiamata a modelli.

## Collegarlo a Claude

1. Claude: Impostazioni, Connettori, **Aggiungi connettore personalizzato**.
2. Nome: PeopleHub. URL: `https://people-mcp.2-24-13-98.sslip.io/mcp`. Campi OAuth avanzati vuoti.
3. Nella pagina che si apre: **Accedi con Google** con un account in allowlist, controlla nome del client e host di
   ritorno, **Autorizza**.

## Tool

Date in ingresso e in uscita ISO (`YYYY-MM-DD`) nel fuso di `settings/main.timeZone` (default `Europe/Rome`); gli
istanti in ISO 8601 con scarto. Le scritture usano sempre id (UUID), mai nomi.

Lettura: `get_profile`, `list_people`, `get_person`, `list_tags`, `list_overdue_people`, `upcoming_birthdays`,
`list_outings`, `list_events`, `list_reminders`, `get_settings`, `raw_read`.

Scrittura: `create_person`, `update_person`, `delete_person`, `check_in`, `update_outing`, `delete_outing`,
`create_event`, `update_event`, `delete_event`, `create_reminder`, `update_reminder`, `delete_reminder`,
`mark_reminder_done`, `update_settings`.

Annotazioni MCP: i tool di lettura sono `readOnlyHint`; `delete_*` e `update_outing` (puo togliere persone) sono
`destructiveHint`.

Regole portate dal Kotlin (`core/domain`), con i casi dei test Kotlin in `test/domain.test.ts`:
- compleanni (`DateCalculations.kt`): prossima ricorrenza, giorni mancanti, eta; il 29 febbraio cade il 28 negli anni
  non bisestili; anno <= 1 = eta sconosciuta; `birthday` salvato come `ISO_LOCAL_DATE`;
- check-in (`CheckInStatus`, `GetUrgentCheckInsUseCase`): FRESH/DUE/OVERDUE/NEVER, soglie della persona solo se ha
  entrambi i valori, esclusi famiglia, check-in disattivati e solo compleanno; un'uscita e' l'insieme dei check-in con
  lo stesso `outingId`, i giorni registrati stanno a mezzogiorno nel fuso; `lastCheckInAt` si ricalcola dopo ogni modifica;
- promemoria (`ReminderScheduling.kt`, `ReminderUseCases.kt`): finestra `target +- target*jitter%`, intervallo estratto a
  caso, nuova data solo se cambia la cadenza (dalla `lastFiredAt` o da adesso), "fatto" = completamento
  (`day` epoch day nel fuso + `at`) e nuova data da adesso;
- cancellazioni: sempre tombstone. `delete_person` marca anche check-in, promemoria e completamenti della persona e
  scollega gli eventi.

## Cosa protegge

- **Allowlist** `ALLOWED_EMAILS` (`/etc/peoplehub-mcp/env`): solo email in elenco, il jolly `*` non vale. ID token
  Firebase verificato (firma, issuer e audience `peoplehub-af26`), email verificata, provider `google.com`, login di meno
  di 10 minuti; a ogni richiesta `/mcp` si ricontrollano allowlist, provider ed eta della sessione (`MAX_SESSION_DAYS`).
- **Redirect** solo verso i callback di Claude; token legati a `<origine>/mcp`; CSP stretta sulla pagina di login;
  rate limit per IP sulle rotte OAuth; origine fissa da `PUBLIC_URL`.
- **Confini dei dati**: il client Firestore rifiuta ogni percorso fuori da `users/{uid}/`; `raw_read` accetta solo percorsi
  relativi validati.
- **Segreti** solo in `/etc/peoplehub-mcp/google-sa.json` e `/etc/peoplehub-mcp/admin-token` (root, 600), passati con
  `LoadCredential`; mai nel repo. Servizio con lo stesso hardening di Finance Hub (`systemd-analyze security`: 1.5 OK).

## Deploy e verifica (sulla VPS, da root)

```bash
bash mcp/deploy/install.sh        # build, test, utente, servizio, rotta Traefik, dominio autorizzato Firebase Auth
bash mcp/deploy/update.sh         # git pull --ff-only + install.sh
bash mcp/deploy/verify.sh         # servizio, discovery OAuth, 401 su /mcp, DCR, pagina di login
bash mcp/deploy/verify.sh --read  # + letture con un token coniato su un uid di test (PH_UID, default mcp-smoke-test)
bash mcp/deploy/verify.sh --write # + crea, rilegge e cancella (tombstone) una persona e un check-in sull'uid di test
```

Allowlist: modifica `ALLOWED_EMAILS` e `systemctl restart peoplehub-mcp`. Revoca: `node mcp/scripts/revoke.mjs --url
https://people-mcp.2-24-13-98.sslip.io --email <email>` (oppure `--uid`, `--all`, `--mint-only`).
Log: `journalctl -u peoplehub-mcp -f`. Togliere la rotta: `docker compose -p peoplehub-mcp-route -f
/opt/peoplehub-mcp/route/docker-compose.yml down`.

## Sviluppo

```bash
cd mcp && npm ci
npm run typecheck && npm run lint
npm test            # test senza emulatore (dominio, percorsi, server HTTP con flusso OAuth completo)
npm run test:emu    # tutti, con Firestore emulator (progetto demo-ph-mcp, mai dati veri)
```
