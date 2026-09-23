package com.emberr.domain.util.task

import com.emberr.domain.model.ParsedTask
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class HeuristicTaskExtractor(
    private val clock: Clock = Clock.System,
    private val currentTimeZone: () -> TimeZone = { TimeZone.currentSystemDefault() }
) : TaskExtractor {

    override fun extractTasks(transcript: String): List<ParsedTask> {
        if (transcript.isBlank()) return emptyList()

        val stripped = stripLeadingFillers(transcript.trim())
        val now = clock.now()
        val tz = currentTimeZone()

        val tasks = mutableListOf<ParsedTask>()
        for (raw in stripped.split(SPLIT_CONNECTORS).flatMap(::splitAtTaskBoundaries)) {
            val segment = stripLeadingFillers(raw.trim())
            if (segment.isBlank()) continue
            val followsPreviousTask = AFTER_PREVIOUS_TASK.containsMatchIn(segment)
            val parsed = parseTemporal(stripLeadingFillers(segment.replace(AFTER_PREVIOUS_TASK, " ")), now, tz)
            val timestamp = parsed.timestamp
                ?: if (followsPreviousTask) tasks.lastOrNull()?.timestamp?.plus(AFTER_PREVIOUS_TASK_GAP_MILLIS) else null
            val task = cleanTaskText(parsed.remaining).ifBlank { "Voice reminder" }
            tasks += ParsedTask(taskText = task, timestamp = timestamp)
        }
        return tasks
    }

    private fun splitAtTaskBoundaries(text: String): List<String> {
        val pieces = mutableListOf<String>()
        var pieceStart = 0
        for (boundary in SOFT_TASK_BOUNDARY.findAll(text)) {
            if (startsANewTask(text.substring(boundary.range.last + 1))) {
                pieces += text.substring(pieceStart, boundary.range.first)
                pieceStart = boundary.range.last + 1
            }
        }
        pieces += text.substring(pieceStart)
        return pieces
    }

    private fun startsANewTask(followingText: String): Boolean {
        val candidateTask = followingText.replace(LEADING_AFTER_PREVIOUS_TASK, "")
        if (PREFIX_FILLERS.any { candidateTask.startsWith(it, ignoreCase = true) }) return true
        val nextWords = WORD.findAll(candidateTask).take(2).map { it.value.lowercase() }.toList()
        val firstWord = nextWords.firstOrNull() ?: return false
        if (firstWord in TASK_STARTING_VERBS) return true
        return firstWord in NOUN_LIKE_TASK_STARTING_VERBS && nextWords.getOrNull(1) in OBJECT_STARTING_WORDS
    }

    private fun String.withoutTemporalPhrase(range: IntRange): String =
        substring(0, range.first).replace(DANGLING_PREPOSITION, "") + " " + substring(range.last + 1)

    // Text cleanup
    private fun stripLeadingFillers(text: String): String {
        var t = text.trim().replace(LEADING_GREETING, "").trim()
        var changed = true
        while (changed) {
            changed = false
            for (prefix in PREFIX_FILLERS) {
                if (t.startsWith(prefix, ignoreCase = true)) {
                    t = t.substring(prefix.length).trim()
                    changed = true
                    break
                }
            }
        }
        return t
    }

    private fun cleanTaskText(text: String): String {
        var s = text
            .replace(SUFFIX_FILLERS, "")
            .replace(LEADING_NON_ALNUM, "")
            .replace(LEADING_PREPOSITION, "")
            .replace(TRAILING_PUNCTUATION, "")
            .replace(MULTI_SPACE, " ")
            .trim()
        if (s.isNotEmpty()) s = s.replaceFirstChar { it.uppercase() }
        return s
    }

    // Temporal parsing
    private data class Temporal(val remaining: String, val timestamp: Long?)
    private data class DatePart(
        val date: LocalDate,
        val remaining: String,
        val timeHint: LocalTime? = null,
    )
    private data class TimePart(val time: LocalTime, val remaining: String)

    private fun parseTemporal(segment: String, now: Instant, tz: TimeZone): Temporal {
        parseInterval(segment, now)?.let { (rest, instant) ->
            return Temporal(rest, instant.toEpochMilliseconds())
        }

        val nowLocal = now.toLocalDateTime(tz)
        val today = nowLocal.date

        var working = segment
        var date: LocalDate? = null
        var time: LocalTime? = null

        parseRelativeDate(working, today)?.let {
            date = it.date
            time = it.timeHint
            working = it.remaining
        }

        if (date == null) {
            parseWeekday(working, nowLocal.dayOfWeek, today)?.let {
                date = it.date
                time = it.timeHint
                working = it.remaining
            }
        }

        // Explicit clock times override any soft hint set above.
        parseExplicitTime(working)?.let {
            time = it.time
            working = it.remaining
        }

        parseSoftTimeOfDay(working)?.let {
            if (time == null) time = it.time
            working = it.remaining
        }

        if (date == null && time == null) return Temporal(working, null)

        val finalDate = date ?: today
        val finalTime = time ?: DEFAULT_TIME
        var instant = LocalDateTime(finalDate, finalTime).toInstant(tz)

        // No explicit date + time already passed today -> roll to tomorrow.
        if (date == null && instant < now) {
            instant = LocalDateTime(today.plus(1, DateTimeUnit.DAY), finalTime).toInstant(tz)
        }

        return Temporal(working, instant.toEpochMilliseconds())
    }

    private fun parseInterval(text: String, now: Instant): Pair<String, Instant>? {
        val m = INTERVAL_REGEX.find(text) ?: return null
        val amountWord = m.groupValues[1].lowercase().replace(MULTI_SPACE, " ").trim()
        val unit = m.groupValues[2].lowercase()
        val isHalf = amountWord == "half a" || amountWord == "half an"

        val amount: Int = if (isHalf) 0 else when (amountWord) {
            "a", "an" -> 1
            "couple", "a couple", "couple of", "a couple of" -> 2
            "few", "a few" -> 3
            else -> NUMBER_WORDS[amountWord] ?: amountWord.toIntOrNull() ?: return null
        }
        if (!isHalf && amount <= 0) return null

        val minutes: Long = when {
            unit.startsWith("sec") -> if (isHalf) return null else maxOf(1L, amount.toLong() / 60L)
            unit.startsWith("min") -> if (isHalf) return null else amount.toLong()
            unit.startsWith("hour") || unit.startsWith("hr") -> if (isHalf) 30L else amount * 60L
            unit.startsWith("day") -> if (isHalf) 12L * 60 else amount * 24L * 60L
            unit.startsWith("week") -> if (isHalf) (3L * 24 + 12) * 60 else amount * 7L * 24L * 60L
            unit.startsWith("month") -> if (isHalf) 15L * 24 * 60 else amount * 30L * 24L * 60L
            else -> return null
        }
        if (minutes <= 0) return null

        val instant = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + minutes * 60_000L)
        return text.withoutTemporalPhrase(m.range) to instant
    }

    private fun parseRelativeDate(text: String, today: LocalDate): DatePart? {
        for ((regex, handler) in REL_DAY_PATTERNS) {
            val match = regex.find(text) ?: continue
            val (date, hint) = handler(today)
            return DatePart(date = date, remaining = text.withoutTemporalPhrase(match.range), timeHint = hint)
        }
        return null
    }

    private fun parseWeekday(text: String, todayDow: DayOfWeek, todayDate: LocalDate): DatePart? {
        val m = WEEKDAY_REGEX.find(text) ?: return null
        val modifier = m.groupValues[1].lowercase().trim()
        val name = m.groupValues[2].uppercase()
        val target = runCatching { DayOfWeek.valueOf(name) }.getOrElse { return null }

        var delta = target.ordinal - todayDow.ordinal
        if (delta <= 0) delta += 7
        if (modifier == "next" && delta < 7) delta += 7
        return DatePart(
            date = todayDate.plus(delta, DateTimeUnit.DAY),
            remaining = text.withoutTemporalPhrase(m.range),
            timeHint = PART_OF_DAY_TIMES[m.groupValues[3].lowercase()],
        )
    }

    private fun parseExplicitTime(text: String): TimePart? {
        NOON_REGEX.find(text)?.let {
            return TimePart(LocalTime(12, 0), text.withoutTemporalPhrase(it.range))
        }
        MIDNIGHT_REGEX.find(text)?.let {
            return TimePart(LocalTime(0, 0), text.withoutTemporalPhrase(it.range))
        }

        FRACTION_TIME_REGEX.find(text)?.let { m ->
            val word = m.groupValues[1].lowercase()
            val dir = m.groupValues[2].lowercase()
            val hour = m.groupValues[3].toIntOrNull() ?: return@let
            val frac = if (word == "half") 30 else 15
            val (h, mn) = when (dir) {
                "past", "after" -> hour to frac
                "to", "till", "before", "of" -> ((hour - 1 + 24) % 24) to (60 - frac)
                else -> return@let
            }
            if (h in 0..23 && mn in 0..59) {
                return TimePart(LocalTime(h, mn), text.withoutTemporalPhrase(m.range))
            }
        }

        TIME_WITH_AMPM.find(text)?.let { m ->
            var hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toIntOrNull() ?: 0
            val ampm = m.groupValues[3].lowercase().replace(".", "")
            when (ampm) {
                "pm" -> if (hour < 12) hour += 12
                "am" -> if (hour == 12) hour = 0
            }
            if (hour in 0..23 && minute in 0..59) {
                return TimePart(LocalTime(hour, minute), text.withoutTemporalPhrase(m.range))
            }
        }

        TIME_24H.find(text)?.let { m ->
            val hour = m.groupValues[1].toIntOrNull() ?: return@let
            val minute = m.groupValues[2].toIntOrNull() ?: return@let
            if (hour in 0..23 && minute in 0..59) {
                return TimePart(LocalTime(hour, minute), text.withoutTemporalPhrase(m.range))
            }
        }
        return null
    }

    private fun parseSoftTimeOfDay(text: String): TimePart? {
        val m = SOFT_TIME_REGEX.find(text) ?: return null
        val time = PART_OF_DAY_TIMES[m.groupValues[1].lowercase()] ?: return null
        return TimePart(time, text.withoutTemporalPhrase(m.range))
    }

    private companion object {

        private val DEFAULT_TIME = LocalTime(9, 0)

        // Cleanup patterns
        private val LEADING_GREETING = Regex(
            "(?i)^(?:" +
                    "(?:hey|hi|hello|ok|okay|alright|yo)\\s+" +
                    "(?:emberr|claude|computer|app|assistant|google|siri)[,!]?\\s+" +
                    "|(?:hey|hi|hello|ok|okay|alright|yo|so)[,!]+\\s+" +
                    ")"
        )

        private val PREFIX_FILLERS = listOf(
            "could you please remind me to ",
            "can you please remind me to ",
            "please add a reminder to ",
            "please remind me to ",
            "could you remind me to ",
            "can you remind me to ",
            "remind me that i need to ",
            "remind me that i have to ",
            "remind me that i should ",
            "remind me that i must ",
            "remind me that ",
            "add a reminder to ",
            "add a reminder for ",
            "set a reminder to ",
            "set a reminder for ",
            "make a reminder to ",
            "make a note to ",
            "don't forget to ",
            "dont forget to ",
            "remind me to ",
            "remind me ",
            "remind to ",
            "i need to ",
            "i have to ",
            "i should ",
            "i must ",
            "note to self: ",
            "note to self ",
            "todo: ",
            "to do: ",
            "to-do: ",
            "note: ",
            "ok ",
            "okay ",
        )

        private val SUFFIX_FILLERS = Regex(
            "(?i)(?:\\s*\\b(?:" +
                    "so\\s+add\\s+a\\s+reminder(?:\\s+for\\s+that)?" +
                    "|add\\s+a\\s+reminder(?:\\s+for\\s+that)?" +
                    "|please|thanks(?:\\s+a\\s+lot)?|thank\\s+you|thx|cheers" +
                    "|can\\s+you|could\\s+you|will\\s+you|would\\s+you" +
                    "|for\\s+that|ok|okay" +
                    ")\\b[.!?]*)+\\s*$"
        )

        // Strong connectors only — bare "and" is intentionally excluded.
        private val SPLIT_CONNECTORS = Regex(
            "\\b(?:" +
                    "oh\\s+and\\s+also\\s+remind\\s+me\\s+to" +
                    "|and\\s+also\\s+add\\s+a\\s+reminder\\s+to" +
                    "|and\\s+also\\s+remind\\s+me\\s+to" +
                    "|also\\s+remind\\s+me\\s+to" +
                    "|oh\\s+and\\s+also" +
                    "|and\\s+also" +
                    "|and\\s+(?=then\\b)" +
                    ")\\b|\\s*;\\s*",
            RegexOption.IGNORE_CASE,
        )

        private val LEADING_NON_ALNUM = Regex("^[^\\p{L}\\p{N}]+")
        private val LEADING_PREPOSITION = Regex("(?i)^(in|on|at|by|to|for)\\s+")
        private val TRAILING_PUNCTUATION = Regex("[\\s,.!?;:]+$")
        private val DANGLING_PREPOSITION = Regex("(?i)\\b(?:by|for|until|till|before|around)\\s*$")
        private val WORD = Regex("[\\p{L}']+")

        private val SOFT_TASK_BOUNDARY = Regex(
            "(?i)\\s*[,.!?]+\\s+(?:(?:and\\s+)?also\\s+|and\\s+|plus\\s+)?" +
                    "|\\s+(?:and|also|plus)\\s+" +
                    "|\\s+(?=(?:right\\s+)?after\\s+that\\b|afterwards?\\b|then\\b)"
        )

        private const val AFTER_PREVIOUS_TASK_GAP_MILLIS = 60L * 60L * 1000L
        private val AFTER_PREVIOUS_TASK = Regex("(?i)\\b(?:right\\s+)?(?:after\\s+that|afterwards?)\\b|^then\\b")
        private val LEADING_AFTER_PREVIOUS_TASK =
            Regex("(?i)^(?:(?:right\\s+)?(?:after\\s+that|afterwards?)|then)\\b[\\s,]*")

        private val TASK_STARTING_VERBS = setOf(
            "add", "apply", "ask", "attend", "bake", "book", "bring", "buy", "call", "cancel", "change",
            "charge", "check", "clean", "collect", "confirm", "cook", "do", "drop", "email", "feed",
            "fetch", "fill", "finish", "fix", "follow", "get", "give", "go", "grab", "have", "hand",
            "help", "invite", "join", "learn", "leave", "look", "mail", "make", "meet", "message",
            "move", "organize", "organise", "pack", "pay", "phone", "pick", "plan", "practice",
            "practise", "prepare", "print", "put", "read", "register", "remember", "renew", "reply",
            "research", "reschedule", "return", "review", "ring", "schedule", "see", "sell", "send",
            "sign", "start", "stop", "study", "submit", "take", "tell", "text", "thank", "tidy",
            "update", "upload", "vacuum", "visit", "walk", "wash", "watch", "write",
        )

        private val NOUN_LIKE_TASK_STARTING_VERBS = setOf(
            "dust", "file", "iron", "order", "paint", "plant", "post", "water",
        )

        private val OBJECT_STARTING_WORDS = setOf(
            "a", "an", "the", "my", "your", "his", "her", "our", "their", "this", "that", "these",
            "those", "some", "all", "it", "them", "him", "me", "up", "out", "off",
        )

        private val PART_OF_DAY_TIMES = mapOf(
            "morning" to LocalTime(9, 0),
            "afternoon" to LocalTime(14, 0),
            "evening" to LocalTime(18, 0),
            "night" to LocalTime(20, 0),
        )
        private val MULTI_SPACE = Regex("\\s+")

        // Temporal patterns

        private val INTERVAL_REGEX = Regex(
            "(?i)\\bin\\s+(" +
                    "half\\s+an|half\\s+a" +
                    "|a\\s+couple\\s+of|a\\s+couple|couple\\s+of|couple" +
                    "|a\\s+few|few" +
                    "|an|a" +
                    "|one|two|three|four|five|six|seven|eight|nine|ten" +
                    "|eleven|twelve|fifteen|twenty|thirty|forty|fifty|sixty|ninety" +
                    "|\\d+" +
                    ")\\s+(" +
                    "seconds?|secs?|minutes?|mins?|hours?|hrs?|days?|weeks?|months?" +
                    ")\\b"
        )

        private val NUMBER_WORDS = mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
            "eleven" to 11, "twelve" to 12, "fifteen" to 15, "twenty" to 20,
            "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60,
            "ninety" to 90,
        )

        // Tried in order. Longer / more specific phrases must come first.
        private val REL_DAY_PATTERNS: List<Pair<Regex, (LocalDate) -> Pair<LocalDate, LocalTime?>>> = listOf(
            Regex("(?i)\\bthe\\s+day\\s+after\\s+tomorrow\\b") to
                    { d -> d.plus(2, DateTimeUnit.DAY) to null },
            Regex("(?i)\\bday\\s+after\\s+tomorrow\\b") to
                    { d -> d.plus(2, DateTimeUnit.DAY) to null },
            Regex("(?i)\\bday\\s+before\\s+yesterday\\b") to
                    { d -> d.minus(2, DateTimeUnit.DAY) to null },
            Regex("(?i)\\bthis\\s+weekend\\b") to
                    { d -> nextDow(d, DayOfWeek.SATURDAY) to LocalTime(10, 0) },
            Regex("(?i)\\bnext\\s+weekend\\b") to
                    { d -> nextDow(d.plus(7, DateTimeUnit.DAY), DayOfWeek.SATURDAY) to LocalTime(10, 0) },
            Regex("(?i)\\bnext\\s+week\\b") to
                    { d -> d.plus(7, DateTimeUnit.DAY) to null },
            Regex("(?i)\\bnext\\s+month\\b") to
                    { d -> d.plus(1, DateTimeUnit.MONTH) to null },
            Regex("(?i)\\bnext\\s+year\\b") to
                    { d -> d.plus(1, DateTimeUnit.YEAR) to null },
            Regex("(?i)\\btomorrow\\s+morning\\b") to
                    { d -> d.plus(1, DateTimeUnit.DAY) to LocalTime(9, 0) },
            Regex("(?i)\\btomorrow\\s+afternoon\\b") to
                    { d -> d.plus(1, DateTimeUnit.DAY) to LocalTime(14, 0) },
            Regex("(?i)\\btomorrow\\s+evening\\b") to
                    { d -> d.plus(1, DateTimeUnit.DAY) to LocalTime(18, 0) },
            Regex("(?i)\\btomorrow\\s+night\\b") to
                    { d -> d.plus(1, DateTimeUnit.DAY) to LocalTime(20, 0) },
            Regex("(?i)\\bthis\\s+morning\\b") to
                    { d -> d to LocalTime(9, 0) },
            Regex("(?i)\\bthis\\s+afternoon\\b") to
                    { d -> d to LocalTime(14, 0) },
            Regex("(?i)\\bthis\\s+evening\\b") to
                    { d -> d to LocalTime(18, 0) },
            Regex("(?i)\\btonight\\b") to
                    { d -> d to LocalTime(20, 0) },
            // Tolerate common misspellings.
            Regex("(?i)\\b(?:tomorrow|tommorrow|tommorow|tomorow)\\b") to
                    { d -> d.plus(1, DateTimeUnit.DAY) to null },
            Regex("(?i)\\btoday\\b") to
                    { d -> d to null },
            Regex("(?i)\\byesterday\\b") to
                    { d -> d.minus(1, DateTimeUnit.DAY) to null },
        )

        private fun nextDow(from: LocalDate, target: DayOfWeek): LocalDate {
            var delta = target.ordinal - from.dayOfWeek.ordinal
            if (delta <= 0) delta += 7
            return from.plus(delta, DateTimeUnit.DAY)
        }

        private val WEEKDAY_REGEX = Regex(
            "(?i)\\b(on\\s+|next\\s+|this\\s+|this\\s+coming\\s+|coming\\s+)?" +
                    "(monday|tuesday|wednesday|thursday|friday|saturday|sunday)" +
                    "(?:\\s+(morning|afternoon|evening|night))?\\b"
        )

        private val NOON_REGEX = Regex("(?i)\\b(?:at\\s+)?(?:noon|midday)\\b")
        private val MIDNIGHT_REGEX = Regex("(?i)\\b(?:at\\s+)?midnight\\b")

        private val FRACTION_TIME_REGEX = Regex(
            "(?i)\\b(?:a\\s+)?(half|quarter)\\s+(past|after|to|till|before|of)\\s+(\\d{1,2})\\b"
        )

        // Hour [+ optional :MM] + required am/pm. Accepts "am", "a.m.", "pm", "p.m.".
        private val TIME_WITH_AMPM = Regex(
            "(?i)\\b(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)\\b"
        )

        // 24-hour HH:MM — colon is required so bare numbers aren't eaten.
        private val TIME_24H = Regex(
            "(?i)\\b(?:at\\s+)?(\\d{1,2}):(\\d{2})\\b"
        )

        private val SOFT_TIME_REGEX = Regex(
            "(?i)\\b(?:in\\s+the|at|by)\\s+(morning|afternoon|evening|night)\\b"
        )
    }
}