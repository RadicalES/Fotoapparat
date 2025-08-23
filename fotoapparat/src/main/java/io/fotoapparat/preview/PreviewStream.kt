package io.fotoapparat.preview

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.hardware.camera2.CameraDevice
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import io.fotoapparat.hardware.CameraHardware
import io.fotoapparat.hardware.frameProcessingExecutor
import io.fotoapparat.hardware.orientation.Orientation
import io.fotoapparat.parameter.Resolution
import io.fotoapparat.util.FrameProcessor
import io.fotoapparat.util.ImageUtils
import io.fotoapparat.view.Preview
import java.util.*

/**
 * Preview stream of Camera.
 */
internal class PreviewStream() {

    private val frameProcessors = LinkedHashSet<FrameProcessor>()

    private var previewResolution: Resolution? = null

    private var imageReader: ImageReader? = null

    private var isProcessingFrame = false

    private val yuvBytes = arrayOfNulls<ByteArray>(3)
    private var yRowStride = 0
    private var rgbBytes: IntArray? = null

    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }

    private var postInferenceCallback: Runnable? = null

    /** [Handler] corresponding to [imageReaderThread] */
    private val imageReaderHandler = Handler(imageReaderThread.looper)

    /**
     * CW orientation.
     */
    var frameOrientation: Orientation = Orientation.Vertical.Portrait

    companion object {
        private val TAG = PreviewStream::class.java.simpleName
    }

    /**
     * Clears all processors.
     */
    private fun clearProcessors() {
        synchronized(frameProcessors) {
            frameProcessors.clear()
        }
    }

    /**
     * Registers new processor. If processor was already added before, does nothing.
     */
    private fun addProcessor(processor: FrameProcessor) {
        synchronized(frameProcessors) {
            frameProcessors.add(processor)
        }
    }

    /**
     * Starts preview stream. After preview is started frame processors will start receiving frames.
     */
    private fun start() {
//        camera.addFrameToBuffer()

        //camera.setPreviewCallbackWithBuffer { data, _ -> dispatchFrameOnBackgroundThread(data) }
    }

    private val onImageAvailableCallback = ImageReader.OnImageAvailableListener { reader ->
        val image: Image? = reader?.acquireLatestImage()

        image?.use {

            if(!isProcessingFrame) {
                isProcessingFrame = true
    //            Log.d(TAG, "onImageAvailable: ")
//                val buffer = it.planes[0].buffer
//                val bytes = ByteArray(buffer.remaining())
//                buffer.get(bytes)


                val planes = it.planes
                fillBytes(planes, yuvBytes)
                yRowStride = planes[0].rowStride
                val uvRowStride = planes[1].rowStride
                val uvPixelStride = planes[1].pixelStride
                ImageUtils.convertYUV420ToARGB8888(
                    yuvBytes[0]!!,
                    yuvBytes[1]!!,
                    yuvBytes[2]!!,
                    previewResolution!!.width,
                    previewResolution!!.height,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    rgbBytes!!
                )

                postInferenceCallback = Runnable {
                    image.close()
                    isProcessingFrame = false
                }

                dispatchFrameOnBackgroundThread(rgbBytes)
            } else {
                it.close()
            }
        }
    }

    /**
     * Stops preview stream.
     */
    fun stop() {
        imageReader?.close()
        imageReaderThread.quitSafely()
    }

    @SuppressLint("Range")
    fun setPreviewResolution(resolution: Resolution) {
        previewResolution = resolution

        if(imageReader != null) {
            imageReader!!.close()
        }

        imageReader = ImageReader.newInstance( resolution.width, resolution.height,
            ImageFormat.YUV_420_888, 1)
        imageReader?.setOnImageAvailableListener(onImageAvailableCallback, imageReaderHandler)

    }

    fun getPreviewSurface() : Surface {
        return imageReader!!.surface
    }

    /**
     * Updates the frame processor safely.
     */
    fun updateProcessorSafely(frameProcessor: FrameProcessor?) {
        clearProcessors()
        if (frameProcessor == null) {
            stop()
        } else {
            addProcessor(frameProcessor)
            start()
        }
    }

    protected fun fillBytes(
        planes: Array<Image.Plane>,
        yuvBytes: Array<ByteArray?>
    ) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer[yuvBytes[i]]
        }
    }

//    private fun Camera.addFrameToBuffer() {
//        addCallbackBuffer(parameters.allocateBuffer())
//    }
//
//    private fun Camera.Parameters.allocateBuffer(): ByteArray {
//        ensureNv21Format()
//
//        previewResolution = Resolution(
//                previewSize.width,
//                previewSize.height
//        )
//
//        return ByteArray(previewSize.bytesPerFrame())
//    }

    private fun dispatchFrameOnBackgroundThread(data: ByteArray) {
        frameProcessingExecutor.execute {
            synchronized(frameProcessors) {
                dispatchFrame(data)
            }
            postInferenceCallback!!.run()
        }
    }

    private fun dispatchFrame(image: ByteArray) {
        val previewResolution = ensurePreviewSizeAvailable()

        val frame = Frame(
                size = previewResolution,
                image = image,
                rotation = frameOrientation.degrees
        )

        frameProcessors.forEach {
            it.invoke(frame)
        }

    }

    private fun ensurePreviewSizeAvailable(): Resolution =
            previewResolution
                    ?: throw IllegalStateException("previewSize is null. Frame was not added?")


}

//private fun Camera.Size.bytesPerFrame(): Int =
//        width * height * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8
//
//private fun Camera.Parameters.ensureNv21Format() {
//    if (previewFormat != ImageFormat.NV21) {
//        throw UnsupportedOperationException("Only NV21 preview format is supported")
//    }
//}
