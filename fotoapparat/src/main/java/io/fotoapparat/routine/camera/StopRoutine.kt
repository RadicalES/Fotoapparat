package io.fotoapparat.routine.camera

import io.fotoapparat.hardware.CameraHardware
import io.fotoapparat.hardware.Device
import io.fotoapparat.hardware.orientation.OrientationSensor
import io.fotoapparat.routine.orientation.stopMonitoring

/**
 * Stops the camera completely.
 */
internal fun Device.shutDown(
        orientationSensor: OrientationSensor
) {
    focusPointSelector?.setFocalPointListener { }
    orientationSensor.stopMonitoring()

    val cameraDevice = getSelectedCamera()

    stop(cameraDevice)
}

/**
 * Stops the camera.
 */
internal fun Device.stop(cameraHardware: CameraHardware) {
    cameraHardware.stopPreview()

    cameraHardware.close()

    clearSelectedCamera()
}
