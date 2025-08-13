@file:Suppress("DEPRECATION")

package io.fotoapparat.parameter

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata.*
import android.util.Range
import android.util.Size


typealias RangeArray = Array<Range<Int>>
typealias RangeInt = Range<Int>

/**
 * Provides the supported [Camera.Parameters] with defaults where needed.
 */
internal class SupportedParameters(
        private val cameraCharacteristics: CameraCharacteristics
) {

    /**
     * @see Camera.Parameters.getSupportedFlashModes
     */
    val flashModes: List<String> by lazy {
        val res = cameraCharacteristics
            .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)!!
            .toFlashModes()
        res
    }

    /**
     * @see Camera.Parameters.getSupportedFocusModes
     */
    val focusModes: List<String> by lazy {
        val res = cameraCharacteristics
            .get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)!!
            .toAutoFocusModes()
        res
    }

    /**
     * @see Camera.Parameters.getSupportedPreviewSizes
     */
    val previewResolutions: List<Size> by lazy {
//        cameraParameters.supportedPreviewSizes
        listOf(Size(1024,1024), Size(1280, 720))
    }

    /**
     * @see Camera.Parameters.getSupportedPictureSizes
     */
    val pictureResolutions: List<Size> by lazy {
        listOf(Size(1024,1024), Size(1280, 720))
    }

    /**
     * @see Camera.Parameters.getSupportedPreviewFpsRange
     */
    val supportedPreviewFpsRanges: List<IntArray> by lazy {

//        cameraParameters.supportedPreviewFpsRange
        cameraCharacteristics
            .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)!!
            .toFpsList()
    }

    /**
     * @return The list of supported sensitivities (ISO) by the camera.
     */
    val sensorSensitivities: List<Int> by lazy {
//        cameraParameters.extractRawCameraValues(supportedSensitivitiesKeys).toInts()
        listOf(1)
    }

    /**
     * @return [io.fotoapparat.parameter.Zoom.FixedZoom] if [Camera.Parameters.isZoomSupported] returns false,
     * and [io.fotoapparat.parameter.Zoom.VariableZoom] with max zoom level otherwise.
     */
    val supportedZoom by lazy {
//        if (cameraParameters.isZoomSupported) Zoom.VariableZoom(cameraParameters.maxZoom, cameraParameters.zoomRatios) else Zoom.FixedZoom
        Zoom.FixedZoom
    }

    /**
     * @see Camera.Parameters.isSmoothZoomSupported
     */
    val supportedSmoothZoom by lazy {
//        cameraParameters.isSmoothZoomSupported
        false
    }

    /**
     * @see Camera.Parameters.getSupportedAntibanding
     */
    val supportedAutoBandingModes by lazy {
//        cameraParameters.supportedAntibanding ?: listOf(Camera.Parameters.ANTIBANDING_OFF)
        cameraCharacteristics
            .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES)!!
            .toAntiBandingModes()
    }

    /**
     * @return A [IntRange] of supported jpeg qualities that the camera can take photos.
     */
    val jpegQualityRange by lazy {
        IntRange(0, 100)
    }

    /**
     * @return A [IntRange] of exposure compensation values supported by the camera.
     */
    val exposureCompensationRange by lazy {
//        IntRange(cameraParameters.minExposureCompensation, cameraParameters.maxExposureCompensation)
        cameraCharacteristics
            .get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)!!
            .toConpensationRangeList()
    }

    /**
     * @see Camera.Parameters.getMaxNumFocusAreas
     */
    val maxNumFocusAreas by lazy {
//        cameraParameters.maxNumFocusAreas
        val res = cameraCharacteristics
            .get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)!!
        res
    }

    /**
     * @see Camera.Parameters.getMaxNumMeteringAreas
     */
    val maxNumMeteringAreas by lazy {
        val res = cameraCharacteristics
            .get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)!!
        res
    }
}

private val supportedSensitivitiesKeys = listOf("iso-values", "iso-mode-values", "iso-speed-values", "nv-picture-iso-values")

private fun IntArray.toFlashModes() : List<String> {
    return this.map { m ->
        when(m) {
            CONTROL_AE_MODE_OFF -> "OFF"
            CONTROL_AE_MODE_ON -> "ON"
            CONTROL_AE_MODE_ON_AUTO_FLASH -> "AUTO"
            CONTROL_AE_MODE_ON_EXTERNAL_FLASH -> "EXTERNAL"
            CONTROL_AE_MODE_ON_ALWAYS_FLASH -> "ALWAYS"
            CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE -> "AUTO REDEYE"
            CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY -> "LOW LIGHT BOOST BRIGHTNESS"
            else -> "UNKNOWN"
        }
    }.toList()
}

private fun IntArray.toAutoFocusModes() : List<String> {
    return this.map { m ->
        when(m) {
            CONTROL_AF_MODE_OFF -> "OFF"
            CONTROL_AF_MODE_MACRO -> "MACRO"
            CONTROL_AF_MODE_EDOF -> "EDOF"
            CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "CONTINUOUS"
            CONTROL_AF_MODE_AUTO -> "AUTO"
            else -> "UNKNOWN"
        }
    }.toList()
}

private fun IntArray.toAntiBandingModes(): List<Int> {
    return this.toList()
}

//Array<Range<Int>>
private fun RangeArray.toFpsList(): List<IntArray> {
    return this.map { range ->
        intArrayOf(range.lower, range.upper)
    }.toList()
}

private fun RangeInt.toConpensationRangeList(): IntRange {
    return IntRange(this.lower, this.upper)
}