package com.emberr.presentation.shared.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.emberr.domain.model.LinkedNoteBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.TextAlignment
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.arrow_down
import emberr.shared.generated.resources.arrow_right2
import emberr.shared.generated.resources.arrow_up
import emberr.shared.generated.resources.check_square
import emberr.shared.generated.resources.code
import emberr.shared.generated.resources.copy
import emberr.shared.generated.resources.eye3
import emberr.shared.generated.resources.format_bold
import emberr.shared.generated.resources.indent_left
import emberr.shared.generated.resources.indent_right
import emberr.shared.generated.resources.italic
import emberr.shared.generated.resources.ordered_list
import emberr.shared.generated.resources.quote_down2
import emberr.shared.generated.resources.scissor2
import emberr.shared.generated.resources.text_x
import emberr.shared.generated.resources.textalign_center2
import emberr.shared.generated.resources.textalign_justifycenter2
import emberr.shared.generated.resources.textalign_left2
import emberr.shared.generated.resources.textalign_right2
import emberr.shared.generated.resources.thumbtack
import emberr.shared.generated.resources.trash
import emberr.shared.generated.resources.underline
import emberr.shared.generated.resources.unordered_list
import emberr.shared.generated.resources.x

@Composable
fun BlockSelectionMenuContent(
    selectedCount: Int,
    onCloseMenu: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    selectedBlocks: List<NoteBlock> = emptyList(),
    isSelectionPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    onAddBlockAbove: (() -> Unit)? = null,
    onAddBlockBelow: (() -> Unit)? = null,
    onChangeBlockType: ((String) -> Unit)? = null,
    onToggleFormat: ((String) -> Unit)? = null,
    onSetAlignment: ((TextAlignment) -> Unit)? = null,
    onAdjustIndentation: ((Boolean) -> Unit)? = null,
    onUpdateLinkedNoteOptions: ((id: String, showIcon: Boolean, showCoverImage: Boolean) -> Unit)? = null
) {
    fun closeThen(action: () -> Unit): () -> Unit = {
        onCloseMenu()
        action()
    }

    val selectedLinkedNote = selectedBlocks.singleOrNull() as? LinkedNoteBlock

    val sections = buildList {
        add(
            SlashMenuSectionData(
                title = if (selectedCount == 1) "1 Block Selected" else "$selectedCount Blocks Selected",
                items = buildList {
                    add(SlashMenuItemData("Copy", Res.drawable.copy, action = closeThen(onCopy)))
                    add(SlashMenuItemData("Cut", Res.drawable.scissor2, action = closeThen(onCut)))
                    if (onTogglePin != null) {
                        add(
                            SlashMenuItemData(
                                label = if (isSelectionPinned) "Unpin" else "Pin",
                                icon = Res.drawable.thumbtack,
                                action = closeThen(onTogglePin)
                            )
                        )
                    }
                    if (onAddBlockAbove != null) {
                        add(SlashMenuItemData("Add Block Above", Res.drawable.arrow_up, action = closeThen(onAddBlockAbove)))
                    }
                    if (onAddBlockBelow != null) {
                        add(SlashMenuItemData("Add Block Below", Res.drawable.arrow_down, action = closeThen(onAddBlockBelow)))
                    }
                    add(SlashMenuItemData("Select All Blocks", Icons.Default.SelectAll, action = closeThen(onSelectAll)))
                    add(SlashMenuItemData("Clear Selection", Res.drawable.x, 14.dp, action = closeThen(onClearSelection)))
                    add(SlashMenuItemData("Delete", Res.drawable.trash, action = closeThen(onDelete)))
                }
            )
        )

        if (selectedLinkedNote != null && onUpdateLinkedNoteOptions != null) {
            add(
                SlashMenuSectionData(
                    title = "Preview",
                    items = listOf(
                        SlashMenuItemData(
                            label = if (selectedLinkedNote.showIcon) "Hide Note Icon" else "Show Note Icon",
                            icon = Res.drawable.eye3,
                            action = closeThen {
                                onUpdateLinkedNoteOptions(
                                    selectedLinkedNote.id,
                                    !selectedLinkedNote.showIcon,
                                    selectedLinkedNote.showCoverImage
                                )
                            }
                        ),
                        SlashMenuItemData(
                            label = if (selectedLinkedNote.showCoverImage) "Hide Cover Image" else "Show Cover Image",
                            icon = Res.drawable.eye3,
                            action = closeThen {
                                onUpdateLinkedNoteOptions(
                                    selectedLinkedNote.id,
                                    selectedLinkedNote.showIcon,
                                    !selectedLinkedNote.showCoverImage
                                )
                            }
                        )
                    )
                )
            )
        }

        if (onChangeBlockType != null) {
            add(
                SlashMenuSectionData(
                    title = "Turn Into",
                    items = listOf(
                        SlashMenuItemData("Text", Icons.AutoMirrored.Filled.Subject, action = closeThen { onChangeBlockType("text") }),
                        SlashMenuItemData("Heading 1", SlashMenuIcon.Label("H1"), closeThen { onChangeBlockType("h1") }),
                        SlashMenuItemData("Heading 2", SlashMenuIcon.Label("H2"), closeThen { onChangeBlockType("h2") }),
                        SlashMenuItemData("To-do List", Res.drawable.check_square, action = closeThen { onChangeBlockType("checkbox") }),
                        SlashMenuItemData("Bulleted List", Res.drawable.unordered_list, 15.dp, closeThen { onChangeBlockType("bullet") }),
                        SlashMenuItemData("Numbered List", Res.drawable.ordered_list, 14.dp, closeThen { onChangeBlockType("number") }),
                        SlashMenuItemData("Toggle List", Res.drawable.arrow_right2, action = closeThen { onChangeBlockType("toggle") }),
                        SlashMenuItemData("Quote", Res.drawable.quote_down2, 14.dp, closeThen { onChangeBlockType("quote") }),
                        SlashMenuItemData("Code Block", Res.drawable.code, action = closeThen { onChangeBlockType("code") })
                    )
                )
            )
        }

        if (onToggleFormat != null) {
            add(
                SlashMenuSectionData(
                    title = "Formatting",
                    items = listOf(
                        SlashMenuItemData("Bold", Res.drawable.format_bold, 13.dp, closeThen { onToggleFormat("bold") }),
                        SlashMenuItemData("Italic", Res.drawable.italic, 13.dp, closeThen { onToggleFormat("italic") }),
                        SlashMenuItemData("Underline", Res.drawable.underline, 15.dp, closeThen { onToggleFormat("underline") }),
                        SlashMenuItemData("Strikethrough", Res.drawable.text_x, 15.dp, closeThen { onToggleFormat("strike") })
                    )
                )
            )
        }

        if (onSetAlignment != null) {
            add(
                SlashMenuSectionData(
                    title = "Alignment",
                    items = listOf(
                        SlashMenuItemData("Align Left", Res.drawable.textalign_left2, action = closeThen { onSetAlignment(TextAlignment.LEFT) }),
                        SlashMenuItemData("Align Right", Res.drawable.textalign_right2, action = closeThen { onSetAlignment(TextAlignment.RIGHT) }),
                        SlashMenuItemData("Align Center", Res.drawable.textalign_center2, action = closeThen { onSetAlignment(TextAlignment.CENTER) }),
                        SlashMenuItemData("Justify", Res.drawable.textalign_justifycenter2, action = closeThen { onSetAlignment(TextAlignment.JUSTIFY) })
                    )
                )
            )
        }

        if (onAdjustIndentation != null) {
            add(
                SlashMenuSectionData(
                    title = "Indentation",
                    items = listOf(
                        SlashMenuItemData("Decrease Indent", Res.drawable.indent_right, action = closeThen { onAdjustIndentation(false) }),
                        SlashMenuItemData("Increase Indent", Res.drawable.indent_left, action = closeThen { onAdjustIndentation(true) })
                    )
                )
            )
        }
    }

    SlashMenuList(sections = sections)
}
