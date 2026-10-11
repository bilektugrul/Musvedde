package dev.aliarslan.musvedde.data.note

fun toFtsQuery(raw: String): String? {
    val tokens = raw.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return null
    return tokens.joinToString(" ") { "\"$it*\"" }
}
