package io.fotoapparat.preview

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import io.fotoapparat.hardware.frameProcessingExecutor
import io.fotoapparat.hardware.orientation.Orientation
import io.fotoapparat.parameter.Resolution
import io.fotoapparat.util.CameraUtils
import io.fotoapparat.util.FrameProcessor
import java.util.*

/**
 * Preview stream of Camera.
 */
internal class PreviewStream() {

    private val frameProcessors = LinkedHashSet<FrameProcessor>()

    private var previewResolution: Resolution? = null

    private var imageReader: ImageReader? = null

    private var isProcessingFrame = false

    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }

    private val imageReaderHandler = Handler(imageReaderThread.looper)

    private var postInferenceCallback: Runnable? = null

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
    }

    /**
     * Stops preview stream.
     */
    fun stop() {
        imageReader?.close()
        imageReaderThread.quitSafely()
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

    private fun dispatchFrameOnBackgroundThread(image: Image) {
        val data = CameraUtils.imageToByteArray(image)
        postInferenceCallback = Runnable {
//            image.close()
            isProcessingFrame = false
        }

        frameProcessingExecutor.execute {
            synchronized(frameProcessors) {
                dispatchFrame(data!!)
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


    @SuppressLint("Range")
    fun setPreviewResolution(resolution: Resolution) {
        previewResolution = resolution

        if(imageReader != null) {
            imageReader!!.close()
        }

        imageReader = ImageReader.newInstance( resolution.width, resolution.height,
            ImageFormat.YUV_420_888, 2)
        imageReader?.setOnImageAvailableListener(onImageAvailableCallback, imageReaderHandler)

    }

    fun getPreviewSurface() : Surface {
        return imageReader!!.surface
    }

    private val onImageAvailableCallback = ImageReader.OnImageAvailableListener { reader ->
        val image: Image? = reader?.acquireLatestImage()

        image?.use {

            if(!isProcessingFrame) {
                isProcessingFrame = true
                dispatchFrameOnBackgroundThread(it)
            } else {
                it.close()
            }
        }
    }

}
