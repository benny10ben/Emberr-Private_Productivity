package com.emberr.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emberr.data.local.room.DatabaseTemplateEntity
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.rememberShowAfterKeyboardCloses
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.files
import emberr.shared.generated.resources.hash
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseTemplatePickerSheet(
    expanded: Boolean,
    templates: List<DatabaseTemplateEntity>,
    onDismiss: () -> Unit,
    onCreateBlank: () -> Unit,
    onSelectTemplate: (DatabaseTemplateEntity) -> Unit
) {
    val isKeyboardOutOfTheWay = rememberShowAfterKeyboardCloses(expanded)

    EmberrBottomSheet(expanded = isKeyboardOutOfTheWay, onDismiss = onDismiss, title = "Add Database") { closeAnd ->
        MuteRippleOnMobile {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
                SheetMenuRow(icon = painterResource(Res.drawable.hash), text = "Create Blank Database") {
                    closeAnd {
                        onDismiss()
                        onCreateBlank()
                    }
                }

                if (templates.isNotEmpty()) {
                    SheetDivider()
                    SheetSectionLabel("Saved Templates", Modifier.padding(vertical = 6.dp))
                    templates.forEach { template ->
                        SheetMenuRow(icon = painterResource(Res.drawable.files), text = template.name) {
                            closeAnd {
                                onDismiss()
                                onSelectTemplate(template)
                            }
                        }
                    }
                }

                EmberrButtonPrimary(
                    text = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            }
        }
    }
}
