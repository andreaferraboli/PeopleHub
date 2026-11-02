# Firestore schema (app + MCP contract)

Project: `peoplehub-af26`. Everything lives under `users/{uid}/`, where `uid` is the Firebase Auth
uid of the Google account signed in on the phone. This is the **only** copy of the data: the Android
app reads and writes these documents directly (snapshot listeners, memory-only cache, no local
database, nothing to sync), and the MCP server reads and writes the same documents. This file is the
contract between them: change it here first.

## Ids

- The document id is a UUID string, lowercase, and it is also the id the app uses everywhere
  (navigation, notifications, widgets).
- References between documents use those ids (`personId`, `reminderId`, `outingId`).

## Sync fields (on every document)

| Field | Type | Meaning |
|---|---|---|
| `updatedAt` | number (epoch millis, writer clock) | last-write-wins comparison |
| `serverUpdatedAt` | timestamp (server time) | pull cursor: clients fetch `serverUpdatedAt > cursor` |
| `deleted` | boolean, always present | tombstone; deleted documents are kept, never removed |
| `updatedBy` | string | `android:<installId>` or `mcp` |

Both writers keep stamping all four fields. With no sync engine left, `updatedAt` and
`serverUpdatedAt` are informational (the app no longer pulls by cursor), but they stay part of the
contract. Writes merge into the document (`set` with merge), so a writer never wipes fields it does
not know about; the last write of a field wins.

**Deletions are tombstones**: `deleted: true` on a document that is kept. Every read filters
`deleted == false` (the app with a query, the MCP in memory). Tombstones stay because the MCP server
and any other reader rely on them, and because a deleted id is never reused.

## Collections

### `people/{id}`
| Field | Type | Meaning |
|---|---|---|
| `firstName` | string | |
| `lastName` | string | |
| `birthday` | string or null | ISO date `YYYY-MM-DD` |
| `notes` | string | |
| `tags` | string[] | |
| `interests` | `{key, value}[]` | |
| `warningDays` | number or null | per-person threshold (both or neither) |
| `criticalDays` | number or null | |
| `notificationsEnabled` | boolean | check-in reminders for this person |
| `birthdayOnly` | boolean | bare birthday, not a tracked relationship |
| `checkInDisabled` | boolean | excluded from check-in tracking |
| `isFamily` | boolean | |
| `createdAt` | number (epoch millis) | |
| `lastCheckInAt` | number or null | written by both clients after every check-in change; the app shows the value computed from the live check-ins and uses this one only for a person without any |
| `hasPhoto` | boolean | whether the phone that saved the person had a photo; photos are never uploaded |

### `checkIns/{id}`
| Field | Type | Meaning |
|---|---|---|
| `personId` | string | the person seen |
| `at` | number (epoch millis) | |
| `note` | string or null | |
| `outingId` | string (UUID) | check-ins of the same outing share it; a check-in without it is an outing of its own |

### `events/{id}`
| Field | Type | Meaning |
|---|---|---|
| `title` | string | |
| `at` | number (epoch millis) | |
| `description` | string or null | |
| `category` | string or null | |
| `personId` | string or null | linked person; set to null when that person is deleted |

The background image, its crop and the widget pin stay on the phone, keyed by the event id.

### `reminders/{id}`
| Field | Type | Meaning |
|---|---|---|
| `personId` | string | |
| `title` | string | |
| `note` | string or null | |
| `category` | string | `LOVE`, `FRIENDSHIP`, `FAMILY` or `CUSTOM` |
| `targetIntervalDays` | number | |
| `jitterPercent` | number | |
| `enabled` | boolean | |
| `lastFiredAt` | number or null | written by the phone when it notifies |
| `nextFireAt` | number | |
| `createdAt` | number | |
| `presetKey` | string or null | |

### `reminderCompletions/{id}`
| Field | Type | Meaning |
|---|---|---|
| `reminderId` | string | |
| `day` | number (epoch day) | the local day of the tap |
| `at` | number or null | the exact instant (null for old taps that kept only the day) |

### `settings/main`
| Field | Type | Meaning |
|---|---|---|
| `warningDays` | number | global check-in thresholds |
| `criticalDays` | number | |
| `birthdayOffsets` | number[] | days before a birthday to notify |
| `dailyReminderHour` | number | hour of every daily sweep (0-23) |
| `timeZone` | string | written by the phone with every settings change (IANA id, default `Europe/Rome`); the MCP uses it for day maths |

The app reads these four values from here (defaults when the document is missing). Device-only
settings (exact alarms, sweep dates, language, widget pin) stay on the phone.

## Security rules

`firestore.rules`: a signed-in user reads and writes only `users/{their uid}/**`. The MCP server uses
a service account and enforces the same scoping in code.

## Notes on the Android client

- It reads only through snapshot listeners on `deleted == false` (one per collection, shared), with
  a memory-only cache: nothing is persisted on the phone. Its first answer waits for the server, so a
  cold start offline shows "loading" rather than an empty list. Writes made offline stay queued in
  memory and are sent when the connection returns, as long as the app process lives.
- Ids it creates are lowercase UUIDs, 8-4-4-4-12 (`UUID.randomUUID()`). It accepts any string id.
- Deletions cascade like the MCP's `delete_person`: deleting a person tombstones their check-ins,
  their reminders and those reminders' completions, and sets `personId: null` on their events;
  deleting a reminder tombstones its completions. Writes go out in batches of at most 400.
- Merging a field into a document also writes `deleted: false`, so the app only merges partial
  updates (flags, `lastCheckInAt`, `nextFireAt`) into documents it still sees as live.
- `birthdayOffsets` values the app understands are 0, 1, 3, 7 and 30; others are ignored.
- **One active phone per account** is assumed: reminders fire and are rescheduled on the phone, and
  two phones on the same account would each notify and move `nextFireAt` independently.
- Photos and event backgrounds are not uploaded; `hasPhoto` only says the phone that saved the person
  had one. The phone keeps a map document id -> local file.
