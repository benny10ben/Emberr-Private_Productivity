package com.emberr.presentation.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberr.presentation.shared.SubNoteOpenMode
import com.emberr.ui.theme.LocalAppIsDark
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.arrow_up
import org.jetbrains.compose.resources.painterResource

private const val SidebarWidthFraction = 0.24f
private const val FullPanelWidthFraction = 1f - SidebarWidthFraction

@Composable
fun OnboardingWindowPreview(
    openMode: SubNoteOpenMode,
    modifier: Modifier = Modifier
) {
    val windowShape = RoundedCornerShape(14.dp)
    val panelFill = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val panelBorder = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(windowShape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, onboardingHairlineColor(), windowShape)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            MockSidebar()
            MockEditor()
        }

        Crossfade(
            targetState = openMode,
            animationSpec = tween(360),
            label = "onboarding-subnote-open-mode"
        ) { visibleMode ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (visibleMode) {
                    SubNoteOpenMode.SIDE_PANEL -> MockPanel(
                        fillColor = panelFill,
                        borderColor = panelBorder,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .fillMaxWidth(0.34f)
                    )

                    SubNoteOpenMode.CENTER_DIALOG -> MockPanel(
                        fillColor = panelFill,
                        borderColor = panelBorder,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxHeight(0.68f)
                            .fillMaxWidth(0.56f),
                        cornerRadius = 10.dp
                    )

                    SubNoteOpenMode.FULL_RIGHT_PANEL -> MockPanel(
                        fillColor = panelFill,
                        borderColor = panelBorder,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .fillMaxWidth(FullPanelWidthFraction)
                    )
                }
            }
        }
    }
}

@Composable
private fun MockSidebar() {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(SidebarWidthFraction)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        repeat(4) { index ->
            MockLine(widthFraction = if (index == 0) 0.9f else 0.72f, height = 7.dp)
        }
    }
}

@Composable
private fun MockEditor() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        MockLine(widthFraction = 0.55f, height = 12.dp, opacity = 0.22f)
        Spacer(modifier = Modifier.height(3.dp))
        repeat(5) { index ->
            MockLine(widthFraction = if (index % 3 == 2) 0.6f else 0.94f, height = 7.dp)
        }
    }
}

@Composable
private fun MockLine(
    widthFraction: Float,
    height: Dp,
    opacity: Float = 0.12f
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = opacity))
    )
}

@Composable
private fun MockPanel(
    fillColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp
) {
    val panelShape = RoundedCornerShape(cornerRadius)

    Column(
        modifier = modifier
            .clip(panelShape)
            .background(MaterialTheme.colorScheme.background)
            .background(fillColor)
            .border(1.dp, borderColor, panelShape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MockLine(widthFraction = 0.7f, height = 9.dp, opacity = 0.26f)
        repeat(3) {
            MockLine(widthFraction = 0.9f, height = 6.dp, opacity = 0.16f)
        }
    }
}


@Composable
fun onboardingHairlineColor(): Color =
    MaterialTheme.colorScheme.onSurface.copy(alpha = if (LocalAppIsDark.current) 0.12f else 0.10f)

@Composable
private fun onboardingSurfaceColor(): Color =
    MaterialTheme.colorScheme.onSurface.copy(alpha = if (LocalAppIsDark.current) 0.07f else 0.05f)

@Composable
fun OnboardingChatMock(
    question: String,
    answer: String,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, onboardingHairlineColor(), cardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .widthIn(max = 230.dp)
                    .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }

        Text(
            text = answer,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(onboardingSurfaceColor())
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.arrow_up),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
