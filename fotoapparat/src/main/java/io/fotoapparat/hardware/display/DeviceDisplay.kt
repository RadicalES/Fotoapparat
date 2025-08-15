package io.fotoapparat.hardware.display

import android.content.Context
import android.content.Context.DISPLAY_SERVICE
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import io.fotoapparat.hardware.orientation.Orientation
import io.fotoapparat.hardware.orientation.Orientation.Horizontal.Landscape
import io.fotoapparat.hardware.orientation.Orientation.Horizontal.ReverseLandscape
import io.fotoapparat.hardware.orientation.Orientation.Vertical.Portrait
import io.fotoapparat.hardware.orientation.Orientation.Vertical.ReversePortrait

/**
 * A phone's display.
 */
internal open class DeviceDisplay(context: Context) {

    private val displayManager: DisplayManager by lazy {
        context.getSystemService(DISPLAY_SERVICE) as DisplayManager
    }

    private val display:Display by lazy{
        displayManager.getDisplay(Display.DEFAULT_DISPLAY)
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(dispId: Int) {}
        override fun onDisplayRemoved(dispId: Int) {}

        override fun onDisplayChanged(dispId: Int) {
        }

    }

    /**
     * Returns the orientation of the screen.
     */
    open fun getOrientation(): Orientation = when (display.rotation) {
        Surface.ROTATION_0 -> Portrait
        Surface.ROTATION_90 -> Landscape
        Surface.ROTATION_180 -> ReversePortrait
        Surface.ROTATION_270 -> ReverseLandscape
        else -> Portrait
    }

    open fun getRotation(): Int = display.rotation

}

