package com.example.adsdk.core

import com.example.adsdk.scheduler.ScheduledBreak

data class AdEngineState(
    override val phase: AdState.Phase = AdState.Phase.Idle,
    val loaded: Boolean = false,
    val inAd: Boolean = false,
    val currentBreak: ScheduledBreak? = null,
    val lastError: Throwable? = null,
) : AdState

