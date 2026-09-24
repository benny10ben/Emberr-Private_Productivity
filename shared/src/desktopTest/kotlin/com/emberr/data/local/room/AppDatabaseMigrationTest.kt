package com.emberr.data.local.room

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class AppDatabaseMigrationTest {

    private val schemaDirectory = Path.of("schemas")
    private val workingDirectory = Files.createTempDirectory("emberr-database-migration")

    @AfterTest
    fun deleteWorkingDirectory() {
        workingDirectory.toFile().deleteRecursively()
    }

    @Test
    fun everyExportedSchemaUpgradesToTheNewestOne() {
        val exportedVersions = exportedSchemaVersions()
        assertTrue(exportedVersions.isNotEmpty(), "No exported schemas in ${schemaDirectory.toAbsolutePath()}")
        val newestVersion = exportedVersions.max()

        exportedVersions.forEach { oldVersion ->
            val migrationTestHelper = MigrationTestHelper(
                schemaDirectoryPath = schemaDirectory,
                databasePath = workingDirectory.resolve("upgraded-from-$oldVersion.db"),
                driver = BundledSQLiteDriver(),
                databaseClass = AppDatabase::class
            )
            migrationTestHelper.createDatabase(oldVersion).close()
            migrationTestHelper.runMigrationsAndValidate(newestVersion, emptyList()).close()
        }
    }

    private fun exportedSchemaVersions(): List<Int> =
        schemaDirectory
            .resolve(checkNotNull(AppDatabase::class.qualifiedName))
            .listDirectoryEntries("*.json")
            .mapNotNull { schemaFile -> schemaFile.fileName.toString().removeSuffix(".json").toIntOrNull() }
}
