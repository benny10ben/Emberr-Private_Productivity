package com.emberr.domain.quote

import emberr.shared.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile

data class DailyQuote(val text: String, val author: String)

object DailyQuoteLibrary {

    private const val QUOTES_FILE_PATH = "files/quotes.json"
    private const val LONGEST_QUOTE_WE_SHOW = 160

    @Serializable
    private data class BundledQuote(
        val quoteText: String = "",
        val quoteAuthor: String = ""
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val loadLock = Mutex()

    @Volatile
    private var cachedQuotes: List<DailyQuote>? = null

    suspend fun quoteForDate(date: LocalDate): DailyQuote? {
        val quotes = loadQuotesOnce()
        if (quotes.isEmpty()) return null
        val stableDayNumber = date.year * 372 + date.month.number * 31 + date.day
        return quotes[stableDayNumber.mod(quotes.size)]
    }

    private suspend fun loadQuotesOnce(): List<DailyQuote> {
        cachedQuotes?.let { return it }
        return loadLock.withLock {
            cachedQuotes ?: readBundledQuotes().also { cachedQuotes = it }
        }
    }

    private suspend fun readBundledQuotes(): List<DailyQuote> = withContext(Dispatchers.IO) {
        try {
            val fileText = Res.readBytes(QUOTES_FILE_PATH).decodeToString()
            json.decodeFromString<List<BundledQuote>>(fileText)
                .asSequence()
                .map { DailyQuote(text = it.quoteText.trim(), author = it.quoteAuthor.trim()) }
                .filter { it.text.isNotEmpty() && it.text.length <= LONGEST_QUOTE_WE_SHOW }
                .distinctBy { it.text }
                .toList()
        } catch (failure: Exception) {
            failure.printStackTrace()
            emptyList()
        }
    }
}
