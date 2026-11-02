package com.peoplehub.core.database.entity

import java.util.UUID

/**
 * Draws the cloud identity of a new row: a random (v4) UUID in its canonical lowercase
 * 8-4-4-4-12 form, the same shape the v11 migration backfills in SQL.
 *
 * Entities default their `remoteId` to a fresh one, which is right for inserts. An update built
 * from a domain model (which does not carry the remote id) must copy the stored value over instead,
 * otherwise the cloud would see the row as a new document and the old one would never be deleted:
 * the repositories do that through each DAO's `remoteIdOf`.
 */
fun newRemoteId(): String = UUID.randomUUID().toString()
