package org.veraproject.veravideo.data.local

/**
 * Turns raw user input into a safe FTS4 MATCH expression.
 *
 * This is not cosmetic: FTS4 MATCH has its own syntax, and passing user text
 * through unescaped makes ordinary input throw. A lone `"` is an unterminated
 * phrase, `-` and `*` are operators, and `AND`/`OR`/`NOT` are keywords — all of
 * which a person could reasonably type into a search box.
 *
 * Input is split on runs of non-alphanumerics and each token gets a `*` suffix,
 * so searching matches on prefixes as you type. Tokens are ANDed, which is what
 * people expect from adding words.
 *
 * Splitting on punctuation rather than only whitespace also makes "all-ages"
 * match the tag "all ages": stripping the hyphen in place would instead produce
 * "allages", which matches nothing. Letters and digits are matched by Unicode
 * category, so accented band names survive.
 */
object FtsQuery {

    fun sanitize(input: String): String? {
        val tokens = input
            .split(NON_ALPHANUMERIC)
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) return null

        return tokens.joinToString(" ") { "$it*" }
    }

    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
}
