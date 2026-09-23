package com.emberr.presentation.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.emberr.domain.util.system.isDesktopPlatform
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.arrow_up_down
import emberr.shared.generated.resources.astroid
import emberr.shared.generated.resources.bell
import emberr.shared.generated.resources.calendar
import emberr.shared.generated.resources.code
import emberr.shared.generated.resources.daily
import emberr.shared.generated.resources.ghost_smile
import emberr.shared.generated.resources.house
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
                icon = Res.drawable.house,
                lead = "No account. No cloud.",
                headline = "It works offline.",
                detail = "Everything you write stays encrypted on this device."
            )
        )
        add(
            OnboardingStep(
                icon = Res.drawable.widget,
                lead = "Write the way you think.",
                headline = "Everything is a block.",
                detail = "Text, checklists, tables, images, voice notes and documents in one note."
            )
        )
        add(
            OnboardingStep(
                icon = Res.drawable.daily,
                lead = "Every day gets a page.",
                headline = "Keep a daily log.",
                detail = "Unfinished tasks move themselves into today."
            )
        )
        add(
            OnboardingStep(
                icon = Res.drawable.calendar,
                lead = "Step back a month.",
                headline = "See it on a calendar.",
                detail = "Every task and reminder in one view, repeats included."
            )
        )
        add(
            OnboardingStep(
                icon = Res.drawable.bell,
                lead = "Any checkbox can ring.",
                headline = "Reminders to the minute.",
                detail = "Emberr notifies you at the exact time you set."
            )
        )
        add(
            OnboardingStep(
                icon = Res.drawable.transfer_h,
                lead = "Your devices, your network.",
                headline = "Sync without a middleman.",
                detail = "Pair devices over your local network, or point Emberr at a server you own."
            )
        )
        add(
            OnboardingStep(
                icon = Res.drawable.shield_alert,
                lead = "Private by design.",
                headline = "Nothing leaves this device.",
                detail = "Notes are stored and searched locally, and never uploaded by default."
            )
        )
        add(
            OnboardingStep(
                icon = Res.drawable.ghost_smile,
                lead = "No trackers. No ads.",
                headline = "Nobody is watching.",
                detail = "Emberr collects no analytics and has nothing to sell."
            )
        )
        add(
            OnboardingStep(
                icon = Res.drawable.code,
                lead = "And when it does sync,",
                headline = "your words stay sealed.",
                detail = "Content is encrypted before it leaves, so your server only ever holds ciphertext."
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
