package com.peoplehub.io

import android.content.Context
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Reads a user-picked document as text in a way that is robust to the encodings real files arrive
 * in. Android's default charset is UTF-8, but import files are frequently authored on Windows and
 * carry either a UTF-8 byte-order mark or are saved as Windows-1252 (ANSI) — both of which turn
 * accented characters (à, è, ò, é, ü, …) into mojibake when read naively.
 *
 * The bytes are decoded with strict UTF-8 first; if that fails (i.e. the file is not valid UTF-8)
 * we fall back to Windows-1252, which round-trips the Latin-1/ANSI files most non-UTF-8 editors
 * produce. A leading UTF-8 BOM is stripped so it never leaks into the parsed text.
 */
fun readTextRobust(context: Context, uri: Uri): String? =
    runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        decodeText(bytes)
    }.getOrNull()

/** Decodes raw file bytes to text, stripping a UTF-8 BOM and falling back to Windows-1252. */
internal fun decodeText(raw: ByteArray): String {
    val bytes =
        if (raw.size >= 3 && raw[0] == 0xEF.toByte() && raw[1] == 0xBB.toByte() && raw[2] == 0xBF.toByte()) {
            raw.copyOfRange(3, raw.size)
        } else {
            raw
        }
    val decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
    return runCatching { decoder.decode(ByteBuffer.wrap(bytes)).toString() }
        .getOrElse { String(bytes, charset("windows-1252")) }
}
