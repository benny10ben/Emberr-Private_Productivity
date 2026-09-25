package com.emberr.presentation.home.note

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberr.domain.util.system.isDesktopPlatform

private val SlashKeyShape = RoundedCornerShape(6.dp)

@Composable
fun NoteEmptyPageMessage(
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp
) {
    val textColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding, bottom = bottomPadding)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isDesktopPlatform) "Double click to start writing…" else "Double tap to start writing…",
                style = MaterialTheme.typography.bodyLarge,
                color = textColor.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "then type",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.3f)
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .background(textColor.copy(alpha = 0.07f), SlashKeyShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.45f)
                    )
                }
                Text(
                    text = "for lists, tables and images",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.3f)
                )
            }
        }
    }
}
