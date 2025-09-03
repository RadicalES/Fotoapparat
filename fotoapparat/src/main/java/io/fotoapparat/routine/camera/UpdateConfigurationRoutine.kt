package io.fotoapparat.routine.camera

import io.fotoapparat.configuration.Configuration
import io.fotoapparat.hardware.CameraHardware
import io.fotoapparat.hardware.Device
import kotlinx.coroutines.runBlocking

/**
 * Updates [Device] configuration.
 */
internal fun Device.updateDeviceConfiguration(newConfiguration: Configuration) {
    val cameraDevice = getSelectedCamera()

    updateConfiguration(newConfiguration)

    updateCameraConfiguration(cameraHardware = cameraDevice)
}

/**
 * Updates [CameraHardware] parameters.
 */
internal fun Device.updateCameraConfiguration(
    cameraHardware: CameraHardware
) = runBlocking {
    val cameraParameters = getCameraParameters(cameraHardware)
    val frameProcessor = getFrameProcessor()

    cameraHardware.updateParameters(
            parameters = cameraParameters
    )

    cameraHardware.updateFrameProcessor(
            frameProcessor = frameProcessor
    )
}
