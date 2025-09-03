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
import io.fotoapparat.util.ImageUtils
import io.fotoapparat.util.FrameProcessor
import kotlinx.coroutines.sync.Mutex
import java.util.*

/**
 * Preview stream of Camera.
 */
internal class PreviewStream() {

    private var processMutex = Mutex()
    private val frameProcessors = LinkedHashSet<FrameProcessor>()
    private var imageResolution: Resolution? = null
    private var imageReader: ImageReader? = null
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

    private fun dispatchFrameOnBackgroundThread( data: ByteArray,
                                                 width: Int,
                                                 height: Int,
                                                 rotation: Int) {
        val frame = Frame(
            image = data,
            rotation = rotation,
            width = width,
            height = height
        )

        frameProcessingExecutor.execute {
            synchronized(frameProcessors) {
                dispatchFrame(frame)
            }
            postInferenceCallback!!.run()
        }
    }

    private fun dispatchFrame(frame: Frame) {
        frameProcessors.forEach {
            it.invoke(frame)
        }
    }

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
              if(processMutex.tryLock()) {

                  try {
                      val data = ImageUtils.yuv420ThreePlanesToNV21(it.planes, it.width, it.height)
                      val buffer = ByteArray(data.remaining()).apply {
                          data.get(this)
                      }

                        postInferenceCallback = Runnable {
                            it.close()
                            processMutex.unlock()
                        }

                        dispatchFrameOnBackgroundThread( buffer,
                            it.width, it.height,
                            frameOrientation.degrees)

                  } catch (_: IllegalStateException) { }

            } else {
                it.close()
            }
        }
    }

}
