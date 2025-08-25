package io.fotoapparat.util

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.media.Image
import android.util.Size
import android.view.TextureView
import io.fotoapparat.hardware.orientation.Orientation
import io.fotoapparat.hardware.orientation.toSurface
import java.nio.ByteBuffer
import kotlin.math.max

object CameraUtils {

    fun imageToByteBuffer(image: Image): ByteBuffer {
        val planes = image.planes
        val yBuffer = planes[0].buffer // Y plane
        val uBuffer = planes[1].buffer // U plane
        val vBuffer = planes[2].buffer // V plane

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize) // Note: V plane often comes before U in NV21
        uBuffer.get(nv21, ySize + vSize, uSize)

        return ByteBuffer.wrap(nv21)
    }

    /** Return the biggest preview size available which is smaller than the window */
    private fun findBestPreviewSize(windowSize: Size, characteristics: CameraCharacteristics):
            Size {
        val supportedPreviewSizes: List<Size> =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java)
                ?.filter { SizeComparator.compare(it, windowSize) >= 0 }
                ?.sortedWith(SizeComparator)
                ?: emptyList()

        return supportedPreviewSizes.getOrElse(0) { Size(0, 0) }
    }

    fun buildTargetTextureFromOrientation(
        containerView: TextureView,
        characteristics: CameraCharacteristics,
        cameraOrientation: Orientation,
        deviceOrientation: Orientation
    ): SurfaceTexture? {

        val surfaceRotationDegrees = deviceOrientation.toSurface() * 90
        val windowSize = Size(containerView.width, containerView.height)
        val previewSize = findBestPreviewSize(windowSize, characteristics)
        val sensorOrientation = cameraOrientation.toSurface()
        val isRotationRequired = cameraOrientation.toSurface() != deviceOrientation.toSurface()


        /* Scale factor required to scale the preview to its original size on the x-axis */
        var scaleX = 1f
        /* Scale factor required to scale the preview to its original size on the y-axis */
        var scaleY = 1f

        if (sensorOrientation == 0) {
            scaleX =
                if (!isRotationRequired) {
                    windowSize.width.toFloat() / previewSize.height
                } else {
                    windowSize.width.toFloat() / previewSize.width
                }

            scaleY =
                if (!isRotationRequired) {
                    windowSize.height.toFloat() / previewSize.width
                } else {
                    windowSize.height.toFloat() / previewSize.height
                }
        } else {
            scaleX =
                if (isRotationRequired) {
                    windowSize.width.toFloat() / previewSize.height
                } else {
                    windowSize.width.toFloat() / previewSize.width
                }

            scaleY =
                if (isRotationRequired) {
                    windowSize.height.toFloat() / previewSize.width
                } else {
                    windowSize.height.toFloat() / previewSize.height
                }
        }

        /* Scale factor required to fit the preview to the TextureView size */
        val finalScale = max(scaleX, scaleY)
        val halfWidth = windowSize.width / 2f
        val halfHeight = windowSize.height / 2f

        val matrix = Matrix()

        if (isRotationRequired) {
            matrix.setScale(
                1 / scaleX * finalScale,
                1 / scaleY * finalScale,
                halfWidth,
                halfHeight
            )
        } else {
            matrix.setScale(
                windowSize.height / windowSize.width.toFloat() / scaleY * finalScale,
                windowSize.width / windowSize.height.toFloat() / scaleX * finalScale,
                halfWidth,
                halfHeight
            )
        }

        // Rotate to compensate display rotation
        matrix.postRotate(
            -surfaceRotationDegrees.toFloat(),
            halfWidth,
            halfHeight
        )

        containerView.setTransform(matrix)

        return containerView.surfaceTexture?.apply {
            setDefaultBufferSize(previewSize.width, previewSize.height)
        }
    }

    /**
     * Returns a new SurfaceTexture with optimized transformation
     */
    fun buildTargetTexture(
        containerView: TextureView,
        characteristics: CameraCharacteristics,
        surfaceRotation: Int
    ): SurfaceTexture? {

        val surfaceRotationDegrees = surfaceRotation * 90
        val windowSize = Size(containerView.width, containerView.height)
        val previewSize = findBestPreviewSize(windowSize, characteristics)
        val sensorOrientation =
            characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val isRotationRequired =
            computeRelativeRotation(characteristics, surfaceRotationDegrees) % 180 != 0

        /* Scale factor required to scale the preview to its original size on the x-axis */
        var scaleX = 1f
        /* Scale factor required to scale the preview to its original size on the y-axis */
        var scaleY = 1f

        if (sensorOrientation == 0) {
            scaleX =
                if (!isRotationRequired) {
                    windowSize.width.toFloat() / previewSize.height
                } else {
                    windowSize.width.toFloat() / previewSize.width
                }

            scaleY =
                if (!isRotationRequired) {
                    windowSize.height.toFloat() / previewSize.width
                } else {
                    windowSize.height.toFloat() / previewSize.height
                }
        } else {
            scaleX =
                if (isRotationRequired) {
                    windowSize.width.toFloat() / previewSize.height
                } else {
                    windowSize.width.toFloat() / previewSize.width
                }

            scaleY =
                if (isRotationRequired) {
                    windowSize.height.toFloat() / previewSize.width
                } else {
                    windowSize.height.toFloat() / previewSize.height
                }
        }

        /* Scale factor required to fit the preview to the TextureView size */
        val finalScale = max(scaleX, scaleY)
        val halfWidth = windowSize.width / 2f
        val halfHeight = windowSize.height / 2f

        val matrix = Matrix()

        if (isRotationRequired) {
            matrix.setScale(
                1 / scaleX * finalScale,
                1 / scaleY * finalScale,
                halfWidth,
                halfHeight
            )
        } else {
            matrix.setScale(
                windowSize.height / windowSize.width.toFloat() / scaleY * finalScale,
                windowSize.width / windowSize.height.toFloat() / scaleX * finalScale,
                halfWidth,
                halfHeight
            )
        }

        // Rotate to compensate display rotation
        matrix.postRotate(
            -surfaceRotationDegrees.toFloat(),
            halfWidth,
            halfHeight
        )

        containerView.setTransform(matrix)

        return containerView.surfaceTexture?.apply {
            setDefaultBufferSize(previewSize.width, previewSize.height)
        }
    }

    private fun computeRelativeRotation(
        characteristics: CameraCharacteristics,
        deviceOrientationDegrees: Int
    ): Int {
        val sensorOrientationDegrees = 0
            characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

        // Reverse device orientation for front-facing cameras
        val sign = if (characteristics.get(CameraCharacteristics.LENS_FACING) ==
            CameraCharacteristics.LENS_FACING_FRONT
        ) 1 else -1

        return (sensorOrientationDegrees - (deviceOrientationDegrees * sign) + 360) % 360
    }

}

internal object SizeComparator : Comparator<Size> {
    override fun compare(a: Size, b: Size): Int {
        return b.height * b.width - a.width * a.height
    }
}