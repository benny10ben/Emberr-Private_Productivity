package com.emberr.presentation.shared.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.presentation.shared.components.EmberrPopupMenuRow
import com.emberr.presentation.shared.components.EmberrPopupMenuSurface
import com.emberr.presentation.shared.components.MenuAtClickPositionProvider

@Composable
fun LinkContextMenu(
    hoverState: LinkHoverState,
    onOpenNoteLink: (String) -> Unit = {},
    findNote: (String) -> NoteMetadataEntity? = { null }
) {
    val link = hoverState.rightClickedLink ?: return
    val webLinkActions = rememberWebLinkActions()
    val positionProvider = remember(hoverState.rightClickPosition) {
        MenuAtClickPositionProvider(
            IntOffset(
                hoverState.rightClickPosition.x.toInt(),
                hoverState.rightClickPosition.y.toInt()
            )
        )
    }

    Box(modifier = Modifier.size(1.dp)) {
        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = { hoverState.closeMenu() },
            properties = PopupProperties(focusable = true)
        ) {
            EmberrPopupMenuSurface {
                when (link) {
                    is HoveredLink.Web -> {
                        EmberrPopupMenuRow("Open link") {
                            hoverState.closeMenu()
                            webLinkActions.openLink(link.url)
                        }
                        EmberrPopupMenuRow("Copy link") {
                            hoverState.closeMenu()
                            webLinkActions.copyLink(link.url)
                        }
                    }
                    is HoveredLink.Note -> {
                        val noteTitle = findNote(link.noteId)?.title?.ifBlank { "Untitled" } ?: "Untitled"
                        EmberrPopupMenuRow("Open note") {
                            hoverState.closeMenu()
                            onOpenNoteLink(link.noteId)
                        }
                        EmberrPopupMenuRow("Copy link") {
                            hoverState.closeMenu()
                            webLinkActions.copyLink("[$noteTitle](emberr://note/${link.noteId})")
                        }
                    }
                    is HoveredLink.Email -> {
                        EmberrPopupMenuRow("Send email") {
                            hoverState.closeMenu()
                            webLinkActions.openEmail(link.email)
                        }
                        EmberrPopupMenuRow("Copy email") {
                            hoverState.closeMenu()
                            webLinkActions.copyLink(link.email)
                        }
                    }
                    is HoveredLink.Phone -> {
                        EmberrPopupMenuRow("Call number") {
                            hoverState.closeMenu()
                            webLinkActions.openPhone(link.phone)
                        }
                        EmberrPopupMenuRow("Copy number") {
                            hoverState.closeMenu()
                            webLinkActions.copyLink(link.phone)
                        }
                    }
                }
            }
        }
    }
}
