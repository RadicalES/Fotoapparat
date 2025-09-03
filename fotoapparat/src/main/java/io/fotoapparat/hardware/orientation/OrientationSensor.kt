package io.fotoapparat.hardware.orientation

import android.content.Context
import android.util.Log
import io.fotoapparat.hardware.Device
import io.fotoapparat.hardware.orientation.Orientation.Vertical.Portrait

/**
 * Monitors orientation of the device.
 */

/**
 * NOTES about rotation
 *
 * In the context of Android's Camera2 API, device rotation and display rotation refer to distinct
 * but related concepts concerning the orientation of the device and its impact on camera output.
 *
 * Device Rotation:
 * This refers to the physical rotation of the device from its "natural" orientation, which is typically
 * portrait for phones and landscape for tablets.
 * It's measured in degrees (0, 90, 180, 270) and indicates how much the device has been rotated from
 * its default upright position.
 * For camera applications, device rotation is crucial for correctly orienting the camera preview and
 * captured images or videos, ensuring they appear upright to the user regardless of how the device is held.
 * It directly influences the JPEG_ORIENTATION setting in Camera2, which dictates how the final JPEG
 * image should be rotated to compensate for the device's orientation at the time of capture.
 *
 * Display Rotation:
 * This refers to the rotation of the display surface itself, as reported by Display.getRotation().
 * It indicates the counter-clockwise rotation of the display from its natural orientation.
 * While related to device rotation, display rotation primarily concerns how the UI and content are rendered on the screen.
 * It's important for ensuring that the camera preview, which is drawn on a SurfaceTexture or SurfaceView,
 * is correctly oriented to match the display's current orientation.
 *
 * Key Differences and Interplay:
 * Scope:
 * Device rotation is about the physical device's orientation, while display rotation is about the rendering surface's orientation.
 *
 * Impact on Camera:
 * Device rotation directly affects the orientation of captured images and videos, necessitating adjustments
 * like JPEG_ORIENTATION. Display rotation primarily affects the visual presentation of the camera preview on the screen.
 *
 * Synchronization:
 * While a change in device rotation often leads to a change in display rotation, they are not always
 * identical. For example, if an app forces a specific screen orientation, the display rotation might not
 * reflect the actual physical device rotation.
 *
 * Calculation for Preview:
 * Correctly orienting the camera preview involves considering both the device's physical orientation
 * (from sensors) and the display's current rotation to ensure the preview appears upright and matches
 * the user's perception of "up." This often involves calculating the setTransform() for the SurfaceTexture or
 * applying transformations to the SurfaceView.
 *
 * */
internal open class OrientationSensor(
        private val rotationListener: RotationListener,
        private val device: Device
) {

    companion object {
        val TAG = "OrientationSensor"
    }

    private lateinit var listener: (OrientationState) -> Unit

    /*
    * onOrientationChanged is the clockwise rotation of the device
    * */
    private val onOrientationChanged: (DeviceRotationDegrees) -> Unit = { deviceRotation ->
        deviceRotation.toClosestRightAngle()
                .toOrientation()
                .let { deviceOrientation ->

                    // display orientation
                    val displayOrientation = device.getDisplayOrientation()

                    val newState = OrientationState(
                        deviceOrientation = deviceOrientation,
                        displayOrientation = displayOrientation
                    )

                    if (newState != lastKnownOrientationState) {
                        lastKnownOrientationState = newState
                        listener(newState)
                        Log.d(TAG, "orientation changed: " +
                                "rotation = $deviceRotation, " +
                                "device = $deviceOrientation, " +
                                "display = $displayOrientation")
                    }
                }
    }

    open var lastKnownOrientationState: OrientationState = OrientationState(
        deviceOrientation = Orientation.Horizontal.Landscape,
        displayOrientation = device.getDisplayOrientation()
    )

    constructor(
            context: Context,
            device: Device
    ) : this(
            RotationListener(context),
            device
    )

    init {
        rotationListener.orientationChanged = onOrientationChanged
    }

    /**
     * Starts monitoring device's orientation state.
     */
    open fun start(listener: (OrientationState) -> Unit) {
        this.listener = listener
        rotationListener.enable()
    }

    /**
     * Stops monitoring device's orientation.
     */
    open fun stop() {
        rotationListener.disable()
    }

}
