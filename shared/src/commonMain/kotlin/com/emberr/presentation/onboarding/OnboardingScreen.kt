package com.emberr.presentation.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.emberr.domain.util.system.isDesktopPlatform
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel()
) {
    val steps = rememberOnboardingSteps()

    var currentStepIndex by rememberSaveable { mutableIntStateOf(0) }
    var isFinishing by remember { mutableStateOf(false) }

    val currentStep = steps[currentStepIndex]
    val isLastStep = currentStepIndex == steps.lastIndex

    fun finishOnboarding() {
        if (isFinishing) return
        isFinishing = true
        viewModel.completeOnboarding()
        onFinished()
    }

    fun goToNextStep() {
        if (isLastStep) finishOnboarding() else currentStepIndex++
    }

    fun goToPreviousStep() {
        if (currentStepIndex > 0) currentStepIndex--
    }

    OnboardingFlowScaffold(
        statements = steps,
        currentStepIndex = currentStepIndex,
        showSkip = !isLastStep,
        onSkip = ::finishOnboarding,
        onSwipeForward = if (isDesktopPlatform) null else ::goToNextStep,
        onSwipeBackward = if (isDesktopPlatform) null else ::goToPreviousStep,
        panel = {
            OnboardingChoicePanel(
                choice = currentStep.choice,
                viewModel = viewModel,
                modifier = Modifier.fillMaxWidth()
            )
        },
        bottomBar = {
            OnboardingFlowBottomBar(
                label = currentStep.buttonLabel,
                showBack = currentStepIndex > 0,
                onBack = ::goToPreviousStep,
                onNext = ::goToNextStep
            )
        }
    )
}
