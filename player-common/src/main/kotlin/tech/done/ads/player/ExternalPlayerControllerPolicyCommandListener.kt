package tech.done.ads.player

class ExternalPlayerControllerPolicyCommandListener(
    private val delegate: PlayerCommandListener,
) : PlayerCommandListener {
    override fun onPlayAdRequested(
        mediaUri: String,
        adSkipOffsetMs: Long?,
        simidInteractiveCreativeUrl: String?,
    ) {
        ExternalPlayerViewControllerPolicy.apply(resolveExternalPlayerView(delegate), inAd = true)
        delegate.onPlayAdRequested(mediaUri, adSkipOffsetMs, simidInteractiveCreativeUrl)
    }

    override fun onResumeContentRequested() {
        ExternalPlayerViewControllerPolicy.apply(resolveExternalPlayerView(delegate), inAd = false)
        delegate.onResumeContentRequested()
    }

    override fun onPauseRequested() {
        delegate.onPauseRequested()
    }

    override fun onPlayRequested() {
        delegate.onPlayRequested()
    }

    override fun onSeekingEnabledChanged(enabled: Boolean) {
        delegate.onSeekingEnabledChanged(enabled)
    }

    private fun resolveExternalPlayerView(root: Any?): Any? {
        if (root == null) return null
        val queue = ArrayDeque<Any>()
        val seen = HashSet<Int>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val identity = System.identityHashCode(current)
            if (!seen.add(identity)) continue
            if (looksLikePlayerView(current)) return current

            current.javaClass.declaredFields.forEach { field ->
                runCatching {
                    field.isAccessible = true
                    val value = field.get(current) ?: return@runCatching
                    if (looksLikePlayerView(value)) return value
                    val className = value.javaClass.name
                    if (className.startsWith("java.") || className.startsWith("kotlin.")) return@runCatching
                    queue.add(value)
                }
            }
        }
        return null
    }

    private fun looksLikePlayerView(target: Any): Boolean {
        val clazz = target.javaClass
        val hasHideController = runCatching { clazz.getMethod("hideController") }.isSuccess
        val hasFindViewById = runCatching {
            clazz.getMethod("findViewById", Int::class.javaPrimitiveType)
        }.isSuccess
        return hasHideController && hasFindViewById
    }
}
