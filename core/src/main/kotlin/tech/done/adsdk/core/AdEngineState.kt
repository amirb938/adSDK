package tech.done.adsdk.core

import tech.done.adsdk.scheduler.ScheduledBreak

data class AdEngineState(
    override val phase: AdState.Phase = AdState.Phase.Idle,
    val loaded: Boolean = false,
    val inAd: Boolean = false,
    val currentBreak: ScheduledBreak? = null,
    val lastError: Throwable? = null,
) : AdState

