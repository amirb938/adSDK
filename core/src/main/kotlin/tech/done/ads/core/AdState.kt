package tech.done.ads.core

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

