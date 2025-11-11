package com.example.vtsdaily

import java.text.Normalizer
import java.util.Locale

private val PARENS_OR_BRACKETS = Regex("""\s*[\(\[].*?[\)\]]""")          // remove (WC), [XLWC], etc.
private val NON_NAME_CHARS      = Regex("""[^A-Za-z'\- \p{L}]""")          // keep letters, spaces, apostrophes, hyphens
private val MULTI_SPACE         = Regex("""\s+""")

fun sanitizeName(raw: String): String {
    // strip any parenthetical/bracket tags like (WC), [XLWC], etc.
    var s = raw.replace(PARENS_OR_BRACKETS, "")
    // normalize unicode (é -> e) but keep letters; then drop non-name chars
    s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    s = s.replace(NON_NAME_CHARS, " ")
    s = s.replace(MULTI_SPACE, " ").trim()
    return s
}

fun normalizeNameForLookup(raw: String): String =
    sanitizeName(raw).lowercase(Locale.US)
