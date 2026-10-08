package com.qtekfun.ultimatephone.feature.onboarding

/** The screens of the first-run flow, in order. Everything except the welcome and the summary can be skipped and done later from Settings. */
enum class OnboardingStep(val skippable: Boolean) {
    WELCOME(skippable = false),
    ROLES(skippable = true),
    PERMISSIONS(skippable = true),
    DATA(skippable = true),
    NEXTCLOUD(skippable = true),
    BATTERY(skippable = true),
    SUMMARY(skippable = false)
}

/**
 * Where the user is in the flow. [skipped] are the steps passed with "skip" (listed in the summary as things to do later);
 * [completed] are the ones left with "continue". [finished] is set by [OnboardingFlow.finish].
 */
data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val skipped: Set<OnboardingStep> = emptySet(),
    val completed: Set<OnboardingStep> = emptySet(),
    val finished: Boolean = false
) {
    val isFirst: Boolean get() = step == OnboardingStep.entries.first()
    val isLast: Boolean get() = step == OnboardingStep.entries.last()

    /** 1-based position and total, for "Step 3 of 7". */
    val position: Int get() = step.ordinal + 1
    val total: Int get() = OnboardingStep.entries.size
}

/** The state machine of the onboarding. Pure: the screens only call these and render the result. */
object OnboardingFlow {
    /** Leaves the current step with "continue". Does nothing on the last step: use [finish]. */
    fun next(state: OnboardingState): OnboardingState {
        if (state.finished || state.isLast) return state
        return state.copy(
            step = OnboardingStep.entries[state.step.ordinal + 1],
            completed = state.completed + state.step,
            skipped = state.skipped - state.step
        )
    }

    /** Leaves the current step with "skip"; only steps that allow it can be skipped. */
    fun skip(state: OnboardingState): OnboardingState {
        if (state.finished || state.isLast || !state.step.skippable) return state
        return state.copy(
            step = OnboardingStep.entries[state.step.ordinal + 1],
            skipped = state.skipped + state.step,
            completed = state.completed - state.step
        )
    }

    fun back(state: OnboardingState): OnboardingState {
        if (state.finished || state.isFirst) return state
        return state.copy(step = OnboardingStep.entries[state.step.ordinal - 1])
    }

    /** Ends the flow. Only possible from the summary. */
    fun finish(state: OnboardingState): OnboardingState = if (state.isLast) state.copy(finished = true, completed = state.completed + state.step) else state

    /** Steps the summary lists as "you can do this later in Settings". */
    fun pendingForLater(state: OnboardingState): List<OnboardingStep> = OnboardingStep.entries.filter { it in state.skipped }

    /** Rebuilds a state saved as plain strings (process death); unknown names are ignored. */
    fun restore(step: String?, skipped: Collection<String>, completed: Collection<String>): OnboardingState {
        fun parse(name: String) = OnboardingStep.entries.firstOrNull { it.name == name }
        return OnboardingState(
            step = step?.let(::parse) ?: OnboardingStep.WELCOME,
            skipped = skipped.mapNotNull(::parse).toSet(),
            completed = completed.mapNotNull(::parse).toSet()
        )
    }
}
