package tech.done.ads.player

object ExternalPlayerViewControllerPolicy {
    @JvmStatic
    fun apply(playerView: Any?, inAd: Boolean) {
        val view = resolveInnermostPlayerView(playerView) ?: return
        setBooleanFieldIfPresent(view, "useController", !inAd)
        invokeBooleanMethodIfPresent(view, "setUseController", !inAd)
        invokeBooleanMethodIfPresent(view, "setControllerAutoShow", !inAd)
        invokeBooleanMethodIfPresent(view, "setControllerHideOnTouch", !inAd)
        if (inAd) {
            invokeMethodWithIntIfPresent(view, "setControllerShowTimeoutMs", 0)
        }
        if (inAd) {
            invokeNoArgMethodIfPresent(view, "hideController")
            val controller = findControllerView(view)
            setFloatFieldIfPresent(controller, "alpha", 0f)
            setBooleanFieldIfPresent(controller, "enabled", false)
            setBooleanFieldIfPresent(controller, "clickable", false)
            setBooleanFieldIfPresent(controller, "focusable", false)
            invokeBooleanMethodIfPresent(controller, "setEnabled", false)
            invokeBooleanMethodIfPresent(controller, "setClickable", false)
            invokeBooleanMethodIfPresent(controller, "setFocusable", false)
        } else {
            val controller = findControllerView(view)
            setFloatFieldIfPresent(controller, "alpha", 1f)
            setBooleanFieldIfPresent(controller, "enabled", true)
            setBooleanFieldIfPresent(controller, "clickable", true)
            setBooleanFieldIfPresent(controller, "focusable", true)
            invokeBooleanMethodIfPresent(controller, "setEnabled", true)
            invokeBooleanMethodIfPresent(controller, "setClickable", true)
            invokeBooleanMethodIfPresent(controller, "setFocusable", true)
        }
    }

    private fun resolveInnermostPlayerView(root: Any?): Any? {
        if (root == null) return null
        val queue = ArrayDeque<Any>()
        val seen = HashSet<Int>()
        queue.add(root)
        var fallback: Any? = null
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val identity = System.identityHashCode(current)
            if (!seen.add(identity)) continue

            if (looksLikePlayerView(current)) {
                fallback = current
                if (supportsControllerFlags(current)) return current
            }

            runCatching { current.javaClass.getMethod("getPlayerView").invoke(current) }
                .getOrNull()
                ?.let { queue.add(it) }
            runCatching {
                val f = current.javaClass.getDeclaredField("playerView")
                f.isAccessible = true
                f.get(current)
            }.getOrNull()?.let { queue.add(it) }

            current.javaClass.declaredFields.forEach { field ->
                runCatching {
                    field.isAccessible = true
                    val value = field.get(current) ?: return@runCatching
                    val className = value.javaClass.name
                    if (className.startsWith("java.") || className.startsWith("kotlin.")) return@runCatching
                    queue.add(value)
                }
            }
        }
        return fallback
    }

    private fun looksLikePlayerView(target: Any): Boolean {
        val clazz = target.javaClass
        val hasHideController = runCatching { clazz.getMethod("hideController") }.isSuccess
        val hasFindViewById = runCatching {
            clazz.getMethod("findViewById", Int::class.javaPrimitiveType)
        }.isSuccess
        return hasHideController && hasFindViewById
    }

    private fun supportsControllerFlags(target: Any): Boolean {
        val clazz = target.javaClass
        val hasSetUseController = runCatching {
            clazz.getMethod("setUseController", Boolean::class.javaPrimitiveType)
        }.isSuccess
        val hasAutoShow = runCatching {
            clazz.getMethod("setControllerAutoShow", Boolean::class.javaPrimitiveType)
        }.isSuccess
        return hasSetUseController && hasAutoShow
    }

    private fun findControllerView(playerView: Any): Any? {
        val controllerId = resolveExoControllerId()
        if (controllerId == 0) return null
        return runCatching {
            playerView.javaClass.getMethod("findViewById", Int::class.javaPrimitiveType)
                .invoke(playerView, controllerId)
        }.getOrNull()
    }

    private fun resolveExoControllerId(): Int {
        val exo2 = runCatching { Class.forName("com.google.android.exoplayer2.ui.R\$id") }.getOrNull()
        val exo3 = runCatching { Class.forName("androidx.media3.ui.R\$id") }.getOrNull()
        return runCatching { exo2?.getField("exo_controller")?.getInt(null) }.getOrNull()
            ?: runCatching { exo3?.getField("exo_controller")?.getInt(null) }.getOrNull()
            ?: 0
    }

    private fun invokeNoArgMethodIfPresent(target: Any, methodName: String) {
        runCatching { target.javaClass.getMethod(methodName).invoke(target) }
    }

    private fun invokeBooleanMethodIfPresent(target: Any?, methodName: String, value: Boolean) {
        if (target == null) return
        runCatching {
            target.javaClass.getMethod(methodName, Boolean::class.javaPrimitiveType).invoke(target, value)
        }
    }

    private fun invokeMethodWithIntIfPresent(target: Any?, methodName: String, value: Int) {
        if (target == null) return
        runCatching {
            target.javaClass.getMethod(methodName, Int::class.javaPrimitiveType).invoke(target, value)
        }
    }

    private fun setBooleanFieldIfPresent(target: Any?, fieldName: String, value: Boolean) {
        if (target == null) return
        runCatching {
            target.javaClass.getMethod(
                "set${fieldName.replaceFirstChar { it.uppercase() }}",
                Boolean::class.javaPrimitiveType
            ).invoke(target, value)
        }.onFailure {
            runCatching {
                val f = target.javaClass.getDeclaredField(fieldName)
                f.isAccessible = true
                f.setBoolean(target, value)
            }
        }
    }

    private fun setFloatFieldIfPresent(target: Any?, fieldName: String, value: Float) {
        if (target == null) return
        runCatching {
            target.javaClass.getMethod(
                "set${fieldName.replaceFirstChar { it.uppercase() }}",
                Float::class.javaPrimitiveType
            ).invoke(target, value)
        }.onFailure {
            runCatching {
                val f = target.javaClass.getDeclaredField(fieldName)
                f.isAccessible = true
                f.setFloat(target, value)
            }
        }
    }
}
