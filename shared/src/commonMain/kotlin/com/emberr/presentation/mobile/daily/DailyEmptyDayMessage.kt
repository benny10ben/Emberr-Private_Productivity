package com.emberr.presentation.mobile.daily

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberr.domain.quote.DailyQuote
import com.emberr.domain.quote.DailyQuoteLibrary
import com.emberr.domain.util.system.isDesktopPlatform
import kotlinx.datetime.LocalDate

@Composable
fun DailyEmptyDayMessage(
    date: LocalDate,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp
) {
    val quoteOfTheDay by produceState<DailyQuote?>(initialValue = null, key1 = date) {
        value = DailyQuoteLibrary.quoteForDate(date)
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    val halfwayBetweenQuoteAndBottom = BiasAlignment(horizontalBias = 0f, verticalBias = 0.5f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding, bottom = bottomPadding)
    ) {
        AnimatedVisibility(
            visible = quoteOfTheDay != null,
            enter = fadeIn(tween(durationMillis = 350)),
            exit = fadeOut(tween(durationMillis = 150)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            quoteOfTheDay?.let { quote ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isDesktopPlatform) 72.dp else 44.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = quote.text,
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                        color = textColor.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center
                    )
                    if (quote.author.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "— ${quote.author}",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.35f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Text(
            text = if (isDesktopPlatform) "Double click to write your thoughts" else "Double tap to write your thoughts",
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.3f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(halfwayBetweenQuoteAndBottom)
                .padding(horizontal = 24.dp)
        )
    }
}
