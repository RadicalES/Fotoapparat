package io.fotoapparat.preview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import io.fotoapparat.hardware.frameProcessingExecutor
import io.fotoapparat.hardware.orientation.Orientation
import io.fotoapparat.image.BitmapUtils
import io.fotoapparat.parameter.Resolution
import io.fotoapparat.util.CameraUtils
import io.fotoapparat.util.FrameProcessor
import kotlinx.coroutines.sync.Mutex
import java.util.*

/**
 * Preview stream of Camera.
 */
internal class PreviewStream() {

    private val frameProcessors = LinkedHashSet<FrameProcessor>()

    private var imageResolution: Resolution? = null

    private var imageReader: ImageReader? = null

    private var isProcessingFrame = false

    private var processMutex = Mutex()

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

    private fun dispatchFrameOnBackgroundThread(data: Bitmap, width: Int, height: Int, rotation: Int) {

        frameProcessingExecutor.execute {
            synchronized(frameProcessors) {
                dispatchFrame(data, width, height, rotation)
            }
            postInferenceCallback!!.run()
        }
    }

    private fun dispatchFrame(image: Bitmap, width: Int, height: Int, rotation: Int) {
        val prevRes = ensurePreviewSizeAvailable()

        val frame = Frame(
                size = prevRes,
                image = image,
                rotation = rotation,
                width = width,
                height = height
        )

        frameProcessors.forEach {
            it.invoke(frame)
        }

    }

    private fun ensurePreviewSizeAvailable(): Resolution =
        imageResolution
                    ?: throw IllegalStateException("previewSize is null. Frame was not added?")


    @SuppressLint("Range")
    fun setImageResolution(resolution: Resolution) {
        imageResolution = resolution
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
//            if(!isProcessingFrame) {
              if(processMutex.tryLock()) {
                  isProcessingFrame = true

                  Log.d(TAG, ": image w = ${it.width}, h = ${it.height}")

                  try {
//                        val data = CameraUtils.imageToByteArray(it)
//                       val data = BitmapUtils.yuv420ThreePlanesToNV21(it.planes, it.width, it.height)
//                       val bdata = ByteArray(data.remaining()).apply {
//                            data.get(this)
//                        }
                      frameOrientation = Orientation.Vertical.Portrait

//                      val data = CameraUtils.imageToByteArray(it)
//                      val data: Bitmap = BitmapUtils.imageToBitmap(image, -90)
                      val data = CameraUtils.yuv420ThreePlanesToNV21(it.planes, it.width, it.height)
                      val bitmap = BitmapUtils.getBitmap(data, it.width, it.height, -90)


                        postInferenceCallback = Runnable {
                            it.close()
                            isProcessingFrame = false
                            processMutex.unlock()
                        }

                        dispatchFrameOnBackgroundThread(bitmap!!, it.width, it.height, frameOrientation.degrees)
                  } catch (_: IllegalStateException) { }

            } else {
                it.close()
            }
        }
    }

}
