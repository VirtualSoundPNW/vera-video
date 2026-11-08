/**
 * AGP and its tooling put Guava 33.4.0 on the build classpath, which is affected
 * by GHSA-xxph-c9ww-hj94. Force the patched version from the catalog (see the
 * `guava` entry in libs.versions.toml); `:app` does the same for its own
 * configurations.
 */
buildscript {
    configurations.classpath {
        resolutionStrategy.force(libs.guava.get().toString())
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
