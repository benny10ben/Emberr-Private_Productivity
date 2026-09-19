package com.emberr.presentation.shared.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Stable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.emberr.domain.model.InlineSpan
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.domain.util.network.openLinkInRunningBrowser
import com.emberr.domain.util.system.showNativeToast
import com.emberr.domain.util.system.triggerHapticFeedback
import com.emberr.presentation.shared.editor.components.DesktopCursor
import com.emberr.presentation.shared.editor.components.desktopPointerCursor
import com.emberr.ui.theme.LocalAppIsDark

const val WEB_LINK_TAG = "WEB_LINK"
const val NOTE_LINK_TAG = "NOTE_LINK"
const val EMAIL_LINK_TAG = "EMAIL_LINK"
const val PHONE_LINK_TAG = "PHONE_LINK"

private const val SHORTEST_POSSIBLE_LINK = 4
private const val PUNCTUATION_THAT_ENDS_A_SENTENCE = ".,;:!?)]}\"'"

private val CommonTopLevelDomains = listOf(
    "com", "org", "net", "edu", "gov", "int", "mil", "io", "dev", "app", "co", "ai", "me",
    "xyz", "info", "online", "site", "blog", "cloud", "tech", "store", "shop", "news", "tv",
    "so", "sh", "gg", "ly", "cc", "fm", "biz", "pro", "live", "page", "link", "space",
    "world", "digital", "studio", "design", "wiki", "email", "work", "group", "media",
    "uk", "us", "in", "de", "fr", "es", "it", "nl", "jp", "cn", "ca", "au", "br", "ru"
)

private val WebLinkPattern = Regex(
    "(?:https?://|www\\.)[^\\s]+" +
        "|[a-zA-Z0-9][a-zA-Z0-9-]*(?:\\.[a-zA-Z0-9-]+)*\\.(?:" +
        CommonTopLevelDomains.joinToString("|") +
        ")(?:/[^\\s]*)?",
    RegexOption.IGNORE_CASE
)

private val EmailPattern = Regex(
    "[a-zA-Z0-9][a-zA-Z0-9._%+-]*@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
)

private const val SHORTEST_POSSIBLE_PHONE_DIGITS = 7
private const val LONGEST_POSSIBLE_PHONE_DIGITS = 15
private val PhonePattern = Regex("\\+?[0-9][0-9\\-.\\s()]{5,}[0-9]")
private val PhoneSeparatorEdge = charArrayOf(' ', '-', '.', '(', ')')

@Immutable
data class WebLink(val start: Int, val end: Int, val url: String)

@Immutable
data class EmailLink(val start: Int, val end: Int, val email: String)

@Immutable
data class PhoneLink(val start: Int, val end: Int, val phone: String)

@Immutable
class WebLinkActions(
    val openLink: (String) -> Unit,
    val openEmail: (String) -> Unit,
    val openPhone: (String) -> Unit,
    val copyLink: (String) -> Unit
)

@Immutable
sealed interface HoveredLink {
    val anchor: Rect
    val start: Int
    val end: Int

    @Immutable
    data class Web(override val anchor: Rect, val url: String, override val start: Int, override val end: Int) : HoveredLink

    @Immutable
    data class Note(override val anchor: Rect, val noteId: String, override val start: Int, override val end: Int) : HoveredLink

    @Immutable
    data class Email(override val anchor: Rect, val email: String, override val start: Int, override val end: Int) : HoveredLink

    @Immutable
    data class Phone(override val anchor: Rect, val phone: String, override val start: Int, override val end: Int) : HoveredLink
}

@Stable
class LinkHoverState {
    var hoveredLink by mutableStateOf<HoveredLink?>(null)
        private set

    var rightClickedLink by mutableStateOf<HoveredLink?>(null)
        private set

    var rightClickPosition by mutableStateOf(Offset.Zero)
        private set

    fun onHover(link: HoveredLink?) {
        hoveredLink = link
    }

    fun clear() = onHover(null)

    fun openMenuFor(link: HoveredLink, position: Offset) {
        hoveredLink = null
        rightClickPosition = position
        rightClickedLink = link
    }

    fun closeMenu() {
        rightClickedLink = null
    }
}

fun findWebLinks(text: String): List<WebLink> {
    if (text.length < SHORTEST_POSSIBLE_LINK) return emptyList()
    if (!text.contains('.') && !text.contains("://")) return emptyList()

    val foundLinks = mutableListOf<WebLink>()
    for (match in WebLinkPattern.findAll(text)) {
        val start = match.range.first
        val matchEnd = match.range.last + 1
        if (start > 0 && joinsTheWordBefore(text[start - 1])) continue
        if (matchEnd < text.length && joinsTheWordAfter(text[matchEnd])) continue

        var end = matchEnd
        while (end > start && text[end - 1] in PUNCTUATION_THAT_ENDS_A_SENTENCE) end--
        if (end - start < SHORTEST_POSSIBLE_LINK) continue

        foundLinks.add(WebLink(start, end, text.substring(start, end)))
    }
    return foundLinks
}

private fun joinsTheWordBefore(character: Char): Boolean =
    character.isLetterOrDigit() || character == '@' || character == '.' || character == '/' ||
        character == '-' || character == '_'

private fun joinsTheWordAfter(character: Char): Boolean =
    character.isLetterOrDigit() || character == '-'

fun findEmails(text: String): List<EmailLink> {
    if (!text.contains('@')) return emptyList()

    val foundEmails = mutableListOf<EmailLink>()
    for (match in EmailPattern.findAll(text)) {
        var end = match.range.last + 1
        val start = match.range.first
        while (end > start && text[end - 1] in PUNCTUATION_THAT_ENDS_A_SENTENCE) end--
        if (end - start < SHORTEST_POSSIBLE_LINK) continue

        foundEmails.add(EmailLink(start, end, text.substring(start, end)))
    }
    return foundEmails
}

fun findPhones(text: String): List<PhoneLink> {
    val foundPhones = mutableListOf<PhoneLink>()
    for (match in PhonePattern.findAll(text)) {
        var start = match.range.first
        var end = match.range.last + 1
        while (end > start && text[end - 1] in PhoneSeparatorEdge) end--
        while (start < end && text[start] in PhoneSeparatorEdge) start++
        if (start >= end) continue

        if (start > 0 && joinsTheWordBefore(text[start - 1])) continue
        if (end < text.length && joinsTheWordAfter(text[end])) continue

        val candidate = text.substring(start, end)
        val digitCount = candidate.count { it.isDigit() }
        if (digitCount !in SHORTEST_POSSIBLE_PHONE_DIGITS..LONGEST_POSSIBLE_PHONE_DIGITS) continue

        foundPhones.add(PhoneLink(start, end, candidate))
    }
    return foundPhones
}

fun toOpenableWebLink(url: String): String = if (url.contains("://")) url else "https://$url"

private fun overlapsAny(start: Int, end: Int, ranges: List<Pair<Int, Int>>): Boolean =
    ranges.any { start < it.second && end > it.first }

private fun isCurrentlyHovered(hoveredLink: HoveredLink?, tag: String, start: Int, end: Int): Boolean {
    if (hoveredLink == null || hoveredLink.start != start || hoveredLink.end != end) return false
    return when (hoveredLink) {
        is HoveredLink.Web -> tag == WEB_LINK_TAG
        is HoveredLink.Email -> tag == EMAIL_LINK_TAG
        is HoveredLink.Phone -> tag == PHONE_LINK_TAG
        is HoveredLink.Note -> false
    }
}

fun AnnotatedString.withInteractiveLinksHighlighted(hoveredLink: HoveredLink? = null): AnnotatedString {
    val noteLinkRanges = getStringAnnotations(NOTE_LINK_TAG, 0, text.length).map { it.start to it.end }

    val webLinks = findWebLinks(text).filterNot { overlapsAny(it.start, it.end, noteLinkRanges) }
    val webLinkRanges = webLinks.map { it.start to it.end }

    val emails = findEmails(text).filterNot { overlapsAny(it.start, it.end, noteLinkRanges + webLinkRanges) }
    val emailRanges = emails.map { it.start to it.end }

    val phones = findPhones(text).filterNot {
        overlapsAny(it.start, it.end, noteLinkRanges + webLinkRanges + emailRanges)
    }

    if (webLinks.isEmpty() && emails.isEmpty() && phones.isEmpty()) return this

    val builder = AnnotatedString.Builder(this)
    fun tagSpan(tag: String, start: Int, end: Int, value: String) {
        if (isCurrentlyHovered(hoveredLink, tag, start, end)) {
            builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
        }
        builder.addStringAnnotation(tag, value, start, end)
    }

    webLinks.forEach { tagSpan(WEB_LINK_TAG, it.start, it.end, it.url) }
    emails.forEach { tagSpan(EMAIL_LINK_TAG, it.start, it.end, it.email) }
    phones.forEach { tagSpan(PHONE_LINK_TAG, it.start, it.end, it.phone) }

    return builder.toAnnotatedString()
}

fun TextLayoutResult.webLinkAtPosition(position: Offset): String? =
    (hoveredLinkAt(position, emptySet()) as? HoveredLink.Web)?.url

fun TextLayoutResult.hoveredLinkAt(position: Offset, validNoteIds: Set<String>): HoveredLink? {
    val line = getLineForVerticalPosition(position.y)
    if (position.x < getLineLeft(line) || position.x > getLineRight(line)) return null

    val laidOutText = layoutInput.text
    if (laidOutText.isEmpty()) return null

    val offset = getOffsetForPosition(position)
    val start = maxOf(0, offset - 1)
    val end = minOf(laidOutText.length, offset + 1)

    laidOutText.getStringAnnotations(WEB_LINK_TAG, start, end).firstOrNull()?.let { webLink ->
        return HoveredLink.Web(anchorAround(webLink.start, webLink.end), webLink.item, webLink.start, webLink.end)
    }

    laidOutText.getStringAnnotations(EMAIL_LINK_TAG, start, end).firstOrNull()?.let { emailLink ->
        return HoveredLink.Email(anchorAround(emailLink.start, emailLink.end), emailLink.item, emailLink.start, emailLink.end)
    }

    laidOutText.getStringAnnotations(PHONE_LINK_TAG, start, end).firstOrNull()?.let { phoneLink ->
        return HoveredLink.Phone(anchorAround(phoneLink.start, phoneLink.end), phoneLink.item, phoneLink.start, phoneLink.end)
    }

    laidOutText.getStringAnnotations(NOTE_LINK_TAG, start, end).firstOrNull()?.let { noteLink ->
        if (validNoteIds.contains(noteLink.item)) {
            return HoveredLink.Note(anchorAround(noteLink.start, noteLink.end), noteLink.item, noteLink.start, noteLink.end)
        }
    }

    return null
}

private fun TextLayoutResult.anchorAround(start: Int, end: Int): Rect {
    val lastCharacter = maxOf(0, layoutInput.text.length - 1)
    val firstCharacterBox = getBoundingBox(start.coerceIn(0, lastCharacter))
    val lastCharacterBox = getBoundingBox((end - 1).coerceIn(0, lastCharacter))

    return Rect(
        left = minOf(firstCharacterBox.left, lastCharacterBox.left),
        top = minOf(firstCharacterBox.top, lastCharacterBox.top),
        right = maxOf(firstCharacterBox.right, lastCharacterBox.right),
        bottom = maxOf(firstCharacterBox.bottom, lastCharacterBox.bottom)
    )
}

fun webLinkHost(url: String): String = url
    .substringAfter("://")
    .substringBefore('/')
    .removePrefix("www.")
    .ifBlank { url }

@Composable
fun rememberLinkHoverState(): LinkHoverState = remember { LinkHoverState() }

@Composable
fun Modifier.linkHover(
    hoverState: LinkHoverState,
    validNoteIds: Set<String> = emptySet(),
    currentTextLayout: () -> TextLayoutResult?
): Modifier {
    if (!isDesktopPlatform) return this

    return this
        .pointerInput(validNoteIds) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    when (event.type) {
                        PointerEventType.Exit, PointerEventType.Press -> hoverState.clear()
                        PointerEventType.Enter, PointerEventType.Move -> {
                            val position = event.changes.firstOrNull()?.position
                            val layout = currentTextLayout()
                            hoverState.onHover(
                                if (position == null || layout == null) null
                                else layout.hoveredLinkAt(position, validNoteIds)
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
        .then(
            if (hoverState.hoveredLink != null) {
                Modifier.desktopPointerCursor(DesktopCursor.HAND, overrideDescendants = true)
            } else {
                Modifier
            }
        )
}

@Composable
fun Modifier.openLinksOnPress(
    validNoteIds: Set<String> = emptySet(),
    onOpenWebLink: (String) -> Unit,
    onOpenNoteLink: (String) -> Unit = {},
    onOpenEmail: (String) -> Unit = {},
    onOpenPhone: (String) -> Unit = {},
    onRightClickLink: (HoveredLink, Offset) -> Unit = { _, _ -> },
    currentTextLayout: () -> TextLayoutResult?
): Modifier {
    if (!isDesktopPlatform) return this

    return this.pointerInput(validNoteIds) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Press) continue

                val opensTheLink = event.buttons.isPrimaryPressed
                val opensTheMenu = event.buttons.isSecondaryPressed
                if (!opensTheLink && !opensTheMenu) continue

                val press = event.changes.firstOrNull() ?: continue
                if (press.isConsumed) continue

                val link = currentTextLayout()?.hoveredLinkAt(press.position, validNoteIds) ?: continue
                press.consume()

                if (opensTheMenu) {
                    onRightClickLink(link, press.position)
                } else {
                    when (link) {
                        is HoveredLink.Web -> onOpenWebLink(link.url)
                        is HoveredLink.Note -> onOpenNoteLink(link.noteId)
                        is HoveredLink.Email -> onOpenEmail(link.email)
                        is HoveredLink.Phone -> onOpenPhone(link.phone)
                    }
                }
            }
        }
    }
}

private val LinkBlueOnLightBackground = Color(0xFF1A56DB)
private val LinkBlueOnDarkBackground = Color(0xFF74A9FF)

@Composable
fun rememberWebLinkColor(): Color =
    if (LocalAppIsDark.current) LinkBlueOnDarkBackground else LinkBlueOnLightBackground

@Composable
fun rememberWebLinkActions(): WebLinkActions {
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    return remember(uriHandler, clipboardManager) {
        WebLinkActions(
            openLink = { url ->
                val link = toOpenableWebLink(url)
                if (!openLinkInRunningBrowser(link)) {
                    try {
                        uriHandler.openUri(link)
                    } catch (_: Exception) {
                        showNativeToast("could not open link")
                    }
                }
            },
            openEmail = { email ->
                try {
                    uriHandler.openUri("mailto:$email")
                } catch (_: Exception) {
                    showNativeToast("could not open email")
                }
            },
            openPhone = { phone ->
                try {
                    uriHandler.openUri("tel:$phone")
                } catch (_: Exception) {
                    showNativeToast("could not open phone")
                }
            },
            copyLink = { url ->
                try {
                    clipboardManager.setText(AnnotatedString(url))
                    triggerHapticFeedback()
                    showNativeToast("copied link")
                } catch (_: Exception) {
                    showNativeToast("could not copy link")
                }
            }
        )
    }
}

data class WebLinkVisualTransformation(
    private val inlineSpans: List<InlineSpan> = emptyList(),
    private val hoveredLink: HoveredLink? = null,
    private val highlightColor: Color = Color.Unspecified
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = text.withInteractiveLinksHighlighted(hoveredLink)
        if (inlineSpans.isEmpty()) return TransformedText(highlighted, OffsetMapping.Identity)

        val builder = AnnotatedString.Builder(highlighted)
        inlineSpans.forEach { span ->
            val start = span.start.coerceIn(0, highlighted.length)
            val end = span.end.coerceIn(start, highlighted.length)
            if (start == end) return@forEach
            val decoration = when {
                span.strikeThrough && span.underline -> TextDecoration.LineThrough + TextDecoration.Underline
                span.strikeThrough -> TextDecoration.LineThrough
                span.underline -> TextDecoration.Underline
                else -> null
            }
            builder.addStyle(
                SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    textDecoration = decoration,
                    background = if (span.highlight) highlightColor else Color.Unspecified
                ),
                start,
                end
            )
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
