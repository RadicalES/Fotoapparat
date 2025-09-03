package io.fotoapparat.routine.camera

import android.util.Log
import io.fotoapparat.concurrent.CameraExecutor
import io.fotoapparat.error.CameraErrorCallback
import io.fotoapparat.exception.camera.CameraException
import io.fotoapparat.hardware.Device
import io.fotoapparat.hardware.orientation.OrientationSensor
import io.fotoapparat.routine.focus.focusOnPoint
import io.fotoapparat.routine.orientation.startOrientationMonitoring
import io.fotoapparat.selector.firstAvailable
import io.fotoapparat.selector.highest
import io.fotoapparat.util.wrap

/**
 * Starts the camera from idle.
 */
internal fun Device.bootStart(
        orientationSensor: OrientationSensor,
        mainThreadErrorCallback: CameraErrorCallback
)  {
    if (hasSelectedCamera()) {
        Log.w("Device.bootStart", "bootStart: camera already started")
        throw IllegalStateException("Camera has already started!")
    }

    try {
        start(
                orientationSensor = orientationSensor
        )
        startOrientationMonitoring(
                orientationSensor = orientationSensor
        )
    } catch (e: CameraException) {
        mainThreadErrorCallback(e)
    }
}

/**
 * Starts the camera.
 */
internal fun Device.start(orientationSensor: OrientationSensor) {

    selectCamera()

    val cameraDeviceHW = getSelectedCamera().apply {
        open()

        updateCameraConfiguration(
                cameraHardware = this
        )

        setDisplayOrientation(orientationSensor.lastKnownOrientationState)
    }

    val streamResolutions = cameraDeviceHW.getStreamResolutions()

    cameraRenderer.apply {
        setScaleType(
                scaleType = scaleType
        )

        setPreviewResolution(
                resolution = getConfiguration().previewResolution(streamResolutions)!!
        )
    }

    focusPointSelector?.setFocalPointListener { focalRequest ->
        executor.execute(CameraExecutor.Operation(cancellable = true) {
            focusOnPoint(focalRequest)
        })
    }

    with(cameraDeviceHW) {

        setDisplaySurface(
            preview = cameraRenderer.getPreview()
        )

        startPreview()
    }

}