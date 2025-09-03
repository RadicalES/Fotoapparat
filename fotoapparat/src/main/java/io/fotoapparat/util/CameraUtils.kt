package io.fotoapparat.util

import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.media.Image
import android.util.Size
import android.view.TextureView
import io.fotoapparat.hardware.orientation.Orientation
import io.fotoapparat.hardware.orientation.toSurface
import io.fotoapparat.parameter.Resolution
import io.fotoapparat.parameter.ScaleType
import java.nio.ByteBuffer
import kotlin.math.max

object CameraUtils {

    fun imageToByteArray(image: Image): ByteArray? {
        image.let {
            val nv21Buffer = yuv420ThreePlanesToNV21(
                it.planes, image.width, image.height
            )

            return ByteArray(nv21Buffer.remaining()).apply {
                nv21Buffer.get(this)
            }
        }

        return null
    }


    fun yuv420ThreePlanesToNV21(
        yuv420888planes: Array<Image.Plane>,
        width: Int,
        height: Int
    ): ByteBuffer {
        val imageSize = width * height
        val out = ByteArray(imageSize + 2 * (imageSize / 4))
        if (areUVPlanesNV21(yuv420888planes, width, height)) {

            yuv420888planes[0].buffer[out, 0, imageSize]
            val uBuffer = yuv420888planes[1].buffer
            val vBuffer = yuv420888planes[2].buffer
            vBuffer[out, imageSize, 1]
            uBuffer[out, imageSize + 1, 2 * imageSize / 4 - 1]
        } else {
            unpackPlane(yuv420888planes[0], width, height, out, 0, 1)
            unpackPlane(yuv420888planes[1], width, height, out, imageSize + 1, 2)
            unpackPlane(yuv420888planes[2], width, height, out, imageSize, 2)
        }
        return ByteBuffer.wrap(out)
    }

    private fun areUVPlanesNV21(planes: Array<Image.Plane>, width: Int, height: Int): Boolean {
        val imageSize = width * height
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val vBufferPosition = vBuffer.position()
        val uBufferLimit = uBuffer.limit()

        vBuffer.position(vBufferPosition + 1)
        uBuffer.limit(uBufferLimit - 1)

        val areNV21 =
            vBuffer.remaining() == 2 * imageSize / 4 - 2 && vBuffer.compareTo(uBuffer) == 0

        vBuffer.position(vBufferPosition)
        uBuffer.limit(uBufferLimit)
        return areNV21
    }

    private fun unpackPlane(
        plane: Image.Plane,
        width: Int,
        height: Int,
        out: ByteArray,
        offset: Int,
        pixelStride: Int
    ) {
        val buffer = plane.buffer
        buffer.rewind()
        val numRow = (buffer.limit() + plane.rowStride - 1) / plane.rowStride
        if (numRow == 0) {
            return
        }
        val scaleFactor = height / numRow
        val numCol = width / scaleFactor

        var outputPos = offset
        var rowStart = 0
        for (row in 0 until numRow) {
            var inputPos = rowStart
            for (col in 0 until numCol) {
                out[outputPos] = buffer[inputPos]
                outputPos += pixelStride
                inputPos += plane.pixelStride
            }
            rowStart += plane.rowStride
        }
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

fun Resolution.projectCenterInside(viewWidth: Int, viewHeight: Int): Rect {
    val scale = Math.min(
        viewWidth / width.toFloat(),
        viewHeight / height.toFloat()
    )

    val w = (width * scale).toInt()
    val h = (height * scale).toInt()
    val extraX = Math.max(0, viewWidth - w)
    val extraY = Math.max(0, viewHeight - h)

    return  Rect(
        extraX / 2,
        extraY / 2,
        w + extraX / 2,
        h + extraY / 2
    )
}

fun Resolution.projectCenterCrop(viewWidth: Int, viewHeight: Int): Rect {
    val scale = Math.max(
        viewWidth / width.toFloat(),
        viewHeight / height.toFloat()
    )

    val w = (width * scale).toInt()
    val h = (height * scale).toInt()
    val extraX = Math.max(0, w - viewWidth)
    val extraY = Math.max(0, h - viewHeight)

    return Rect(
        -extraX / 2,
        -extraY / 2,
        w + extraX / 2,
        h + extraY / 2
    )
}

fun Resolution.projectTopCrop(viewWidth: Int, viewHeight: Int): Rect {
    val scale = Math.max(
        viewWidth / width.toFloat(),
        viewHeight / height.toFloat()
    )

    val w = (width * scale).toInt()
    val h = (height * scale).toInt()
    val extraX = Math.max(0, w - viewWidth)

    return Rect(
        -extraX / 2,
        0,
        w + extraX / 2,
        h
    )
}

fun projectImage(scaleType: ScaleType, viewWidth: Int, viewHeight: Int, imageWidth: Int, imageHeight: Int): Rect {
    val res = Resolution(imageWidth, imageHeight)
    return when (scaleType) {
        ScaleType.CenterInside -> res.projectCenterInside(viewWidth, viewHeight)
        ScaleType.CenterCrop -> res.projectCenterCrop(viewWidth, viewHeight)
        ScaleType.TopCrop -> res.projectTopCrop(viewWidth, viewHeight)
    }
}

