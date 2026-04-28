package tech.done.ads.player.media3

import android.view.View
import androidx.media3.ui.PlayerView

object ExternalPlayerViewControllerPolicy {
    @JvmStatic
    fun apply(playerView: PlayerView?, inAd: Boolean) {
        val pv = playerView ?: return
        if (inAd) {
            pv.useController = false
            pv.setControllerAutoShow(false)
            pv.setControllerHideOnTouch(false)
            pv.hideController()
            pv.findViewById<View>(androidx.media3.ui.R.id.exo_controller)?.apply {
                alpha = 0f
                isEnabled = false
                isClickable = false
                isFocusable = false
            }
        } else {
            pv.findViewById<View>(androidx.media3.ui.R.id.exo_controller)?.apply {
                alpha = 1f
                isEnabled = true
            }
            pv.useController = true
            pv.setControllerAutoShow(true)
            pv.setControllerHideOnTouch(true)
        }
    }
}

