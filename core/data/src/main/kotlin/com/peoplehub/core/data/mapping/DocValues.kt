package com.peoplehub.core.data.mapping

/**
 * Typed reads from a Firestore field map. Firestore hands numbers back as `Long` or `Double`
 * whatever was written, and a document written by another client (the MCP server) may lack a field,
 * so every read is lenient and returns `null` instead of throwing.
 */
internal fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()

internal fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

internal fun Map<String, Any?>.string(key: String): String? = this[key] as? String

internal fun Map<String, Any?>.bool(key: String): Boolean? = this[key] as? Boolean

internal fun Map<String, Any?>.list(key: String): List<Any?> = (this[key] as? List<*>).orEmpty()
