package com.emberr.presentation.settings.about

import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class OpenSourceLicenseDefinitionsTest {

    private val libraries: List<Library> = Libs.Builder()
        .withJson(File("src/commonMain/composeResources/files/aboutlibraries.json").readText())
        .build()
        .libraries

    private fun libraryWithId(uniqueId: String): Library? = libraries.firstOrNull { it.uniqueId == uniqueId }

    @Test
    fun includesEveryHandAddedEntry() {
        val handAddedIds = listOf(
            "steveruizok:perfect-freehand",
            "font:bricolage-grotesque",
            "font:fuzzy-bubbles",
            "font:inter",
            "font:jetbrains-mono",
            "font:lora",
            "font:merriweather",
            "font:open-sans",
            "font:poppins",
            "font:yuyu-short",
            "ggml-org:llama.cpp",
            "ggml-org:whisper.cpp",
            "leejet:stable-diffusion.cpp",
            "llvm:openmp"
        )

        handAddedIds.forEach { uniqueId ->
            assertTrue(libraryWithId(uniqueId) != null, "Missing hand-added entry $uniqueId")
        }
    }

    @Test
    fun handAddedEntriesCarryFullLicenseText() {
        val perfectFreehand = libraryWithId("steveruizok:perfect-freehand")
        assertTrue(perfectFreehand?.licenses?.any { it.licenseContent?.contains("Stephen Ruiz Ltd") == true } == true)

        val lora = libraryWithId("font:lora")
        assertTrue(lora?.licenses?.any { it.licenseContent?.contains("SIL OPEN FONT LICENSE Version 1.1") == true } == true)
    }

    @Test
    fun sqlCipherCarriesItsLicenseText() {
        val sqlCipher = libraryWithId("net.zetetic:sqlcipher-android")
        assertTrue(sqlCipher != null, "SQLCipher is missing, so Android dependencies were not collected")
        assertTrue(sqlCipher.licenses.any { it.licenseContent?.contains("ZETETIC LLC") == true })
    }

    @Test
    fun javaKeyringCarriesItsLicenseText() {
        val javaKeyring = libraryWithId("com.github.javakeyring:java-keyring")
        assertTrue(javaKeyring?.licenses?.any { it.licenseContent?.contains("Rex Hoffman") == true } == true)
    }

    @Test
    fun librariesWithGenericLicenseTemplatesCarryTheirRealCopyrightLine() {
        val expectedCopyrightHolderByLibrary = mapOf(
            "org.jsoup:jsoup" to "Jonathan Hedley",
            "org.slf4j:slf4j-api" to "QOS.ch",
            "com.github.hypfvieh:dbus-java-core" to "David M.",
            "com.github.hypfvieh:dbus-java-transport-native-unixsocket" to "David M.",
            "de.swiesend:secret-service" to "Sebastian Wiesendahl",
            "pt.davidafsilva.apple:jkeychain" to "Conor McDermottroe",
            "androidx.camera:camera-core" to "The LibYuv Project Authors",
            "androidx.glance:glance-appwidget-external-protobuf" to "Google Inc."
        )

        expectedCopyrightHolderByLibrary.forEach { (uniqueId, copyrightHolder) ->
            val library = libraryWithId(uniqueId)
            assertTrue(
                library?.licensesToShow()?.any { it.licenseContent?.contains(copyrightHolder) == true } == true,
                "$uniqueId is missing the copyright line for $copyrightHolder"
            )
        }
    }

    @Test
    fun includesDesktopOnlyDependencies() {
        assertTrue(libraries.any { it.uniqueId.startsWith("io.netty:") }, "Netty is missing, so desktop dependencies were not collected")
    }
}
