package com.emberr.domain.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.AppDatabase
import com.emberr.data.local.room.entity.DEFAULT_SPACE_ID
import com.emberr.data.local.room.entity.NoteKind
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.database.EmberrDatabase
import com.emberr.domain.ai.LocalAiEngine
import com.emberr.domain.ai.NoteIndexer
import com.emberr.domain.ai.external.AiSettingsRepository
import com.emberr.domain.database.DatabaseCellPreset
import com.emberr.domain.database.DatabaseRow
import com.emberr.domain.database.HistoryDirection
import com.emberr.domain.database.LinkableDatabase
import com.emberr.domain.database.databaseNoteId
import com.emberr.domain.database.databaseSettingsBlockId
import com.emberr.domain.database.RepeatedRowToCreate
import com.emberr.domain.database.databaseCellBlockId
import com.emberr.domain.database.repeatedRowNoteId
import com.emberr.domain.model.CanvasBlock
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NoteContent
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.TextBlock
import com.emberr.domain.space.ActiveSpaceStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteRepositoryDatabaseRowsTest {

    private val appDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
    private val aiIndexDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EmberrDatabase.Schema.create(it) }

    private val settings: SettingsManager = stubOf { methodName ->
        when (methodName) {
            "getActiveSpaceId" -> DEFAULT_SPACE_ID
            "getActiveSpaceIdFlow" -> flowOf(DEFAULT_SPACE_ID)
            "isAiFeaturesDisabled" -> true
            else -> error("These tests do not expect SettingsManager.$methodName to be called")
        }
    }
    private val aiSettingsThatAreNeverRead: AiSettingsRepository = stubOf { methodName ->
        error("These tests do not expect AiSettingsRepository.$methodName to be called")
    }

    private val repository = NoteRepositoryImpl(
        activeSpaceStore = ActiveSpaceStore(settings),
        noteDao = appDatabase.noteDao(),
        folderDao = appDatabase.folderDao(),
        blockDao = appDatabase.blockDao(),
        noteIndexer = NoteIndexer(EmberrDatabase(aiIndexDriver), LocalAiEngine(aiSettingsThatAreNeverRead), settings),
        calendarTaskDao = appDatabase.calendarTaskDao(),
        calendarEventExceptionDao = appDatabase.calendarEventExceptionDao(),
        imageBlockDao = appDatabase.imageBlockDao(),
        documentBlockDao = appDatabase.documentBlockDao(),
        bookmarkBlockDao = appDatabase.bookmarkBlockDao(),
        categoryDao = appDatabase.categoryDao(),
        propertyTagDao = appDatabase.propertyTagDao(),
        customPropertyDao = appDatabase.customPropertyDao(),
        selfHostDeletedNoteDao = appDatabase.selfHostDeletedNoteDao(),
        mediaReferenceDao = appDatabase.mediaReferenceDao(),
        canvasDao = appDatabase.canvasDao()
    )

    private val nameColumn = DatabaseColumnTarget.Property(PropertyType.NAME)
    private val statusColumn = DatabaseColumnTarget.Property(PropertyType.STATUS)
    private val days = DatabaseBlock(id = "days-block", databaseId = "days", title = "Days", columns = listOf(nameColumn), updatedAt = 1L)
    private val daysWithStatus = days.copy(columns = listOf(nameColumn, statusColumn))
    private val linkedDays = DatabaseBlock(id = "linked-days-block", databaseId = "days", isLinkedDatabase = true, updatedAt = 1L)

    @AfterTest
    fun closeDatabases() {
        appDatabase.close()
        aiIndexDriver.close()
    }

    private inline fun <reified T : Any> stubOf(crossinline answer: (methodName: String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { stub, method, arguments ->
            when (method.name) {
                "toString" -> "stub of ${T::class.simpleName}"
                "hashCode" -> System.identityHashCode(stub)
                "equals" -> stub === arguments?.firstOrNull()
                else -> answer(method.name)
            }
        } as T

    private fun waitForTheClockToTick() {
        val startMillis = System.currentTimeMillis()
        while (System.currentTimeMillis() == startMillis) Thread.onSpinWait()
    }

    private suspend fun rowsOf(databaseId: String): List<DatabaseRow> =
        repository.observeDatabaseRows(databaseId).first().sortedBy { it.createdAt }

    private fun note(
        noteId: String,
        title: String,
        databaseId: String? = null,
        createdAt: Long = 1L,
        trashedAt: Long? = null,
        isTemplate: Boolean = false
    ) = NoteMetadataEntity(
        noteId = noteId,
        title = title,
        folderId = null,
        isDaily = false,
        dateString = null,
        createdAt = createdAt,
        updatedAt = createdAt,
        filePath = "",
        trashedAt = trashedAt,
        isSubNote = databaseId != null,
        isTemplate = isTemplate,
        databaseId = databaseId
    )

    private suspend fun saveNote(metadata: NoteMetadataEntity, vararg blocks: NoteBlock) {
        repository.saveNote(metadata, NoteContent(blocks = blocks.toList()))
    }

    private suspend fun saveRow(
        rowNoteId: String,
        databaseId: String,
        title: String,
        name: String,
        createdAt: Long = 1L,
        trashedAt: Long? = null
    ) {
        val nameCell = PropertyBlock(
            id = databaseCellBlockId(nameColumn, rowNoteId),
            propertyType = PropertyType.NAME,
            text = name,
            updatedAt = 1L
        )
        saveNote(note(rowNoteId, title, databaseId, createdAt, trashedAt), nameCell)
    }

    @Test
    fun aNewRowIsAHiddenSubNoteInTheTableWithAnEmptyCellForEachColumn() = runTest {
        val change = repository.createDatabaseRow(days)

        val rows = rowsOf("days")
        val rowNote = repository.getNoteById(change.rowNoteId)

        assertEquals(listOf(change.rowNoteId), rows.map { it.noteId })
        assertEquals("", rows.single().cell(nameColumn)?.text)
        assertEquals(true, rowNote?.isSubNote)
        assertEquals("days", rowNote?.databaseId)
    }

    @Test
    fun editingACellSavesTheValueAndReportsTheValueBeforeAndAfter() = runTest {
        val rowNoteId = repository.createDatabaseRow(days).rowNoteId
        waitForTheClockToTick()

        val change = repository.updateDatabaseCell(days, rowNoteId, nameColumn) { it.copy(text = "Gym") }

        assertEquals("Gym", rowsOf("days").single().cell(nameColumn)?.text)
        assertEquals("", change?.before?.text)
        assertEquals("Gym", change?.after?.text)
    }

    @Test
    fun writingTheSameCellValueAgainIsNotAChange() = runTest {
        val rowNoteId = repository.createDatabaseRow(days).rowNoteId
        waitForTheClockToTick()
        repository.updateDatabaseCell(days, rowNoteId, nameColumn) { it.copy(text = "Gym") }

        val secondChange = repository.updateDatabaseCell(days, rowNoteId, nameColumn) { it.copy(text = "Gym") }

        assertNull(secondChange)
    }

    @Test
    fun addingAColumnGivesEveryRowACellAndRemovingItHidesThemAll() = runTest {
        repository.createDatabaseRow(days)
        repository.createDatabaseRow(days)

        val addChanges = repository.addDatabaseColumnToRows(daysWithStatus, statusColumn)
        val rowsWithStatus = rowsOf("days")
        val removeChanges = repository.removeDatabaseColumnFromRows("days", statusColumn)
        val rowsWithoutStatus = rowsOf("days")

        assertEquals(2, addChanges.size)
        assertTrue(rowsWithStatus.all { it.cell(statusColumn) != null })
        assertEquals(2, removeChanges.size)
        assertTrue(rowsWithoutStatus.all { it.cell(statusColumn) == null })
    }

    @Test
    fun undoingARemovedColumnBringsBackItsValuesAndRedoRemovesThemAgain() = runTest {
        val rowNoteId = repository.createDatabaseRow(daysWithStatus).rowNoteId
        waitForTheClockToTick()
        repository.updateDatabaseCell(daysWithStatus, rowNoteId, statusColumn) { it.copy(tags = listOf("Done")) }
        val removal = repository.removeDatabaseColumnFromRows("days", statusColumn)

        repository.applyDatabaseRowChanges(removal, HistoryDirection.UNDO)
        val statusAfterUndo = rowsOf("days").single().cell(statusColumn)?.tags
        repository.applyDatabaseRowChanges(removal, HistoryDirection.REDO)
        val statusAfterRedo = rowsOf("days").single().cell(statusColumn)

        assertEquals(listOf("Done"), statusAfterUndo)
        assertNull(statusAfterRedo)
    }

    @Test
    fun undoingANewRowTakesItOutOfTheTableAndRedoPutsItBack() = runTest {
        val change = repository.createDatabaseRow(days)

        repository.applyDatabaseRowChanges(listOf(change), HistoryDirection.UNDO)
        val rowsAfterUndo = rowsOf("days")
        repository.applyDatabaseRowChanges(listOf(change), HistoryDirection.REDO)
        val rowsAfterRedo = rowsOf("days")

        assertEquals(emptyList(), rowsAfterUndo)
        assertEquals(listOf(change.rowNoteId), rowsAfterRedo.map { it.noteId })
    }

    @Test
    fun undoingACellEditAndARenameTogetherRestoresBothAndRedoReappliesThem() = runTest {
        val rowNoteId = repository.createDatabaseRow(days).rowNoteId
        waitForTheClockToTick()
        val cellChange = repository.updateDatabaseCell(days, rowNoteId, nameColumn) { it.copy(text = "Gym") }!!
        val titleChange = repository.renameDatabaseRow(rowNoteId, "Monday")!!
        waitForTheClockToTick()

        repository.applyDatabaseRowChanges(listOf(cellChange, titleChange), HistoryDirection.UNDO)
        val rowAfterUndo = rowsOf("days").single()
        waitForTheClockToTick()
        repository.applyDatabaseRowChanges(listOf(cellChange, titleChange), HistoryDirection.REDO)
        val rowAfterRedo = rowsOf("days").single()

        assertEquals("" to "", rowAfterUndo.title to rowAfterUndo.cell(nameColumn)?.text)
        assertEquals("Monday" to "Gym", rowAfterRedo.title to rowAfterRedo.cell(nameColumn)?.text)
    }

    @Test
    fun aNoteMadeFromATemplateGetsItsOwnCopyOfTheLiveRowsInTheSameOrder() = runTest {
        saveRow("tuesday", "days", title = "Tuesday", name = "Rest", createdAt = 20L)
        saveRow("monday", "days", title = "Monday", name = "Gym", createdAt = 10L)
        saveRow("old", "days", title = "Old", name = "Gone", createdAt = 5L, trashedAt = 30L)

        val copiedContent = repository.copyDatabasesIn(NoteContent(blocks = listOf(days)))
        val copiedDatabaseId = (copiedContent.blocks.single() as DatabaseBlock).databaseId
        val copiedRows = rowsOf(copiedDatabaseId)

        assertNotEquals("days", copiedDatabaseId)
        assertEquals(listOf("Monday", "Tuesday"), copiedRows.map { it.title })
        assertEquals(listOf("Gym", "Rest"), copiedRows.map { it.cell(nameColumn)?.text })
        assertTrue(copiedRows.none { it.noteId in setOf("monday", "tuesday", "old") })
    }

    @Test
    fun editingACopiedRowLeavesTheTemplateRowAndOtherCopiesAlone() = runTest {
        saveRow("monday", "days", title = "Monday", name = "Gym")
        val firstCopyId = (repository.copyDatabasesIn(NoteContent(blocks = listOf(days))).blocks.single() as DatabaseBlock).databaseId
        val secondCopyId = (repository.copyDatabasesIn(NoteContent(blocks = listOf(days))).blocks.single() as DatabaseBlock).databaseId
        val firstCopyRowId = rowsOf(firstCopyId).single().noteId

        repository.updateDatabaseCell(days.copy(databaseId = firstCopyId), firstCopyRowId, nameColumn) { it.copy(text = "Swim") }

        assertNotEquals(firstCopyId, secondCopyId)
        assertEquals("Swim", rowsOf(firstCopyId).single().cell(nameColumn)?.text)
        assertEquals("Gym", rowsOf(secondCopyId).single().cell(nameColumn)?.text)
        assertEquals("Gym", rowsOf("days").single().cell(nameColumn)?.text)
    }

    @Test
    fun duplicatingARowAddsANewRowAfterItWithTheSameTitleAndCells() = runTest {
        saveRow("monday", "days", title = "Monday", name = "Gym")

        val change = repository.duplicateDatabaseRow("monday")

        val rows = rowsOf("days")
        assertEquals(listOf("monday", change?.rowNoteId), rows.map { it.noteId })
        assertEquals("Monday", rows.last().title)
        assertEquals("Gym", rows.last().cell(nameColumn)?.text)
        assertEquals(true, change?.isInTable)
    }

    @Test
    fun editingADuplicatedRowLeavesTheOriginalAlone() = runTest {
        saveRow("monday", "days", title = "Monday", name = "Gym")
        val copyRowNoteId = repository.duplicateDatabaseRow("monday")!!.rowNoteId

        repository.updateDatabaseCell(days, copyRowNoteId, nameColumn) { it.copy(text = "Swim") }

        assertEquals(listOf("Gym", "Swim"), rowsOf("days").map { it.cell(nameColumn)?.text })
    }

    @Test
    fun undoingADuplicateTakesTheCopyOutOfTheTable() = runTest {
        saveRow("monday", "days", title = "Monday", name = "Gym")
        val change = repository.duplicateDatabaseRow("monday")!!

        repository.applyDatabaseRowChanges(listOf(change), HistoryDirection.UNDO)

        assertEquals(listOf("monday"), rowsOf("days").map { it.noteId })
    }

    @Test
    fun aTrashedRowOrANoteOutsideADatabaseIsNotDuplicated() = runTest {
        saveRow("monday", "days", title = "Monday", name = "Gym", trashedAt = 5L)
        saveNote(note("plain-note", "Plain"))

        assertNull(repository.duplicateDatabaseRow("monday"))
        assertNull(repository.duplicateDatabaseRow("plain-note"))
    }

    @Test
    fun aRepeatedRowIsMadeOnceFromItsTemplateWithTheSameIdsOnEveryDevice() = runTest {
        val templateBody = TextBlock(id = "template-body", text = "How did today go?", updatedAt = 1L)
        saveNote(note("daily-log", "Daily Log", isTemplate = true), templateBody)
        val rowToCreate = RepeatedRowToCreate(
            rowNoteId = repeatedRowNoteId("days", "daily-log", LocalDate(2026, 10, 4)),
            templateNoteId = "daily-log",
            date = LocalDate(2026, 10, 4)
        )

        val createdFirstTime = repository.createRepeatedDatabaseRow(days, rowToCreate)
        val createdSecondTime = repository.createRepeatedDatabaseRow(days, rowToCreate)

        val row = rowsOf("days").single()
        val rowBody = repository.getNoteContent(row.noteId)?.blocks.orEmpty().filterIsInstance<TextBlock>().single()
        assertEquals(true, createdFirstTime)
        assertEquals(false, createdSecondTime)
        assertEquals("repeat-days-daily-log-2026-10-04", row.noteId)
        assertEquals("Daily Log Oct 4, 2026", row.title)
        assertEquals("repeat-days-daily-log-2026-10-04-template-body", rowBody.id)
        assertEquals("How did today go?", rowBody.text)
    }

    @Test
    fun aRepeatedRowIsNotMadeAgainAfterItWasDeletedOrWhenTheTemplateIsGone() = runTest {
        saveNote(note("daily-log", "Daily Log", isTemplate = true))
        val today = LocalDate(2026, 10, 4)
        val rowToCreate = RepeatedRowToCreate(repeatedRowNoteId("days", "daily-log", today), "daily-log", today)
        repository.createRepeatedDatabaseRow(days, rowToCreate)
        repository.trashDatabaseRow(rowToCreate.rowNoteId)

        val madeAgain = repository.createRepeatedDatabaseRow(days, rowToCreate)
        val madeFromMissingTemplate = repository.createRepeatedDatabaseRow(
            days,
            RepeatedRowToCreate(repeatedRowNoteId("days", "gone", today), "gone", today)
        )

        assertEquals(false, madeAgain)
        assertEquals(false, madeFromMissingTemplate)
        assertEquals(emptyList(), rowsOf("days"))
    }

    @Test
    fun searchFindsARowByItsTitleOrCellsAndNamesTheNoteThatShowsItsTable() = runTest {
        saveNote(note("week-plan", "Week plan"), days)
        saveRow("monday", "days", title = "Monday", name = "Gym at seven")

        val titleResults = repository.searchNoteTitlesAndSnippets("Monday")
        val contentResults = repository.searchNotes("Gym at seven")

        assertEquals(listOf("monday" to "Week plan"), titleResults.map { it.note.noteId to it.parentTitle })
        assertEquals(listOf("monday" to "Week plan"), contentResults.map { it.note.noteId to it.parentTitle })
    }

    @Test
    fun trashedRowsAreLabelledWithTheirNoteEvenWhenThatNoteIsTrashedOrATemplate() = runTest {
        val templateTable = days.copy(id = "template-block", databaseId = "template-days")
        saveNote(note("week-plan", "Week plan", trashedAt = 50L), days)
        saveNote(note("template", "Week template", isTemplate = true), templateTable)
        saveRow("monday", "days", title = "Monday", name = "Gym", trashedAt = 60L)
        saveRow("template-monday", "template-days", title = "Monday", name = "Gym", trashedAt = 60L)
        saveNote(note("plain", "Shopping", trashedAt = 70L))
        val trashedNotes = listOf("monday", "template-monday", "plain").mapNotNull { repository.getNoteById(it) }

        val parentTitles = repository.parentTitlesOfSubNotes(trashedNotes)

        assertEquals(mapOf("monday" to "Week plan", "template-monday" to "Week template"), parentTitles)
    }

    @Test
    fun searchLeavesOutRowsWhoseTableIsOnlyInATemplateOrNowhere() = runTest {
        val templateTable = days.copy(id = "template-block", databaseId = "template-days")
        saveNote(note("template", "Week template", isTemplate = true), templateTable)
        saveRow("template-monday", "template-days", title = "Monday", name = "Gym")
        saveRow("orphan-monday", "nowhere", title = "Monday", name = "Gym")

        val results = repository.searchNotes("Monday")

        assertEquals(emptyList(), results.map { it.note.noteId })
    }

    @Test
    fun aNewDatabaseTemplateHasAnEmptyCellForEachColumnAndIsOnlyListedWithDatabaseTemplates() = runTest {
        val templateNoteId = repository.createDatabaseTemplate(daysWithStatus)

        val templateBlocks = repository.getNoteContent(templateNoteId)?.blocks.orEmpty()

        assertEquals(
            listOf(databaseCellBlockId(nameColumn, templateNoteId), databaseCellBlockId(statusColumn, templateNoteId)),
            templateBlocks.map { it.id }
        )
        assertEquals(listOf(templateNoteId), repository.getAllDatabaseTemplates().first().map { it.noteId })
        assertEquals(emptyList(), repository.getAllTemplates().first())
    }

    @Test
    fun aRowMadeFromATemplateGetsItsTitleCellValuesAndBody() = runTest {
        val templateNoteId = repository.createDatabaseTemplate(daysWithStatus)
        val templateNameCell = PropertyBlock(
            id = databaseCellBlockId(nameColumn, templateNoteId),
            propertyType = PropertyType.NAME,
            text = "Gym",
            updatedAt = 1L
        )
        val templateBody = TextBlock(id = "template-text", text = "Warm up first", updatedAt = 1L)
        saveNote(note(templateNoteId, "Workout", isTemplate = true).copy(isDatabaseTemplate = true), templateNameCell, templateBody)

        val rowNoteId = repository.createDatabaseRow(daysWithStatus, templateNoteId).rowNoteId

        val row = rowsOf("days").single()
        val rowBody = repository.getNoteContent(rowNoteId)?.blocks.orEmpty().filterIsInstance<TextBlock>().single()
        assertEquals("Workout", row.title)
        assertEquals("Gym", row.cell(nameColumn)?.text)
        assertEquals("", row.cell(statusColumn)?.text)
        assertEquals("Warm up first", rowBody.text)
        assertNotEquals("template-text", rowBody.id)
    }

    @Test
    fun editingARowMadeFromATemplateLeavesTheTemplateAlone() = runTest {
        val templateNoteId = repository.createDatabaseTemplate(days)
        val rowNoteId = repository.createDatabaseRow(days, templateNoteId).rowNoteId

        repository.updateDatabaseCell(days, rowNoteId, nameColumn) { it.copy(text = "Swim") }

        val templateNameCell = repository.getNoteContent(templateNoteId)?.blocks.orEmpty().single() as PropertyBlock
        assertEquals("Swim", rowsOf("days").single().cell(nameColumn)?.text)
        assertEquals("", templateNameCell.text)
    }

    @Test
    fun aRowFromADeletedTemplateStartsEmpty() = runTest {
        val templateNoteId = repository.createDatabaseTemplate(days)
        saveNote(note(templateNoteId, "Workout", isTemplate = true).copy(isDatabaseTemplate = true))
        repository.deleteTemplate(templateNoteId)

        repository.createDatabaseRow(days, templateNoteId)

        val row = rowsOf("days").single()
        assertEquals("", row.title)
        assertEquals("", row.cell(nameColumn)?.text)
    }

    @Test
    fun aRowCarriesItsNotesIconAndCoverImage() = runTest {
        saveNote(note("dune", "Dune", databaseId = "days").copy(icon = "📚", coverImagePath = "dune-cover.jpg"))

        val row = rowsOf("days").single()

        assertEquals("📚", row.icon)
        assertEquals("dune-cover.jpg", row.coverImagePath)
    }

    @Test
    fun aRowAddedToABoardColumnStartsWithThatColumnsValueInOneUndoStep() = runTest {
        val preset = DatabaseCellPreset(statusColumn) { it.copy(tags = listOf("Done")) }

        val change = repository.createDatabaseRow(daysWithStatus, cellPreset = preset)

        assertEquals(listOf("Done"), rowsOf("days").single().cell(statusColumn)?.tags)
        repository.applyDatabaseRowChanges(listOf(change), HistoryDirection.UNDO)
        assertEquals(emptyList(), rowsOf("days"))
    }

    private suspend fun statusOptionNames(): List<String> = repository.getPropertyTags("STATUS").first().map { it.name }

    @Test
    fun newOptionsGoToTheEndAndReorderingChangesTheOrderEverywhere() = runTest {
        repository.createPropertyTag("STATUS", "To do")
        repository.createPropertyTag("STATUS", "Doing")
        repository.createPropertyTag("STATUS", "Done")

        repository.reorderPropertyTags("STATUS", listOf("Done", "To do", "Doing"))
        repository.createPropertyTag("STATUS", "Blocked")

        assertEquals(listOf("Done", "To do", "Doing", "Blocked"), statusOptionNames())
    }

    @Test
    fun reorderingSavesAnOptionThatOnlyRowsWereUsing() = runTest {
        repository.createPropertyTag("STATUS", "Done")

        repository.reorderPropertyTags("STATUS", listOf("Waiting", "Done"))

        assertEquals(listOf("Waiting", "Done"), statusOptionNames())
    }

    @Test
    fun anOptionKeepsTheColorChosenForItUntilItIsSetBackToAutomatic() = runTest {
        repository.createPropertyTag("STATUS", "Done")

        repository.setPropertyTagColor("STATUS", "done", "green")
        val chosenColor = repository.getPropertyTags("STATUS").first().single().colorName
        repository.setPropertyTagColor("STATUS", "Done", null)

        assertEquals("green", chosenColor)
        assertNull(repository.getPropertyTags("STATUS").first().single().colorName)
    }

    private suspend fun settingsOf(databaseId: String): DatabaseBlock? = repository.observeDatabaseSettings(databaseId).first()

    @Test
    fun aNewDatabaseGetsAHiddenNoteForItsSharedSettings() = runTest {
        repository.createDatabase("films")

        val databaseNote = repository.getNoteById(databaseNoteId("films"))

        assertEquals(NoteKind.DATABASE, databaseNote?.kind)
        assertEquals(true, databaseNote?.isSubNote)
        assertEquals(databaseSettingsBlockId("films"), settingsOf("films")?.id)
    }

    @Test
    fun aDatabaseFromBeforeTheHiddenNoteGetsOneWithTheSettingsItHad() = runTest {
        saveNote(note("week-plan", "Week plan"), days)

        val settings = settingsOf("days")

        assertEquals("Days", settings?.title)
        assertEquals(listOf(nameColumn), settings?.columns)
        assertEquals(databaseSettingsBlockId("days"), settings?.id)
    }

    @Test
    fun movingTheSettingsOutOfOldDatabasesHappensOnceForEveryDatabase() = runTest {
        saveNote(note("week-plan", "Week plan"), days)
        saveNote(note("dashboard", "Dashboard"), linkedDays)

        repository.createMissingDatabaseNotes()
        repository.createMissingDatabaseNotes()

        assertEquals("Days", settingsOf("days")?.title)
        assertEquals(listOf(databaseSettingsBlockId("days")), repository.getNoteContent(databaseNoteId("days"))?.blocks?.map { it.id })
    }

    @Test
    fun changingTheSharedSettingsSavesThemInTheHiddenNote() = runTest {
        repository.createDatabase("films")
        waitForTheClockToTick()

        val wasChanged = repository.changeDatabaseSettings("films") { it.copy(title = "Films", updatedAt = 5L) }
        val wasChangedAgain = repository.changeDatabaseSettings("films") { it }

        assertTrue(wasChanged)
        assertFalse(wasChangedAgain)
        assertEquals("Films", settingsOf("films")?.title)
        assertEquals("Films", repository.getNoteById(databaseNoteId("films"))?.title)
    }

    @Test
    fun theHiddenDatabaseNoteNeverShowsUpInSearchOrTheLinkToNoteList() = runTest {
        repository.createDatabase("films")
        repository.changeDatabaseSettings("films") { it.copy(title = "Films", updatedAt = 5L) }

        val titleResults = repository.searchNoteTitlesAndSnippets("Films")
        val contentResults = repository.searchNotes("Films")
        val linkableNotes = repository.getAllLinkableNotes().first()

        assertEquals(emptyList(), titleResults)
        assertEquals(emptyList(), contentResults)
        assertTrue(linkableNotes.none { it.noteId == databaseNoteId("films") })
    }

    @Test
    fun searchingADatabasesTitleFindsEveryLiveNoteThatShowsIt() = runTest {
        saveNote(note("week-plan", "Week plan"), days)
        saveNote(note("dashboard", "Dashboard"), linkedDays)
        saveNote(note("old", "Old", trashedAt = 5L), linkedDays.copy(id = "trashed-view"))
        repository.createMissingDatabaseNotes()

        val results = repository.searchNotes("Days")

        assertEquals(setOf("week-plan", "dashboard"), results.map { it.note.noteId }.toSet())
        assertTrue(results.all { it.matchedText == "Days" })
    }

    @Test
    fun onlyDatabasesShownInLiveNotesCanBeLinked() = runTest {
        saveNote(note("week-plan", "Week plan"), days)
        repository.createMissingDatabaseNotes()
        saveNote(note("old", "Old", trashedAt = 5L), days.copy(id = "trashed-block", databaseId = "trashed-days"))
        saveNote(note("template", "Week template", isTemplate = true), days.copy(id = "template-block", databaseId = "template-days"))

        val linkable = repository.getLinkableDatabases()

        assertEquals(listOf(LinkableDatabase(databaseId = "days", title = "Days", noteTitle = "Week plan")), linkable)
    }

    @Test
    fun copyingALinkedDatabaseKeepsItShowingTheSameRows() = runTest {
        saveRow("monday", "days", title = "Monday", name = "Gym")

        val copiedLink = repository.copyDatabasesIn(NoteContent(blocks = listOf(linkedDays))).blocks.single() as DatabaseBlock

        assertEquals(linkedDays, copiedLink)
        assertEquals(listOf("monday"), rowsOf("days").map { it.noteId })
    }

    @Test
    fun copyingADatabaseCopiesItsRowsAndItsSharedSettings() = runTest {
        saveNote(note("template", "Week template", isTemplate = true), days)
        saveRow("monday", "days", title = "Monday", name = "Gym")

        val copiedBlocks = repository.copyDatabasesIn(NoteContent(blocks = listOf(linkedDays, days))).blocks.map { it as DatabaseBlock }
        val (copiedLink, copiedView) = copiedBlocks

        assertNotEquals("days", copiedView.databaseId)
        assertEquals(copiedView.databaseId, copiedLink.databaseId)
        assertEquals(listOf("Monday"), rowsOf(copiedView.databaseId).map { it.title })
        assertEquals("Days", settingsOf(copiedView.databaseId)?.title)
    }

    @Test
    fun deletingTheNoteOfTheFirstViewKeepsTheDatabaseWhileALinkStillShowsIt() = runTest {
        saveNote(note("week-plan", "Week plan"), days)
        saveNote(note("dashboard", "Dashboard"), linkedDays)
        saveRow("monday", "days", title = "Monday", name = "Gym")
        repository.createMissingDatabaseNotes()

        repository.deleteNote("week-plan", "")

        assertEquals(listOf("monday"), rowsOf("days").map { it.noteId })
        assertEquals("Days", settingsOf("days")?.title)
        assertEquals(listOf(databaseSettingsBlockId("days")), repository.getNoteContent(databaseNoteId("days"))?.blocks?.map { it.id })
    }

    @Test
    fun deletingTheLastNoteThatShowsADatabaseKeepsItsDataForNowAndParksTheViewAsDeleted() = runTest {
        saveNote(note("week-plan", "Week plan"), days)
        saveRow("monday", "days", title = "Monday", name = "Gym")
        repository.createMissingDatabaseNotes()

        repository.deleteNote("week-plan", "")
        val parkedView = repository.getNoteContent(databaseNoteId("days"))?.blocks?.firstOrNull { it.id == days.id }

        assertEquals(listOf("monday"), rowsOf("days").map { it.noteId })
        assertEquals("Days", settingsOf("days")?.title)
        assertEquals(true, parkedView?.isDeleted)
        assertTrue((parkedView?.updatedAt ?: 0L) > days.updatedAt)
    }

    @Test
    fun aParkedViewDoesNotCountAsShowingTheDatabase() = runTest {
        saveNote(note("week-plan", "Week plan"), days)
        repository.createMissingDatabaseNotes()
        repository.deleteNote("week-plan", "")

        assertEquals(emptyList(), repository.getLinkableDatabases())
    }

    private fun embeddedCanvasNote(canvasNoteId: String) = note(canvasNoteId, "").copy(isSubNote = true, kind = NoteKind.CANVAS)

    private fun canvasBlock(id: String, canvasNoteId: String) = CanvasBlock(id = id, canvasNoteId = canvasNoteId, updatedAt = 1L)

    @Test
    fun deletingTheLastNoteThatShowsAnEmbeddedCanvasKeepsItForNowAndParksTheBlockAsDeleted() = runTest {
        saveNote(embeddedCanvasNote("canvas-1"))
        saveNote(note("sketches", "Sketches"), canvasBlock("canvas-block", "canvas-1"))

        repository.deleteNote("sketches", "")
        val parkedBlock = repository.getNoteContent("canvas-1")?.blocks?.singleOrNull()

        assertTrue(repository.getNoteById("canvas-1") != null)
        assertEquals("canvas-block", parkedBlock?.id)
        assertEquals(true, parkedBlock?.isDeleted)
    }

    @Test
    fun deletingANoteWhileAnotherNoteStillShowsTheCanvasLeavesTheCanvasAlone() = runTest {
        saveNote(embeddedCanvasNote("canvas-1"))
        saveNote(note("sketches", "Sketches"), canvasBlock("canvas-block", "canvas-1"))
        saveNote(note("board", "Board"), canvasBlock("other-canvas-block", "canvas-1"))

        repository.deleteNote("sketches", "")

        assertTrue(repository.getNoteById("canvas-1") != null)
        assertEquals(emptyList(), repository.getNoteContent("canvas-1")?.blocks.orEmpty())
    }
}
