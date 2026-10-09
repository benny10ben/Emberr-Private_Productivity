package com.emberr.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.emberr.core.desktop.DesktopAppStorage
import com.emberr.database.EmberrDatabase
import java.io.File

class DatabaseDriverFactory {
    fun createDriver(): SqlDriver {
        val appDir = DesktopAppStorage.emberrDirectory
        appDir.mkdirs()

        val dbFile = File(appDir, "emberr_ai_index.db")
        val isNewDatabase = !dbFile.exists()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

        if (isNewDatabase) {
            EmberrDatabase.Schema.create(driver)
        }

        return driver
    }
}