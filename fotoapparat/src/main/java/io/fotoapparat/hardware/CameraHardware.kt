@file:Suppress("DEPRECATION")

package io.fotoapparat.hardware

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.annotation.FloatRange
import androidx.core.graphics.rotationMatrix
import io.fotoapparat.capability.Capabilities
import io.fotoapparat.capability.provide.getCapabilities
import io.fotoapparat.characteristic.Characteristics
import io.fotoapparat.coroutines.AwaitBroadcastChannel
import io.fotoapparat.hardware.metering.FocalRequest
import io.fotoapparat.hardware.orientation.*
import io.fotoapparat.log.Logger
import io.fotoapparat.parameter.Resolution
import io.fotoapparat.parameter.SupportedParameters
import io.fotoapparat.parameter.camera.CameraParameters
import io.fotoapparat.parameter.camera.convert.toResolution
import io.fotoapparat.preview.PreviewStream
import io.fotoapparat.result.FocusResult
import io.fotoapparat.result.Photo
import io.fotoapparat.util.FrameProcessor
import io.fotoapparat.util.lineSeparator
import io.fotoapparat.view.Preview
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException


typealias PreviewSize = io.fotoapparat.parameter.Resolution

/**
 * Camera.
 */
internal open class CameraHardware(
        private val cameraManager: CameraManager,
        private val logger: Logger,
        val characteristics: Characteristics
) {

    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private val capabilities = CompletableDeferred<Capabilities>()
    private val cameraParameters = AwaitBroadcastChannel<CameraParameters>()

    private var cameraCompletable = CompletableDeferred<CameraDevice>()
    private var sessionCompletable = CompletableDeferred<CameraCaptureSession>()
    private lateinit var camera: CameraDevice
    private lateinit var previewStream: PreviewStream
    private lateinit var surface: Surface
    private lateinit var cameraSession: CameraCaptureSession

//    private var cachedCameraParameters: Camera.Parameters? = null
    private lateinit var displayOrientation: Orientation
    private lateinit var imageOrientation: Orientation
    private lateinit var previewOrientation: Orientation
    private val componentScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private val TAG = CameraHardware::class.java.simpleName
    }

    private class HandlerExecutor(handler: Handler) : Executor {
        private val mHandler = handler

        override fun execute(command: Runnable) {
            if (!mHandler.post(command)) {
                throw RejectedExecutionException("" + mHandler + " is shutting down");
            }
        }
    }

    /**
     * Opens a connection to a camera.
     */
    @SuppressLint("MissingPermission")
    open fun open() {
        logger.recordMethod()
        val lensPosition = characteristics.lensPosition
        val cameraId = characteristics.cameraId
        val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)

        cameraManager.openCamera(
            cameraId,
            object: CameraDevice.StateCallback() {

                override fun onOpened(device: CameraDevice) {
                    cameraCompletable.complete(device)
                }

                override fun onDisconnected(p0: CameraDevice) {
                    Log.w(TAG, "Camera $cameraId has been disconnected")
                }

                override fun onError(device: CameraDevice, error: Int) {
                    val msg = when (error) {
                        ERROR_CAMERA_DEVICE -> "Fatal (device)"
                        ERROR_CAMERA_DISABLED -> "Device policy"
                        ERROR_CAMERA_IN_USE -> "Camera in use"
                        ERROR_CAMERA_SERVICE -> "Fatal (service)"
                        ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                        else -> "Unknown"
                    }
                    val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                    Log.e(TAG, exc.message, exc)
//                    if (cont.isActive) cont.resumeWithException(exc)
                }

            }, cameraHandler)



        waitForCamera()
        getCamCapabilities(cameraCharacteristics)
        previewStream = PreviewStream(camera)

        Log.d(TAG, "open: complete")
    }

    private fun getCamCapabilities(cameraCharacteristics: CameraCharacteristics) = runBlocking {
        capabilities.complete(SupportedParameters(cameraCharacteristics).getCapabilities())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun waitForCamera() = runBlocking {
        cameraCompletable.await()
        camera = cameraCompletable.getCompleted()
        Log.d(TAG, "waitForCamera: complete")
    }


    /**
     * Closes the connection to a camera.
     */
    open fun close() {
        logger.recordMethod()
        surface.release()
        camera.close()
    }

    /**
     * Starts preview.
     */
    open fun startPreview() {
        logger.recordMethod()
        val w = getPreviewResolution().width.toFloat()
        val h = getPreviewResolution().height.toFloat()
        val rect = RectF(0F, 0F, w,h)
        val cx = rect.centerX()
        val cy = rect.centerY()
        val rd = displayOrientation.degrees.toFloat() + 90F
        val targets = listOf(surface)

        createCaptureSession(camera, targets, cameraHandler)

        waitForSession()

        val captureRequest = camera.createCaptureRequest(
            CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
//                rotationMatrix(rd, cx, cy)
            }
        cameraSession.setRepeatingRequest(captureRequest.build(), null, cameraHandler)

//        try {
//            camera.startPreview()
//        } catch (e: RuntimeException) {
//            throw CameraException(
//                    message = "Failed to start preview for camera with lens " +
//                            "position: ${characteristics.lensPosition} and id: ${characteristics.cameraId}",
//                    cause = e
//            )
//        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun waitForSession() = runBlocking {
        sessionCompletable.await()
        cameraSession = sessionCompletable.getCompleted()
        Log.d(TAG, "waitForSession: complete")
    }

    fun getCameraCharacteristics() : CameraCharacteristics {
        return cameraManager.getCameraCharacteristics(characteristics.cameraId)
    }

    private fun setupSessionWithDynamicRangeProfile(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler,
        stateCallback: CameraCaptureSession.StateCallback
    ): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val outputConfigs = mutableListOf<OutputConfiguration>()
            for (target in targets) {
                val outputConfig = OutputConfiguration(target)
//                outputConfig.setDynamicRangeProfile(args.dynamicRange)
                outputConfigs.add(outputConfig)
            }

            val sessionConfig = SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                outputConfigs, HandlerExecutor(handler), stateCallback)
//            if (android.os.Build.VERSION.SDK_INT >=
//                android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
//                && args.colorSpace != ColorSpaceProfiles.UNSPECIFIED) {
//                sessionConfig.setColorSpace(ColorSpace.Named.values()[args.colorSpace])
//            }
            device.createCaptureSession(sessionConfig)
            return true
        } else {
            device.createCaptureSession(targets, stateCallback, handler)
            return false
        }
    }



    /**
     * Stops preview.
     */
    open fun stopPreview() {
        logger.recordMethod()
        cameraSession.stopRepeating()
//        camera.stopPreview()
        cameraThread.quitSafely()
    }

    /**
     * Unlock camera.
     */
    open fun unlock() {
        logger.recordMethod()

//        camera.unlock()
    }

    /**
     * Lock camera.
     */
    open fun lock() {
        logger.recordMethod()

//        camera.lock()
    }

    /**
     * Invokes a still photo capture action.
     *
     * @return The captured photo.
     */
    open fun takePhoto(): Photo {
        logger.recordMethod()

//        return camera.takePhoto(imageOrientation.degrees)
        return Photo(byteArrayOf(), 1)
    }

    /**
     * Returns the [Capabilities] of the camera.
     */
    open suspend fun getCapabilities(): Capabilities {
        logger.recordMethod()
        return capabilities.await()
    }

    /**
     * Returns the [CameraParameters] used.
     */
    open suspend fun getParameters(): CameraParameters {
        logger.recordMethod()
        return cameraParameters.getValue()
    }

    /**
     * Updates the desired camera parameters.
     */
    open suspend fun updateParameters(cameraParameters: CameraParameters) {
        logger.recordMethod()

        this.cameraParameters.send(cameraParameters)

        logger.log("New camera parameters are: $cameraParameters")

//        cameraParameters.applyInto(cachedCameraParameters ?: camera.parameters)
//                .cacheLocally()
//                .setInCamera()
    }

    /**
     * Updates the frame processor.
     */
    open fun updateFrameProcessor(frameProcessor: FrameProcessor?) {
        logger.recordMethod()

        //previewStream.updateProcessorSafely(frameProcessor)
    }

    /**
     * Sets the current orientation of the display.
     */
    open fun setDisplayOrientation(orientationState: OrientationState) {
        logger.recordMethod()

        imageOrientation = computeImageOrientation(
                deviceOrientation = orientationState.deviceOrientation,
                cameraOrientation = characteristics.cameraOrientation,
                cameraIsMirrored = characteristics.isMirrored
        )

        displayOrientation = computeDisplayOrientation(
                screenOrientation = orientationState.screenOrientation,
                cameraOrientation = characteristics.cameraOrientation,
                cameraIsMirrored = characteristics.isMirrored
        )

        previewOrientation = computePreviewOrientation(
                screenOrientation = orientationState.screenOrientation,
                cameraOrientation = characteristics.cameraOrientation,
                cameraIsMirrored = characteristics.isMirrored
        )

        Log.d(TAG, "Orientations: $lineSeparator" +
                "Screen orientation (preview) is: ${orientationState.screenOrientation}. " + lineSeparator +
                "Camera sensor orientation is always at: ${characteristics.cameraOrientation}. " + lineSeparator +
                "Camera is " + if (characteristics.isMirrored) "mirrored." else "not mirrored."
        )

        Log.d(TAG, "Orientation adjustments: $lineSeparator" +
                "Image orientation will be adjusted by: ${imageOrientation.degrees} degrees. " + lineSeparator +
                "Display orientation will be adjusted by: ${displayOrientation.degrees} degrees. " + lineSeparator +
                "Preview orientation will be adjusted by: ${previewOrientation.degrees} degrees."
        )

        previewStream.frameOrientation = previewOrientation
//        camera.setDisplayOrientation(displayOrientation.degrees)
    }

    /**
     * Changes zoom level of the camera. Must be called only if zoom is supported.
     *
     * @param level normalized zoom level. Value in range [0..1].
     */
    open fun setZoom(@FloatRange(from = 0.0, to = 1.0) level: Float) {
        logger.recordMethod()

        setZoomSafely(level)
    }

    /**
     * Performs auto focus. This is a blocking operation which returns the result of the operation
     * when auto focus completes.
     */
    open fun autoFocus(): FocusResult {
        logger.recordMethod()

//        return camera.focusSafely()
        return FocusResult.Focused
    }

    /**
     * Sets the point where the focus & exposure metering will happen.
     */
    open suspend fun setFocalPoint(focalRequest: FocalRequest) {
        logger.recordMethod()

        if (capabilities.await().canSetFocusingAreas()) {
//            camera.updateFocusingAreas(focalRequest)
        }
    }

    /**
     * Clears the point where the focus & exposure will happen.
     */
    open fun clearFocalPoint() {
        logger.recordMethod()

//        camera.clearFocusingAreas()
    }

    /**
     * Sets the desired surface on which the camera's preview will be displayed.
     */
    @Throws(IOException::class)
    open fun setDisplaySurface(preview: Preview) {
        logger.recordMethod()
        surface = camera.setDisplaySurface(preview)
    }

    /**
     * Attaches the camera to the [MediaRecorder].
     */
    open fun attachRecordingCamera(mediaRecorder: MediaRecorder) {
        logger.recordMethod()

//        mediaRecorder.setCamera(camera)
    }

    /**
     * Returns the [Resolution] of the displayed preview.
     */
    open fun getPreviewResolution(): Resolution {
        logger.recordMethod()

        val previewResolution = this.getPreviewResolution(previewOrientation)

        logger.log("Preview resolution is: $previewResolution")

        return previewResolution
    }

    private fun setZoomSafely(@FloatRange(from = 0.0, to = 1.0) level: Float) {
        try {
            setZoomUnsafe(level)
        } catch (e: Exception) {
            logger.log("Unable to change zoom level to " + level + " e: " + e.message)
        }
    }

//    private fun getDisplayRotation(): Int {
//        previewOrientation
//        return when (textureView.getDisplay().getRotation()) {
//            Surface.ROTATION_0 -> 0
//            Surface.ROTATION_90 -> 90
//            Surface.ROTATION_180 -> 180
//            Surface.ROTATION_270 -> 270
//            else -> 0
//        }
//    }

    private fun setZoomUnsafe(@FloatRange(from = 0.0, to = 1.0) level: Float) {
//        (cachedCameraParameters ?: camera.parameters)
//                .apply {
//                    zoom = (maxZoom * level).toInt()
//                }
//                .cacheLocally()
//                .setInCamera()
    }

//    private fun Camera.Parameters.cacheLocally() = apply {
//        cachedCameraParameters = this
//    }
//
//    private fun Camera.Parameters.setInCamera() = apply {
//        camera.parameters = this
//    }
//
//    private fun Camera.focusSafely(): FocusResult {
//        val latch = CountDownLatch(1)
//
//        try {
//            autoFocus { _, _ -> latch.countDown() }
//        } catch (e: Exception) {
//            logger.log("Failed to perform autofocus using device ${characteristics.cameraId} e: ${e.message}")
//
//            return FocusResult.UnableToFocus
//        }
//
//        try {
//            latch.await(AUTOFOCUS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
//        } catch (e: InterruptedException) {
//            // Do nothing
//        }
//
//        return FocusResult.Focused
//    }
//
//    private suspend fun Camera.updateFocusingAreas(focalRequest: FocalRequest) {
//        val focusingAreas = focalRequest.toFocusAreas(
//                displayOrientationDegrees = displayOrientation.degrees,
//                cameraIsMirrored = characteristics.isMirrored
//        )
//
//        parameters = parameters.apply {
//            with(capabilities.await()) {
//                if (maxMeteringAreas > 0) {
//                    meteringAreas = focusingAreas
//                }
//
//                if (maxFocusAreas > 0) {
//                    if (focusModes.contains(FocusMode.Auto)) {
//                        focusMode = FocusMode.Auto.toCode()
//                    }
//                    focusAreas = focusingAreas
//                }
//            }
//        }
//    }
//
//    private fun Camera.clearFocusingAreas() {
//        parameters = parameters.apply {
//            meteringAreas = null
//            focusAreas = null
//        }
//    }

//    private fun isDimensionSwapped(): Boolean {
//
//        val sensorOrientation = getCameraCharacteristics().get(CameraCharacteristics.SENSOR_ORIENTATION)
//        var swappedDimensions = false
//        when (displayOrientation) {
//            Surface.ROTATION_0, Surface.ROTATION_180 -> {
//                if (sensorOrientation == 90 || sensorOrientation == 270) {
//                    swappedDimensions = true
//                }
//            }
//
//            Surface.ROTATION_90, Surface.ROTATION_270 -> {
//                if (sensorOrientation == 0 || sensorOrientation == 180) {
//                    swappedDimensions = true
//                }
//            }
//
//        }
//        return swappedDimensions
//    }

    private fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ) {

        // Create a capture session using the predefined targets; this also involves defining the
        // session state callback to be notified of when the session is ready
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) {
                sessionCompletable.complete(session)
                Log.d(TAG, "onConfigured: camera capture session created!")
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                sessionCompletable.completeExceptionally(exc)
            }
        }, handler)

    }


}

private const val AUTOFOCUS_TIMEOUT_SECONDS = 3L

//private fun Camera.takePhoto(imageRotation: Int): Photo {
//    val latch = CountDownLatch(1)
//    val photoReference = AtomicReference<Photo>()
//
//    takePicture(
//            null,
//            null,
//            null,
//            Camera.PictureCallback { data, _ ->
//                photoReference.set(
//                        Photo(data, imageRotation)
//                )
//
//                latch.countDown()
//            }
//    )
//
//    latch.await()
//
//    return photoReference.get()
//}

@Throws(IOException::class)
private fun CameraDevice.setDisplaySurface(
        preview: Preview
): Surface = when (preview) {
    is Preview.Texture -> preview.surfaceTexture
          //  .also(this::setPreviewTexture)
            .let(::Surface)

    is Preview.Surface -> preview.surfaceHolder
//            .also(this::setPreviewDisplay)
            .surface
}


private fun CameraHardware.getPreviewResolution(previewOrientation: Orientation): Resolution {

    val cfgMap: StreamConfigurationMap? = getCameraCharacteristics().get(SCALER_STREAM_CONFIGURATION_MAP)

    return cfgMap.run {
        val sizes = cfgMap?.getOutputSizes(SurfaceTexture::class.java)
        sizes!![0].toResolution()
    }

//    return parameters.previewSize
//            .run {
//                PreviewSize(width, height)
//            }
//            .run {
//                when (previewOrientation) {
//                    is Orientation.Vertical -> this
//                    is Orientation.Horizontal -> flipDimensions()
//                }
//            }
}

private fun Capabilities.canSetFocusingAreas(): Boolean =
        maxMeteringAreas > 0 || maxFocusAreas > 0
