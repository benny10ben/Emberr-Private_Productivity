// The markers the writer and reader must both agree on, kept in one place so they cannot drift apart.

package com.emberr.domain.vault

object VaultFormat {

    const val VOICE_FENCE_NAME = "emberr-voice"
    const val CANVAS_FENCE_NAME = "emberr-canvas"

    const val TOGGLE_MARKER = "- ▸ "
    const val CHECKED_MARKER = "- [x] "
    const val UNCHECKED_MARKER = "- [ ] "
    const val BULLET_MARKER = "- "

    const val SOLID_DIVIDER_LINE = "---"
    const val DOT_DIVIDER_LINE = "* * *"

    const val DEFAULT_CODE_LANGUAGE = "plaintext"
    const val MAX_HEADING_LEVEL = 6
    const val MAX_INDENT_LEVELS = 10
    const val SPACES_PER_INDENT_LEVEL = 2
}
