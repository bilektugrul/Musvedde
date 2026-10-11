package dev.aliarslan.musvedde.ui.history

enum class DiffType { SAME, ADDED, REMOVED }

data class DiffLine(val type: DiffType, val text: String)

data class DiffStats(val added: Int, val removed: Int)

private const val MAX_TABLE_CELLS = 4_000_000L

private fun String.splitLines(): List<String> = if (isEmpty()) emptyList() else lines()

fun lineDiff(old: String, new: String): List<DiffLine> {
    val a = old.splitLines()
    val b = new.splitLines()
    var prefix = 0
    while (prefix < a.size && prefix < b.size && a[prefix] == b[prefix]) prefix++
    var suffix = 0
    while (suffix < a.size - prefix && suffix < b.size - prefix && a[a.size - 1 - suffix] == b[b.size - 1 - suffix]) suffix++

    val result = ArrayList<DiffLine>(a.size + b.size)
    a.subList(0, prefix).mapTo(result) { DiffLine(DiffType.SAME, it) }
    middleDiff(a.subList(prefix, a.size - suffix), b.subList(prefix, b.size - suffix), result)
    a.subList(a.size - suffix, a.size).mapTo(result) { DiffLine(DiffType.SAME, it) }
    return result
}

fun diffStats(old: String, new: String): DiffStats {
    val diff = lineDiff(old, new)
    return DiffStats(added = diff.count { it.type == DiffType.ADDED }, removed = diff.count { it.type == DiffType.REMOVED })
}

private fun middleDiff(a: List<String>, b: List<String>, out: MutableList<DiffLine>) {
    if (a.size.toLong() * b.size > MAX_TABLE_CELLS) {
        a.mapTo(out) { DiffLine(DiffType.REMOVED, it) }
        b.mapTo(out) { DiffLine(DiffType.ADDED, it) }
        return
    }
    // lcs[i][j] = length of the longest common subsequence of a[i..] and b[j..]
    val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
    for (i in a.indices.reversed()) {
        for (j in b.indices.reversed()) {
            lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
    }
    var i = 0
    var j = 0
    while (i < a.size && j < b.size) {
        when {
            a[i] == b[j] -> {
                out += DiffLine(DiffType.SAME, a[i])
                i++
                j++
            }
            lcs[i + 1][j] >= lcs[i][j + 1] -> out += DiffLine(DiffType.REMOVED, a[i++])
            else -> out += DiffLine(DiffType.ADDED, b[j++])
        }
    }
    while (i < a.size) out += DiffLine(DiffType.REMOVED, a[i++])
    while (j < b.size) out += DiffLine(DiffType.ADDED, b[j++])
}
