package org.veraproject.veravideo.data.local

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FtsQueryTest {

    @Test
    fun `appends a prefix wildcard so results appear while typing`() {
        assertThat(FtsQuery.sanitize("chast")).isEqualTo("chast*")
    }

    @Test
    fun `ands multiple terms together`() {
        assertThat(FtsQuery.sanitize("chastity belt")).isEqualTo("chastity* belt*")
    }

    @Test
    fun `collapses extra whitespace`() {
        assertThat(FtsQuery.sanitize("  chastity   belt  ")).isEqualTo("chastity* belt*")
    }

    @Test
    fun `blank input means no search`() {
        assertThat(FtsQuery.sanitize("")).isNull()
        assertThat(FtsQuery.sanitize("   ")).isNull()
    }

    // Everything below would be FTS MATCH syntax if passed through, and would
    // throw at query time rather than simply not matching.
    @Test
    fun `strips a lone double quote that would open an unterminated phrase`() {
        assertThat(FtsQuery.sanitize("\"")).isNull()
    }

    @Test
    fun `strips quotes from around a term`() {
        assertThat(FtsQuery.sanitize("\"chastity belt\"")).isEqualTo("chastity* belt*")
    }

    @Test
    fun `treats a leading hyphen as text rather than the NOT operator`() {
        assertThat(FtsQuery.sanitize("-belt")).isEqualTo("belt*")
    }

    @Test
    fun `strips FTS operators that a user might type`() {
        assertThat(FtsQuery.sanitize("belt^2")).isEqualTo("belt* 2*")
        assertThat(FtsQuery.sanitize("(belt)")).isEqualTo("belt*")
        assertThat(FtsQuery.sanitize("belt:")).isEqualTo("belt*")
    }

    @Test
    fun `punctuation-only input means no search`() {
        assertThat(FtsQuery.sanitize("!!!")).isNull()
        assertThat(FtsQuery.sanitize("- \" *")).isNull()
    }

    @Test
    fun `keeps digits`() {
        assertThat(FtsQuery.sanitize("2026 show")).isEqualTo("2026* show*")
    }

    // FTS keywords are only special when bare and uppercase; lowercasing is not
    // this function's job, but they must not be dropped either.
    @Test
    fun `keeps words that happen to be FTS keywords as searchable text`() {
        assertThat(FtsQuery.sanitize("belt AND vera")).isEqualTo("belt* AND* vera*")
    }

    // Splitting on the hyphen is what lets "all-ages" match the tag "all ages";
    // stripping it in place would give "allages", which matches nothing.
    @Test
    fun `splits a hyphenated word into two terms`() {
        assertThat(FtsQuery.sanitize("all-ages")).isEqualTo("all* ages*")
    }

    @Test
    fun `keeps accented letters so band names stay searchable`() {
        assertThat(FtsQuery.sanitize("Sigur Rós")).isEqualTo("Sigur* Rós*")
    }
}
