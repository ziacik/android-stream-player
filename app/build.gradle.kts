import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val torrServerAbi = providers.gradleProperty("torrserverAbi").orElse("arm64-v8a")
val torrServerAbis = providers.gradleProperty("torrserverAbis")
    .map { value ->
        value.split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
    }
    .orElse(torrServerAbi.map { listOf(it) })
val tmdbApiKey = providers.gradleProperty("tmdbApiKey")
    .orElse(providers.environmentVariable("TMDB_API_KEY"))
    .orElse("")
val torrServerAssets = mapOf(
    "arm64-v8a" to Pair(
        "TorrServer-android-arm64",
        "23cea145c38e948f1a967c7fdbcb9c71506cd21a2fe7b3723903e233a323465b",
    ),
    "armeabi-v7a" to Pair(
        "TorrServer-android-arm7",
        "9bab078a0976b86ff392c9eee756194643f4e939ee2c9504dfd4ab7094ef9490",
    ),
)
val generatedTorrServerJniLibsDir = layout.buildDirectory
    .dir("generated/torrserver/jniLibs")
    .get()
    .asFile
val releaseKeystoreFile = providers.environmentVariable("KINO_KEYSTORE_FILE")
val releaseKeystorePassword = providers.environmentVariable("KINO_KEYSTORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("KINO_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("KINO_KEY_PASSWORD")
val releaseVersionName = providers.environmentVariable("KINO_VERSION_NAME").orElse("0.1.0")
val releaseVersionCode = providers.environmentVariable("KINO_VERSION_CODE").orElse("1")
val releaseSigningConfigured = listOf(
    releaseKeystoreFile.orNull,
    releaseKeystorePassword.orNull,
    releaseKeyAlias.orNull,
    releaseKeyPassword.orNull,
).all { !it.isNullOrBlank() }

val prepareTorrServerBinary = tasks.register("prepareTorrServerBinary") {
    notCompatibleWithConfigurationCache("Downloads and verifies the pinned TorrServer release asset")
    inputs.property("torrserverAbis", torrServerAbis)
    outputs.dir(generatedTorrServerJniLibsDir)

    doLast {
        val abis = torrServerAbis.get().distinct()
        val unsupportedAbis = abis.filterNot(torrServerAssets::containsKey)
        if (unsupportedAbis.isNotEmpty()) {
            throw GradleException("Unsupported TorrServer ABI(s): ${unsupportedAbis.joinToString()}")
        }

        generatedTorrServerJniLibsDir.listFiles()
            ?.filter { it.name !in abis }
            ?.forEach { staleAbiDir ->
                if (!staleAbiDir.deleteRecursively()) {
                    throw GradleException("Could not remove stale TorrServer ABI directory: $staleAbiDir")
                }
            }

        fun sha256(file: java.io.File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }

        abis.forEach { abi ->
            val (assetName, expectedSha256) = torrServerAssets.getValue(abi)
            val outputDir = generatedTorrServerJniLibsDir.resolve(abi)
            val outputFile = outputDir.resolve("libtorrserver.so")

            if (outputFile.isFile && sha256(outputFile) == expectedSha256) {
                logger.lifecycle("Using cached TorrServer $abi binary")
                return@forEach
            }

            outputDir.mkdirs()
            val temporaryFile = outputDir.resolve("libtorrserver.so.tmp")
            temporaryFile.delete()
            outputFile.delete()

            val url = URI(
                "https://github.com/YouROK/TorrServer/releases/download/MatriX.143/$assetName",
            ).toURL()
            logger.lifecycle("Downloading TorrServer $assetName")
            url.openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 120_000
            }.getInputStream().buffered().use { input ->
                temporaryFile.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }

            val actualSha256 = sha256(temporaryFile)
            if (actualSha256 != expectedSha256) {
                temporaryFile.delete()
                throw GradleException(
                    "TorrServer SHA-256 mismatch for $assetName: expected $expectedSha256, got $actualSha256",
                )
            }

            if (!temporaryFile.renameTo(outputFile)) {
                temporaryFile.copyTo(outputFile, overwrite = true)
                temporaryFile.delete()
            }
            logger.lifecycle("Prepared TorrServer $abi at ${outputFile.absolutePath}")
        }
    }
}

android {
    namespace = "sk.ziacik.androidstreamplayer"
    compileSdk = 36

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = file(requireNotNull(releaseKeystoreFile.orNull))
                storePassword = releaseKeystorePassword.orNull
                keyAlias = releaseKeyAlias.orNull
                keyPassword = releaseKeyPassword.orNull
            }
        }
    }

    defaultConfig {
        applicationId = "sk.ziacik.androidstreamplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode.get().toInt()
        versionName = releaseVersionName.get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "TMDB_API_KEY", "\"${tmdbApiKey.get()}\"")
    }

    buildTypes {
        getByName("release") {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    sourceSets {
        getByName("main").jniLibs.srcDir(generatedTorrServerJniLibsDir)
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += setOf("**/libtorrserver.so")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val verifyTmdbApiKey = tasks.register("verifyTmdbApiKey") {
    doLast {
        require(tmdbApiKey.get().isNotBlank()) {
            "TMDB_API_KEY/tmdbApiKey is required for release builds"
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyTmdbApiKey)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(prepareTorrServerBinary)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json.jvm)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
