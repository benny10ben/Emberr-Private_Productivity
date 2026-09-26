// Splits a markdown file into its front matter and one chunk per block.

package com.emberr.domain.vault

private const val MINIMUM_FENCE_LENGTH = 3

enum class VaultChunkKind { FENCE, TABLE, QUOTE, DIVIDER, HEADING, LIST, PARAGRAPH, EMPTY }

data class VaultMarkdownChunk(
    val kind: VaultChunkKind,
    val lines: List<String>,
    val tag: String?,
    val fenceInfo: String? = null,
    val rawText: String = ""
)

data class VaultFrontMatter(
    val noteId: String? = null,
    val title: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val icon: String? = null,
    val isFavorite: Boolean = false,
    val isDaily: Boolean = false,
    val dateString: String? = null
)

object VaultMarkdownScanner {

    val tagRegex = Regex("""\^em-([A-Za-z0-9]+)\s*$""")
    val headingRegex = Regex("""^(#{1,6})(?: (.*))?$""")
    val checkboxRegex = Regex("""^( *)- \[([ xX])](?: (.*))?$""")
    val toggleRegex = Regex("""^( *)- ▸(?: (.*))?$""")
    val numberedRegex = Regex("""^( *)(\d{1,9})\.(?: (.*))?$""")
    val bulletRegex = Regex("""^( *)-(?: (.*))?$""")

    private val separatorCellRegex = Regex("""^:?-{3,}:?$""")
    private val dashDividerRegex = Regex("""^-{3,}$""")
    private val starDividerRegex = Regex("""^\*(\s*\*){2,}$""")
    private val underscoreDividerRegex = Regex("""^_{3,}$""")

    fun splitFrontMatter(markdown: String): Pair<VaultFrontMatter, String> {
        val normalised = markdown.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalised.split('\n')

        if (lines.firstOrNull()?.trim() != "---") return VaultFrontMatter() to normalised

        val closingIndex = (1 until lines.size).firstOrNull { lines[it].trim() == "---" }
            ?: return VaultFrontMatter() to normalised

        val fields = parseKeyValueLines(lines.subList(1, closingIndex))
        val body = lines.subList(closingIndex + 1, lines.size).joinToString("\n")

        val frontMatter = VaultFrontMatter(
            noteId = fields["id"]?.takeIf { it.isNotBlank() },
            title = fields["title"],
            createdAt = fields["created"]?.toLongOrNull(),
            updatedAt = fields["updated"]?.toLongOrNull(),
            icon = fields["icon"]?.takeIf { it.isNotBlank() },
            isFavorite = fields["favorite"].equals("true", ignoreCase = true),
            isDaily = fields["daily"].equals("true", ignoreCase = true),
            dateString = fields["date"]?.takeIf { it.isNotBlank() }
        )
        return frontMatter to body
    }

    fun scanBody(body: String): List<VaultMarkdownChunk> {
        val lines = body.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val chunks = mutableListOf<VaultMarkdownChunk>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank()) {
                index++
                continue
            }

            val fenceLength = openingFenceLength(line)
            val startIndex = index
            index = when {
                fenceLength >= MINIMUM_FENCE_LENGTH -> readFenceChunk(lines, index, fenceLength, chunks)
                isTableRow(line) -> readTableChunk(lines, index, chunks)
                isQuoteLine(line) -> readQuoteChunk(lines, index, chunks)
                isDividerLine(line) -> readDividerChunk(lines, index, chunks)
                isLoneTagLine(line) -> readLoneTagChunk(lines, index, chunks)
                headingRegex.matches(stripTag(line)) -> readHeadingChunk(lines, index, chunks)
                else -> readTextChunk(lines, index, chunks)
            }

            chunks[chunks.lastIndex] = chunks[chunks.lastIndex].copy(
                rawText = lines.subList(startIndex, index).joinToString("\n")
            )
        }
        return chunks
    }

    fun parseKeyValueLines(lines: List<String>): Map<String, String> {
        val fields = LinkedHashMap<String, String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val separatorIndex = indexOfUnquotedColon(trimmed)
            if (separatorIndex <= 0) continue
            val key = unquoteYaml(trimmed.substring(0, separatorIndex).trim())
            val value = unquoteYaml(trimmed.substring(separatorIndex + 1).trim())
            if (key.isNotEmpty()) fields[key] = value
        }
        return fields
    }

    fun unquoteYaml(value: String): String {
        if (value.length < 2 || !value.startsWith("\"") || !value.endsWith("\"")) return value

        val inner = value.substring(1, value.length - 1)
        val output = StringBuilder(inner.length)
        var index = 0
        while (index < inner.length) {
            val character = inner[index]
            if (character == '\\' && index + 1 < inner.length) {
                output.append(inner[index + 1])
                index += 2
            } else {
                output.append(character)
                index++
            }
        }
        return output.toString()
    }

    fun extractTag(line: String): String? = tagRegex.find(line)?.groupValues?.get(1)

    fun stripTag(line: String): String {
        val match = tagRegex.find(line) ?: return line
        val beforeTag = line.substring(0, match.range.first)
        return if (beforeTag.endsWith(" ")) beforeTag.dropLast(1) else beforeTag
    }

    fun isSeparatorTableRow(line: String): Boolean {
        if (!isTableRow(line)) return false
        val cells = splitTableCells(line)
        return cells.isNotEmpty() && cells.all { separatorCellRegex.matches(it.trim()) }
    }

    fun splitTableCells(line: String): List<String> {
        val withoutEdges = line.trim().removePrefix("|").removeSuffix("|")
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0

        while (index < withoutEdges.length) {
            val character = withoutEdges[index]
            when {
                character == '\\' && index + 1 < withoutEdges.length -> {
                    current.append(character).append(withoutEdges[index + 1])
                    index += 2
                }
                character == '|' -> {
                    cells.add(current.toString())
                    current.clear()
                    index++
                }
                else -> {
                    current.append(character)
                    index++
                }
            }
        }
        cells.add(current.toString())
        return cells
    }

    fun isTableRow(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.length > 1 && trimmed.startsWith("|")
    }

    private fun isQuoteLine(line: String): Boolean = line.trimStart().startsWith(">")

    private fun isDividerLine(line: String): Boolean {
        val trimmed = stripTag(line).trim()
        return dashDividerRegex.matches(trimmed) ||
            starDividerRegex.matches(trimmed) ||
            underscoreDividerRegex.matches(trimmed)
    }

    private fun isLoneTagLine(line: String): Boolean =
        extractTag(line) != null && stripTag(line).isBlank()

    fun isListItemLine(line: String): Boolean {
        val withoutTag = stripTag(line)
        return checkboxRegex.matches(withoutTag) ||
            toggleRegex.matches(withoutTag) ||
            numberedRegex.matches(withoutTag) ||
            bulletRegex.matches(withoutTag)
    }

    private fun startsNewStructure(line: String): Boolean {
        if (line.isBlank()) return true
        if (openingFenceLength(line) >= MINIMUM_FENCE_LENGTH) return true
        if (isTableRow(line)) return true
        if (isQuoteLine(line)) return true
        if (isDividerLine(line)) return true
        if (headingRegex.matches(stripTag(line))) return true
        return isListItemLine(line)
    }

    private fun openingFenceLength(line: String): Int {
        val trimmed = line.trimStart()
        var backtickCount = 0
        while (backtickCount < trimmed.length && trimmed[backtickCount] == '`') backtickCount++
        return backtickCount
    }

    private fun isClosingFence(line: String, fenceLength: Int): Boolean {
        val trimmed = line.trim()
        if (trimmed.length < fenceLength) return false
        return trimmed.all { it == '`' }
    }

    private fun readFenceChunk(
        lines: List<String>,
        startIndex: Int,
        fenceLength: Int,
        chunks: MutableList<VaultMarkdownChunk>
    ): Int {
        val fenceInfo = lines[startIndex].trimStart().drop(fenceLength).trim()
        val fenceBody = mutableListOf<String>()
        var index = startIndex + 1

        while (index < lines.size && !isClosingFence(lines[index], fenceLength)) {
            fenceBody.add(lines[index])
            index++
        }
        if (index < lines.size) index++

        var tag: String? = null
        if (index < lines.size && isLoneTagLine(lines[index])) {
            tag = extractTag(lines[index])
            index++
        }

        chunks.add(VaultMarkdownChunk(VaultChunkKind.FENCE, fenceBody, tag, fenceInfo))
        return index
    }

    private fun readTableChunk(
        lines: List<String>,
        startIndex: Int,
        chunks: MutableList<VaultMarkdownChunk>
    ): Int {
        val collected = mutableListOf<String>()
        var index = startIndex

        while (index < lines.size && isTableRow(lines[index])) {
            collected.add(lines[index])
            index++
        }

        var tag: String? = null
        if (index < lines.size && isLoneTagLine(lines[index])) {
            tag = extractTag(lines[index])
            index++
        }

        chunks.add(VaultMarkdownChunk(VaultChunkKind.TABLE, collected, tag))
        return index
    }

    private fun readQuoteChunk(
        lines: List<String>,
        startIndex: Int,
        chunks: MutableList<VaultMarkdownChunk>
    ): Int {
        val collected = mutableListOf<String>()
        var index = startIndex
        var tag: String? = null

        while (index < lines.size && isQuoteLine(lines[index])) {
            val lineTag = extractTag(lines[index])
            collected.add(stripTag(lines[index]))
            index++
            if (lineTag != null) {
                tag = lineTag
                break
            }
        }

        chunks.add(VaultMarkdownChunk(VaultChunkKind.QUOTE, collected, tag))
        return index
    }

    private fun readDividerChunk(
        lines: List<String>,
        startIndex: Int,
        chunks: MutableList<VaultMarkdownChunk>
    ): Int {
        var index = startIndex
        var tag = extractTag(lines[index])
        val dividerLine = stripTag(lines[index]).trim()
        index++

        if (tag == null && index < lines.size && isLoneTagLine(lines[index])) {
            tag = extractTag(lines[index])
            index++
        }

        chunks.add(VaultMarkdownChunk(VaultChunkKind.DIVIDER, listOf(dividerLine), tag))
        return index
    }

    private fun readLoneTagChunk(
        lines: List<String>,
        startIndex: Int,
        chunks: MutableList<VaultMarkdownChunk>
    ): Int {
        chunks.add(VaultMarkdownChunk(VaultChunkKind.EMPTY, emptyList(), extractTag(lines[startIndex])))
        return startIndex + 1
    }

    private fun readHeadingChunk(
        lines: List<String>,
        startIndex: Int,
        chunks: MutableList<VaultMarkdownChunk>
    ): Int {
        val line = lines[startIndex]
        chunks.add(
            VaultMarkdownChunk(VaultChunkKind.HEADING, listOf(stripTag(line)), extractTag(line))
        )
        return startIndex + 1
    }

    private fun readTextChunk(
        lines: List<String>,
        startIndex: Int,
        chunks: MutableList<VaultMarkdownChunk>
    ): Int {
        val kind = if (isListItemLine(lines[startIndex])) VaultChunkKind.LIST else VaultChunkKind.PARAGRAPH
        val collected = mutableListOf<String>()
        var index = startIndex
        var tag: String? = null

        while (index < lines.size) {
            val line = lines[index]
            val lineTag = extractTag(line)
            collected.add(stripTag(line))
            index++

            if (lineTag != null) {
                tag = lineTag
                break
            }
            if (index >= lines.size) break
            if (startsNewStructure(lines[index])) break
        }

        chunks.add(VaultMarkdownChunk(kind, collected, tag))
        return index
    }

    private fun indexOfUnquotedColon(text: String): Int {
        var insideQuotes = false
        var index = 0

        while (index < text.length) {
            val character = text[index]
            when {
                character == '\\' && insideQuotes -> index++
                character == '"' -> insideQuotes = !insideQuotes
                character == ':' && !insideQuotes -> return index
            }
            index++
        }
        return -1
    }
}
