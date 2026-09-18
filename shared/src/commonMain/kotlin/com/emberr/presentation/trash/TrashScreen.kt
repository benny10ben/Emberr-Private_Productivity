package com.emberr.presentation.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrTopHeaderBar
import com.emberr.presentation.shared.components.TopBarIconButton
import com.emberr.presentation.shared.components.topHeaderBarPadding
import com.emberr.presentation.mobile.home.NoteCard
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.components.EmberrVerticalScrollbar
import com.emberr.presentation.shared.components.smoothWheelScroll
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

private val HORIZONTAL_PADDING = 16.dp

@Composable
fun TrashScreen(
    onNavigateBack: () -> Unit,
    viewModel: TrashViewModel = koinViewModel()
) {
    val trashedNotes by viewModel.trashedNotes.collectAsState()
    var selectedNoteToManage by remember { mutableStateOf<NoteMetadataEntity?>(null) }
    var showEmptyTrashConfirm by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableFloatStateOf(0f) }
    val topBarHeightDp = with(density) { topBarHeightPx.toDp() }

    val backgroundColor = if (isDesktopPlatform) Color.Transparent else MaterialTheme.colorScheme.background
    val hazeState = remember { HazeState() }

    Box(modifier = Modifier.fillMaxSize()) {
        if (trashedNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .background(backgroundColor)
                    .padding(top = topBarHeightDp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Trash is empty",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            val gridState = rememberLazyGridState()

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(
                    top = topBarHeightDp + 8.dp,
                    bottom = 80.dp,
                    start = HORIZONTAL_PADDING,
                    end = HORIZONTAL_PADDING
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .background(backgroundColor)
                    .smoothWheelScroll(gridState)
            ) {
                items(trashedNotes, key = { it.noteId }) { note ->
                    NoteCard(
                        note = note,
                        isSelected = false,
                        onClick = { selectedNoteToManage = note },
                        onLongClick = { selectedNoteToManage = note }
                    )
                }
            }

            EmberrVerticalScrollbar(
                gridState = gridState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(top = topBarHeightDp + 8.dp, bottom = 24.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(10f)
                .onGloballyPositioned { coordinates -> topBarHeightPx = coordinates.size.height.toFloat() }
        ) {
            EmberrTopHeaderBar(
                title = "Trash",
                hazeState = hazeState,
                contentPadding = topHeaderBarPadding(bottom = 16.dp),
                onBackClick = onNavigateBack,
                actions = {
                    if (trashedNotes.isNotEmpty()) {
                        TopBarIconButton(
                            icon = Icons.Default.DeleteSweep,
                            contentDescription = "Empty Trash",
                            bgColor = Color.Transparent,
                            tint = MaterialTheme.colorScheme.error,
                            hazeState = hazeState,
                            hazeStyle = EmberrBlur.Regular,
                            onClick = { showEmptyTrashConfirm = true }
                        )
                    }
                }
            )
        }

        ManageNoteBottomSheet(
            expanded = selectedNoteToManage != null,
            onDismiss = { selectedNoteToManage = null },
            onRestore = {
                val noteId = selectedNoteToManage?.noteId ?: return@ManageNoteBottomSheet
                selectedNoteToManage = null
                viewModel.restoreNote(noteId)
            },
            onPermanentlyDelete = {
                val note = selectedNoteToManage ?: return@ManageNoteBottomSheet
                selectedNoteToManage = null
                viewModel.permanentlyDelete(note.noteId, note.filePath)
            }
        )

        EmptyTrashBottomSheet(
            expanded = showEmptyTrashConfirm,
            onDismiss = { showEmptyTrashConfirm = false },
            onConfirmEmpty = {
                showEmptyTrashConfirm = false
                viewModel.emptyTrash()
            }
        )
    }
}

@Composable
fun ManageNoteBottomSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onPermanentlyDelete: () -> Unit
) {
    val scope = rememberCoroutineScope()

    EmberrBottomSheet(
        expanded = expanded,
        onDismiss = onDismiss,
        title = "Manage Note",
        subtitle = "Notes in trash are automatically deleted after 30 days."
    ) { closeAnd ->

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            BottomSheetActionItem(Icons.Default.Restore, "Restore Note") {
                closeAnd {
                    scope.launch { delay(250.milliseconds); onRestore() }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            BottomSheetActionItem(
                Icons.Default.DeleteForever,
                "Delete Permanently",
                isDestructive = true
            ) {
                closeAnd {
                    scope.launch { delay(250.milliseconds); onPermanentlyDelete() }
                }
            }

            EmberrButtonPrimary(
                text = "Cancel",
                onClick = { closeAnd(onDismiss) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
fun EmptyTrashBottomSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onConfirmEmpty: () -> Unit
) {
    val scope = rememberCoroutineScope()

    EmberrBottomSheet(
        expanded = expanded,
        onDismiss = onDismiss,
        title = "Empty Trash?",
        subtitle = "This will permanently delete all notes currently in the trash. This action cannot be undone."
    ) { closeAnd ->

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            BottomSheetActionItem(Icons.Default.DeleteSweep, "Empty Trash", isDestructive = true) {
                closeAnd {
                    scope.launch { delay(250.milliseconds); onConfirmEmpty() }
                }
            }

            EmberrButtonPrimary(
                text = "Cancel",
                onClick = { closeAnd(onDismiss) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun BottomSheetActionItem(icon: ImageVector, text: String, isDestructive: Boolean = false, onClick: () -> Unit) {
    val textColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = textColor)
    }
}
