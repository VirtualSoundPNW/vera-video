import java.util.Properties

plugins {
    // No `kotlin.android`: AGP 9 compiles Kotlin itself (built-in Kotlin support).
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "org.veraproject.veravideo"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.veraproject.veravideo"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 3
        versionName = "0.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        // Credentials come from a gitignored keystore.properties (local dev) or,
        // failing that, environment variables (CI). The config is only registered
        // when all four are present, so a checkout without the keystore — CI that
        // hasn't injected secrets, a fresh clone — still builds; its release APK
        // is simply left unsigned rather than failing configuration.
        val keystorePropertiesFile = rootProject.file("keystore.properties")
        val props = Properties().apply {
            if (keystorePropertiesFile.exists()) {
                keystorePropertiesFile.inputStream().use { load(it) }
            }
        }
        val storePath = props.getProperty("storeFile") ?: System.getenv("VERA_KEYSTORE_FILE")
        val storePw = props.getProperty("storePassword") ?: System.getenv("VERA_KEYSTORE_PASSWORD")
        val alias = props.getProperty("keyAlias") ?: System.getenv("VERA_KEY_ALIAS")
        val keyPw = props.getProperty("keyPassword") ?: System.getenv("VERA_KEY_PASSWORD")
        if (storePath != null && storePw != null && alias != null && keyPw != null) {
            create("release") {
                storeFile = rootProject.file(storePath)
                storePassword = storePw
                keyAlias = alias
                keyPassword = keyPw
            }
        }
    }

    buildTypes {
        debug {
            // Point at a locally running `wrangler dev` from an emulator:
            //   -Pvera.catalog.baseUrl=http://10.0.2.2:8787/
            buildConfigField("String", "CATALOG_BASE_URL", "\"${catalogBaseUrl()}\"")
        }
        release {
            // Null when no credentials were provided (see signingConfigs above),
            // leaving the release APK unsigned instead of breaking the build.
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "CATALOG_BASE_URL", "\"${catalogBaseUrl()}\"")
            optimization {
                enable = true
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        // Lint suggests merging mipmap-anydpi-v26 into mipmap-anydpi because
        // minSdk is already 26. Taking that advice makes AGP's resource merger
        // silently drop the adaptive icons and the build then fails with
        // "resource mipmap/ic_launcher not found". -v26 is the convention the
        // tooling actually supports; don't "fix" this.
        disable += "ObsoleteSdkInt"
    }


    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

/**
 * Robolectric resolves its ~200 MB `android-all` jar under `user.home/.m2` and
 * builds the path to it from a URL that it never decodes. A home directory
 * containing spaces therefore becomes a literal `%20` path, and every
 * Robolectric test dies with "Unable to load Robolectric native runtime
 * library".
 *
 * Windows keeps an 8.3 short name for the same directory with no spaces, so
 * pointing the test JVM's `user.home` at it fixes the lookup without moving
 * anyone's Maven cache. On any path without spaces (Linux CI, most setups)
 * this does nothing.
 */
val spaceFreeUserHome: Provider<String> = run {
    val home = System.getProperty("user.home")
    if (!home.contains(" ") || !System.getProperty("os.name").startsWith("Windows")) {
        provider { home }
    } else {
        providers.exec {
            commandLine("cmd", "/c", "for %I in (\"$home\") do @echo %~sI")
        }.standardOutput.asText.map { it.trim().ifBlank { home } }
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("user.home", spaceFreeUserHome.get())
}

/**
 * AGP's Unified Test Platform (used only when running instrumented tests, e.g.
 * `connectedDebugAndroidTest`) pulls in Netty 4.1.110.Final transitively via
 * gRPC. That version is affected by several advisories fixed in later 4.1.x
 * releases (CORS Vary-header cache poisoning, Bzip2/Lz4 decoder resource
 * exhaustion, zip-bomb DoS). `netty-codec-http` and `netty-codec` are
 * separate Maven coordinates, so Gradle resolves each independently and
 * forcing one does not bump the other. Netty keeps the 4.1.x line binary
 * compatible, so force the patched version everywhere both show up rather
 * than waiting on AGP to bump its own pin.
 */
configurations.all {
    resolutionStrategy.force(
        "io.netty:netty-codec-http:4.1.137.Final",
        "io.netty:netty-codec:4.1.137.Final",
    )
}

/** Read from gradle.properties so the backend URL is not hardcoded in source. */
fun catalogBaseUrl(): String =
    (project.findProperty("vera.catalog.baseUrl") as String?)?.takeIf { it.isNotBlank() }
        ?: error("vera.catalog.baseUrl must be set in gradle.properties")

room {
    // Exports the schema so migrations can be reviewed and tested.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.okhttp.logging)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.youtube.player)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.work.testing)
    debugImplementation(libs.compose.ui.test.manifest)
}
