package com.emberr.domain.database

import com.emberr.data.local.room.dao.BlockDao
import com.emberr.data.local.room.dao.NoteDao
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseRepeatFrequency
import com.emberr.domain.model.DatabaseTemplateRepeat
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.RecurrenceEngine
import com.emberr.domain.model.RecurrenceFrequency
import com.emberr.domain.model.RecurrenceRule
import com.emberr.domain.model.isoDayNumberToDayOfWeek
import com.emberr.domain.model.shortDateText
import com.emberr.domain.repository.NoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlin.time.Clock

private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

data class RepeatedRowToCreate(
    val rowNoteId: String,
    val templateNoteId: String,
    val date: LocalDate
)

fun DatabaseTemplateRepeat.occursOn(date: LocalDate): Boolean {
    if (skipsFirstDay && date == startsOn) return false
    val rule = when (frequency) {
        DatabaseRepeatFrequency.DAILY -> RecurrenceRule(RecurrenceFrequency.DAILY)
        DatabaseRepeatFrequency.WEEKDAYS -> RecurrenceRule(RecurrenceFrequency.WEEKLY, daysOfWeek = weekdays)
        DatabaseRepeatFrequency.WEEKLY -> RecurrenceRule(
            RecurrenceFrequency.WEEKLY,
            daysOfWeek = isoDaysOfWeek.mapNotNull(::isoDayNumberToDayOfWeek).toSet()
        )
        DatabaseRepeatFrequency.MONTHLY -> RecurrenceRule(RecurrenceFrequency.MONTHLY)
    }
    return RecurrenceEngine.occursOn(rule, startsOn, date)
}

fun repeatStartingNow(
    frequency: DatabaseRepeatFrequency,
    isoDaysOfWeek: List<Int>,
    time: LocalTime?,
    now: LocalDateTime
): DatabaseTemplateRepeat {
    return DatabaseTemplateRepeat(
        frequency = frequency,
        startsOn = now.date,
        isoDaysOfWeek = isoDaysOfWeek,
        time = time,
        skipsFirstDay = time.hasPassedBy(now)
    )
}

fun DatabaseTemplateRepeat.withTime(newTime: LocalTime?, now: LocalDateTime): DatabaseTemplateRepeat =
    copy(time = newTime, skipsFirstDay = if (startsOn == now.date) newTime.hasPassedBy(now) else skipsFirstDay)

private fun LocalTime?.hasPassedBy(now: LocalDateTime): Boolean = this != null && this < LocalTime(now.hour, now.minute)

fun repeatedRowNoteId(databaseId: String, templateNoteId: String, date: LocalDate): String =
    "repeat-$databaseId-$templateNoteId-$date"

fun repeatedRowTitle(templateTitle: String, date: LocalDate): String =
    listOf(templateTitle.trim(), shortDateText(date)).filter { it.isNotEmpty() }.joinToString(" ")

fun DatabaseBlock.repeatedRowsDueAt(now: LocalDateTime): List<RepeatedRowToCreate> {
    if (isDeleted) return emptyList()
    return repeatingTemplates
        .filter { (_, repeat) -> repeat.occursOn(now.date) && repeat.timeHasCome(now) }
        .map { (templateNoteId, _) -> RepeatedRowToCreate(repeatedRowNoteId(databaseId, templateNoteId, now.date), templateNoteId, now.date) }
}

fun DatabaseBlock.laterRepeatTimesToday(now: LocalDateTime): List<LocalDateTime> {
    if (isDeleted) return emptyList()
    return repeatingTemplates.values
        .filter { repeat -> repeat.occursOn(now.date) && !repeat.timeHasCome(now) }
        .mapNotNull { repeat -> repeat.time?.let { LocalDateTime(now.date, it) } }
}

fun nextRepeatCheckAfter(now: LocalDateTime, laterRepeatTimesToday: List<LocalDateTime>): LocalDateTime =
    laterRepeatTimesToday.minOrNull() ?: LocalDateTime(now.date.plus(1, DateTimeUnit.DAY), LocalTime(0, 0))

private fun DatabaseTemplateRepeat.timeHasCome(now: LocalDateTime): Boolean {
    val repeatTime = time ?: return true
    return repeatTime <= now.time
}

fun DatabaseBlock.withTemplateRepeat(templateNoteId: String, repeat: DatabaseTemplateRepeat?): DatabaseBlock =
    copy(repeatingTemplates = if (repeat == null) repeatingTemplates - templateNoteId else repeatingTemplates + (templateNoteId to repeat))

object RepeatingTemplateSchedule {
    private val repeatChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = repeatChanges.asSharedFlow()

    fun notifyRepeatChanged() {
        repeatChanges.tryEmit(Unit)
    }
}

class RepeatingTemplateRowCreator(
    private val noteDao: NoteDao,
    private val blockDao: BlockDao,
    private val noteRepository: NoteRepository
) {
    private val blockJson = Json { ignoreUnknownKeys = true }

    suspend fun createRowsDueAt(now: LocalDateTime): LocalDateTime = withContext(Dispatchers.IO) {
        val laterRepeatTimes = mutableListOf<LocalDateTime>()
        try {
            blockDao.findBlocksContainingIncludingDeleted(DatabaseRowCleanup.DATABASE_BLOCK_JSON_MARKER).forEach { entity ->
                val databaseBlock = runCatching { blockJson.decodeFromString<NoteBlock>(entity.blockDataJson) }.getOrNull() as? DatabaseBlock
                    ?: return@forEach
                if (databaseBlock.repeatingTemplates.isEmpty()) return@forEach
                val parentNote = noteDao.getNoteById(entity.noteId) ?: return@forEach
                if (parentNote.trashedAt != null || parentNote.isTemplate) return@forEach
                databaseBlock.repeatedRowsDueAt(now).forEach { rowToCreate -> noteRepository.createRepeatedDatabaseRow(databaseBlock, rowToCreate) }
                laterRepeatTimes += databaseBlock.laterRepeatTimesToday(now)
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            cause.printStackTrace()
        }
        nextRepeatCheckAfter(now, laterRepeatTimes)
    }
}

fun localNow(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime = Clock.System.now().toLocalDateTime(timeZone)

fun millisecondsUntil(moment: LocalDateTime, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long =
    (moment.toInstant(timeZone).toEpochMilliseconds() - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0L)
