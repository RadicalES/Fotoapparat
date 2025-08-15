@file:Suppress("DEPRECATION")

package io.fotoapparat.hardware

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import io.fotoapparat.characteristic.getCharacteristics
import io.fotoapparat.concurrent.CameraExecutor
import io.fotoapparat.configuration.CameraConfiguration
import io.fotoapparat.configuration.Configuration
import io.fotoapparat.exception.camera.UnsupportedLensException
import io.fotoapparat.hardware.display.DeviceDisplay
import io.fotoapparat.hardware.orientation.Orientation
import io.fotoapparat.log.Logger
import io.fotoapparat.parameter.ScaleType
import io.fotoapparat.parameter.camera.CameraParameters
import io.fotoapparat.parameter.camera.provide.getCameraParameters
import io.fotoapparat.selector.LensPositionSelector
import io.fotoapparat.util.FrameProcessor
import io.fotoapparat.view.CameraRenderer
import io.fotoapparat.view.FocalPointSelector
import kotlinx.coroutines.CompletableDeferred

/**
 * Phone.
 */
internal open class Device(
    private val context: Context,
    internal open val logger: Logger,
    private val deviceDisplay: DeviceDisplay,
    internal open val scaleType: ScaleType,
    internal open val cameraRenderer: CameraRenderer,
    internal val focusPointSelector: FocalPointSelector?,
    internal val executor: CameraExecutor,
    initialConfiguration: CameraConfiguration, initialLensPositionSelector: LensPositionSelector
) {

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val cameraList: List<CameraItem> by lazy {
        getCameraList(cameraManager)
    }

    private val cameras = cameraList.map { camItem ->
        CameraHardware(
            cameraManager,
            logger = logger,
            display = deviceDisplay,
            characteristics = getCharacteristics(cameraManager, camItem.cameraId)
        )
    }

    private var lensPositionSelector: LensPositionSelector = initialLensPositionSelector
    private var selectedCameraHardware = CompletableDeferred<CameraHardware>()
    private var savedConfiguration = CameraConfiguration.default()

    init {
        updateLensPositionSelector(initialLensPositionSelector)
        savedConfiguration = initialConfiguration
    }

    /**
     * Selects a camera.
     */
    open fun canSelectCamera(lensPositionSelector: LensPositionSelector): Boolean {
        val selectedCameraDevice = selectCamera(
                availableCameras = cameras,
                lensPositionSelector = lensPositionSelector
        )
        return selectedCameraDevice != null
    }

    /**
     * Selects a camera. Will do nothing if camera cannot be selected.
     */
    open fun selectCamera() {
        logger.recordMethod()

        selectCamera(
                availableCameras = cameras,
                lensPositionSelector = lensPositionSelector
        )
                ?.let(selectedCameraHardware::complete)
                ?: selectedCameraHardware.completeExceptionally(UnsupportedLensException())
    }

    /**
     * Clears the selected camera.
     */
    open fun clearSelectedCamera() {
        selectedCameraHardware = CompletableDeferred()
    }

    /**
     * Waits and returns the selected camera.
     */
    open suspend fun awaitSelectedCamera(): CameraHardware = selectedCameraHardware.await()

    /**
     * Returns the selected camera.
     *
     * @throws IllegalStateException If no camera has been yet selected.
     * @throws UnsupportedLensException If no camera could get selected.
     */
    open fun getSelectedCamera(): CameraHardware = try {
        selectedCameraHardware.getCompleted()
    } catch (e: IllegalStateException) {
        throw IllegalStateException("Camera has not started!")
    }

    /**
     * @return `true` if a camera has been selected.
     */
    open fun hasSelectedCamera() = selectedCameraHardware.isCompleted

    /**
     * @return Orientation of the screen.
     */
    open fun getScreenOrientation(): Orientation {
        return deviceDisplay.getOrientation()
    }

    open fun getScreenRotation(): Int {
        return deviceDisplay.getRotation()
    }

    /**
     * Updates the desired from the user camera lens position.
     */
    open fun updateLensPositionSelector(newLensPosition: LensPositionSelector) {
        logger.recordMethod()

        lensPositionSelector = newLensPosition
    }

    /**
     * Updates the desired from the user selectors.
     */
    open fun updateConfiguration(newConfiguration: Configuration) {
        logger.recordMethod()

        savedConfiguration = updateConfiguration(
                savedConfiguration = savedConfiguration,
                newConfiguration = newConfiguration
        )
    }

    /**
     * @return The desired from the user selectors.
     */
    open fun getConfiguration(): CameraConfiguration = savedConfiguration

    /**
     * @return The selected [CameraParameters] for the given [CameraHardware].
     */
    open suspend fun getCameraParameters(cameraDevice: CameraHardware): CameraParameters =
            getCameraParameters(
                    cameraConfiguration = savedConfiguration,
                    capabilities = cameraDevice.getCapabilities()
            )

    /**
     * @return The frame processor.
     */
    open fun getFrameProcessor(): FrameProcessor? = savedConfiguration.frameProcessor

    /**
     * @return The desired from the user camera lens position.
     */
    open fun getLensPositionSelector(): LensPositionSelector = lensPositionSelector

}

internal data class CameraItem(val title: String, val cameraId: String, val format: Int)

/**
 * Updates the device's configuration.
 */
internal fun updateConfiguration(
        savedConfiguration: CameraConfiguration,
        newConfiguration: Configuration
) = CameraConfiguration(
        flashMode = newConfiguration.flashMode ?: savedConfiguration.flashMode,
        focusMode = newConfiguration.focusMode ?: savedConfiguration.focusMode,
        exposureCompensation = newConfiguration.exposureCompensation
                ?: savedConfiguration.exposureCompensation,
        frameProcessor = newConfiguration.frameProcessor ?: savedConfiguration.frameProcessor,
        previewFpsRange = newConfiguration.previewFpsRange ?: savedConfiguration.previewFpsRange,
        sensorSensitivity = newConfiguration.sensorSensitivity
                ?: savedConfiguration.sensorSensitivity,
        pictureResolution = newConfiguration.pictureResolution
                ?: savedConfiguration.pictureResolution,
        previewResolution = newConfiguration.previewResolution
                ?: savedConfiguration.previewResolution
)

/**
 * Selects a camera from the set of available ones.
 */
internal fun selectCamera(
    availableCameras: List<CameraHardware>,
    lensPositionSelector: LensPositionSelector
): CameraHardware? {

    val lensPositions = availableCameras.map { it.characteristics.lensPosition }.toSet()
    val desiredPosition = lensPositionSelector(lensPositions)

    return availableCameras.find { it.characteristics.lensPosition == desiredPosition }
}

internal fun getCameraList(cameraManager: CameraManager): List<CameraItem> {
    val availableCameras: MutableList<CameraItem> = mutableListOf()

    // Get list of all compatible cameras
    val cameraIds = cameraManager.cameraIdList.filter {
        val characteristics = cameraManager.getCameraCharacteristics(it)
        val capabilities = characteristics.get(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        capabilities?.contains(
            CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) ?: false
    }

    // Iterate over the list of cameras and return all the compatible ones
    cameraIds.forEach { id ->
        val characteristics = cameraManager.getCameraCharacteristics(id)
        val orientation = lensOrientationString(
            characteristics.get(CameraCharacteristics.LENS_FACING)!!)

        // Query the available capabilities and output formats
        val capabilities = characteristics.get(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)!!
        val outputFormats = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.outputFormats

        // All cameras *must* support JPEG output so we don't need to check characteristics
        availableCameras.add(CameraItem(
            "$orientation JPEG ($id)", id, ImageFormat.JPEG))

        // Return cameras that support RAW capability
        if (capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) &&
            outputFormats.contains(ImageFormat.RAW_SENSOR)) {
            availableCameras.add(CameraItem(
                "$orientation RAW ($id)", id, ImageFormat.RAW_SENSOR))
        }

        // Return cameras that support JPEG DEPTH capability
        if (capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) &&
            outputFormats.contains(ImageFormat.DEPTH_JPEG)) {
            availableCameras.add(CameraItem(
                "$orientation DEPTH ($id)", id, ImageFormat.DEPTH_JPEG))
        }
    }

    return availableCameras
}

internal fun lensOrientationString(value: Int) = when(value) {
    CameraCharacteristics.LENS_FACING_BACK -> "Back"
    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
    else -> "Unknown"
}