# Firestore schema (cloud sync + MCP contract)

Project: `peoplehub-af26`. Everything lives under `users/{uid}/`, where `uid` is the Firebase Auth
uid of the Google account signed in on the phone. The Android sync engine and the MCP server both
read and write these documents, so this file is the contract between them: change it here first.

## Ids

- The document id is the row's `remote_id` (UUID string, lowercase). Local Room `Long` ids never
  leave the phone.
- References between documents use remote ids (`personId`, `reminderId`, `outingId`).

## Sync fields (on every document)

| Field | Type | Meaning |
|---|---|---|
| `updatedAt` | number (epoch millis, writer clock) | last-write-wins comparison |
| `serverUpdatedAt` | timestamp (server time) | pull cursor: clients fetch `serverUpdatedAt > cursor` |
| `deleted` | boolean, always present | tombstone; deleted documents are kept, never removed |
| `updatedBy` | string | `android:<installId>` or `mcp` |

Conflict rule: last write wins on the whole document. A remote document is ignored by the phone if a
local pending change for the same row is newer than its `updatedAt`.

## Collections

### `people/{id}`
| Field | Type | Room column |
|---|---|---|
| `firstName` | string | `person.first_name` |
| `lastName` | string | `person.last_name` |
| `birthday` | string or null | `person.birthday`, same string format as Room |
| `notes` | string | `person.notes` |
| `tags` | string[] | `person_tag.tag` rows |
| `interests` | `{key, value}[]` | `interest` rows |
| `warningDays` | number or null | `person.warning_days` |
| `criticalDays` | number or null | `person.critical_days` |
| `notificationsEnabled` | boolean | `person.notifications_enabled` |
| `birthdayOnly` | boolean | `person.birthday_only` |
| `checkInDisabled` | boolean | `person.check_in_disabled` |
| `isFamily` | boolean | `person.is_family` |
| `createdAt` | number (epoch millis) | `person.created_at_epoch_millis` |
| `lastCheckInAt` | number or null | informative copy; the phone recomputes it from check-ins |
| `hasPhoto` | boolean | `person.photo_path != null`; photos themselves are not synced |

### `checkIns/{id}`
| Field | Type | Room column |
|---|---|---|
| `personId` | string | `check_in.person_id` (remote id) |
| `at` | number (epoch millis) | `check_in.timestamp_epoch_millis` |
| `note` | string or null | `check_in.note` |
| `outingId` | string (UUID) | `check_in.outing_remote_id`; check-ins of the same outing share it |

### `events/{id}`
| Field | Type | Room column |
|---|---|---|
| `title` | string | `event.title` |
| `at` | number (epoch millis) | `event.date_time_epoch_millis` |
| `description` | string or null | `event.description` |
| `category` | string or null | `event.category` |
| `personId` | string or null | `event.person_id` (remote id) |

Background image fields and `pinned_to_widget` stay on the phone.

### `reminders/{id}`
| Field | Type | Room column |
|---|---|---|
| `personId` | string | `reminder.person_id` (remote id) |
| `title` | string | `reminder.title` |
| `note` | string or null | `reminder.note` |
| `category` | string | `reminder.category` |
| `targetIntervalDays` | number | `reminder.target_interval_days` |
| `jitterPercent` | number | `reminder.jitter_percent` |
| `enabled` | boolean | `reminder.enabled` |
| `lastFiredAt` | number or null | `reminder.last_fired_epoch_millis` |
| `nextFireAt` | number | `reminder.next_fire_epoch_millis` |
| `createdAt` | number | `reminder.created_epoch_millis` |
| `presetKey` | string or null | `reminder.preset_key` |

### `reminderCompletions/{id}`
| Field | Type | Room column |
|---|---|---|
| `reminderId` | string | `reminder_completion.reminder_id` (remote id) |
| `day` | number (epoch day) | `reminder_completion.done_epoch_day` |
| `at` | number or null | `reminder_completion.done_epoch_millis` |

### `settings/main`
| Field | Type | Source (DataStore `peoplehub_settings`) |
|---|---|---|
| `warningDays` | number | `warning_days` |
| `criticalDays` | number | `critical_days` |
| `birthdayOffsets` | number[] | `reminder_offsets_v2` |
| `dailyReminderHour` | number | `daily_reminder_hour` |
| `timeZone` | string | written by the phone (IANA id, default `Europe/Rome`); the MCP uses it for day maths |

Device-only settings (exact alarms, sweep dates, language, widget pin) are not synced.

## Security rules

`firestore.rules`: a signed-in user reads and writes only `users/{their uid}/**`. The MCP server uses
a service account and enforces the same scoping in code.

## Notes on the Android client

- Ids it creates are lowercase UUIDs, 8-4-4-4-12 (`UUID.randomUUID()` for new rows, the same shape
  generated in SQL when the v11 migration backfilled existing rows). It accepts any string id coming
  from the cloud.
- It pulls in the order people, reminders, events, checkIns, reminderCompletions. A child whose
  parent document is unknown is retried for three rounds, then dropped (an event is kept without its
  person). Tombstoning a person does not need to tombstone its children for the phone (it deletes
  them by cascade), but other readers will see them unless the writer tombstones them too.
- `birthdayOffsets` values the app understands are 0, 1, 3, 7 and 30; others are ignored.
- **One active phone per account** is assumed: reminders fire and are rescheduled on the phone, and
  two phones on the same account would each notify and move `nextFireAt` independently.
- Photos and event backgrounds are not synced; `hasPhoto` only says the phone has one.
