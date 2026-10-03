package com.emberr.presentation.settings.about

import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License
import kotlin.test.Test
import kotlin.test.assertEquals

class LicensesToShowTest {

    private val genericMitTemplate = License(
        name = "MIT License",
        url = "https://spdx.org/licenses/MIT.html",
        spdxId = "MIT",
        licenseContent = "Copyright (c) <year> <copyright holders>",
        hash = "MIT"
    )

    private val realMitLicense = License(
        name = "MIT License",
        url = "https://github.com/jhy/jsoup/blob/master/LICENSE",
        spdxId = "MIT",
        licenseContent = "Copyright (c) 2009-2026 Jonathan Hedley",
        hash = "jsoup-mit"
    )

    private val apacheLicense = License(
        name = "Apache License 2.0",
        url = "https://spdx.org/licenses/Apache-2.0.html",
        spdxId = "Apache-2.0",
        licenseContent = "Apache License Version 2.0",
        hash = "Apache-2.0"
    )

    private fun libraryWith(vararg licenses: License) = Library(
        uniqueId = "example:library",
        artifactVersion = null,
        name = "Example",
        description = null,
        website = null,
        developers = emptyList(),
        organization = null,
        scm = null,
        licenses = licenses.toSet()
    )

    @Test
    fun genericTemplateIsHiddenWhenRealCopyOfSameLicenseExists() {
        val shown = libraryWith(genericMitTemplate, realMitLicense).licensesToShow()

        assertEquals(listOf("jsoup-mit"), shown.map { it.hash })
    }

    @Test
    fun genericTemplateIsKeptWhenItIsTheOnlyCopy() {
        val shown = libraryWith(genericMitTemplate).licensesToShow()

        assertEquals(listOf("MIT"), shown.map { it.hash })
    }

    @Test
    fun differentLicensesAreAllKept() {
        val shown = libraryWith(apacheLicense, realMitLicense).licensesToShow()

        assertEquals(setOf("Apache-2.0", "jsoup-mit"), shown.map { it.hash }.toSet())
    }

    @Test
    fun licenseWithoutTextOrLinkIsHidden() {
        val emptyLicense = License(name = "BSD-3", url = null, hash = "empty-bsd")

        val shown = libraryWith(emptyLicense, realMitLicense).licensesToShow()

        assertEquals(listOf("jsoup-mit"), shown.map { it.hash })
    }

    @Test
    fun licenseWithNameAndLinkButNoTextIsKept() {
        val linkOnlyLicense = License(
            name = "Eclipse Public License - Version 1.0",
            url = "http://www.eclipse.org/org/documents/epl-v10.php",
            hash = "epl-1.0"
        )

        val shown = libraryWith(apacheLicense, linkOnlyLicense).licensesToShow()

        assertEquals(setOf("Apache-2.0", "epl-1.0"), shown.map { it.hash }.toSet())
    }
}
