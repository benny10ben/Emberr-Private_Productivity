package com.emberr.presentation.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.emberr.domain.util.system.isDesktopPlatform
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.arrow_up_down
import emberr.shared.generated.resources.astroid
import emberr.shared.generated.resources.daily
import emberr.shared.generated.resources.pen_square
import emberr.shared.generated.resources.shield_alert
import emberr.shared.generated.resources.sidebar
import emberr.shared.generated.resources.sliders_horizontal
import emberr.shared.generated.resources.square_check
import emberr.shared.generated.resources.text_tool_2
import emberr.shared.generated.resources.text_type
import emberr.shared.generated.resources.transfer_h
import emberr.shared.generated.resources.widget
import org.jetbrains.compose.resources.DrawableResource

enum class OnboardingChoice {
    NONE,
    TYPEFACE,
    TEXT_SIZE,
    AI_ASSISTANT,
    DESKTOP_LAYOUT,
    SCROLLBARS,
    PERMISSIONS
}

data class OnboardingStep(
    val icon: DrawableResource,
    val lead: String,
    val headline: String,
    val detail: String? = null,
    val choice: OnboardingChoice = OnboardingChoice.NONE,
    val buttonLabel: String = "Continue",
    val hasRainbowHeadline: Boolean = false
)

@Composable
fun rememberOnboardingSteps(): List<OnboardingStep> = remember {
    buildList {
        add(
            OnboardingStep(
                icon = Res.drawable.pen_square,
                lead = "Welcome to",
                headline = "Emberr.",
                buttonLabel = "Show me around."
            )
        )
        add(
            OnboardingStep(
                icon = Res.drawable.shield_alert,
                lead = "No account. No cloud. No trackers.",
                headline = "Your notes stay yours.",
                detail = if (isDesktopPlatform) {
                    "Everything stays on this device. No analytics, no ads."
                } else {
                    "Everything is encrypted on this device. No analytics, no ads."
                }
            )
        )
        add(
            OnboardingStep(
                icon = Res.drawable.widget,
                lead = "Write the way you think.",
                headline = "Everything is a block.",
                detail = "Text, checklists, tables, databases, images, voice notes and documents all in one note."
            )
        )
        add(
            OnboardingStep(
                icon = Res.drawable.daily,
                lead = "Every day gets a page.",
                headline = "Plan your days.",
                detail = "Unfinished tasks roll into today, everything shows on a calendar, and any checkbox can remind you to the minute."
            )
        )
        add(
            OnboardingStep(
                icon = Res.drawable.transfer_h,
                lead = "Your devices, your network.",
                headline = "Sync without a middleman.",
                detail = "Pair over your local network or use a server you own. Content is encrypted before it leaves."
            )
        )
        add(
            OnboardingStep(
                icon = Res.drawable.text_type,
                lead = "Make it yours.",
                headline = "Pick a typeface.",
                choice = OnboardingChoice.TYPEFACE
            )
        )
        add(
            OnboardingStep(
                icon = Res.drawable.text_tool_2,
                lead = "Comfortable to read?",
                headline = "Set the text size.",
                choice = OnboardingChoice.TEXT_SIZE
            )
        )
        add(
            OnboardingStep(
                icon = Res.drawable.astroid,
                lead = "Ask your own notes.",
                headline = "Bring in an assistant.",
                choice = OnboardingChoice.AI_ASSISTANT
            )
        )
        if (isDesktopPlatform) {
            add(
                OnboardingStep(
                    icon = Res.drawable.sidebar,
                    lead = "One more thing.",
                    headline = "Where sub-notes open.",
                    choice = OnboardingChoice.DESKTOP_LAYOUT
                )
            )
            add(
                OnboardingStep(
                    icon = Res.drawable.arrow_up_down,
                    lead = "Edges, or no edges?",
                    headline = "Show the scrollbars.",
                    choice = OnboardingChoice.SCROLLBARS
                )
            )
        } else {
            add(
                OnboardingStep(
                    icon = Res.drawable.sliders_horizontal,
                    lead = "One more thing.",
                    headline = "Turn on what you need.",
                    choice = OnboardingChoice.PERMISSIONS
                )
            )
        }
        add(
            OnboardingStep(
                icon = Res.drawable.square_check,
                lead = "That is everything.",
                headline = "You are all set.",
                detail = "Every choice you made here lives in Settings.",
                buttonLabel = "Start Using Emberr",
                hasRainbowHeadline = true
            )
        )
    }
}
