package com.example.adsdk.core

/**
 * Minimal state contract for AdEngine.
 *
 * Keep this tiny for now; concrete state modeling can evolve without breaking API.
 */
interface AdState {
    val phase: Phase

    enum class Phase {
        Idle,
        Initialized,
        Running,
        Stopped,
        Released,
        Error,
    }
}

