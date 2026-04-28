package tech.done.ads.player

object ExternalPlayerViewControllerPolicy {
    @JvmStatic
    fun apply(playerView: Any?, inAd: Boolean) {
        val view = playerView ?: return
        setBooleanFieldIfPresent(view, "useController", !inAd)
        invokeBooleanMethodIfPresent(view, "setUseController", !inAd)
        invokeBooleanMethodIfPresent(view, "setControllerAutoShow", !inAd)
        invokeBooleanMethodIfPresent(view, "setControllerHideOnTouch", !inAd)
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
