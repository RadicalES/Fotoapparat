package io.fotoapparat.image;

import android.graphics.Matrix;
import android.hardware.camera2.CameraCharacteristics;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

public interface ImageInfo {

//    @NonNull
//    TagBundle getTagBundle();

    long getTimestamp();

    /**
     * Returns the rotation needed to transform the image to the correct orientation.
     *
     * <p> This is a clockwise rotation in degrees that needs to be applied to the image buffer.
     * Note that for images that are in {@link android.graphics.ImageFormat#JPEG} this value will
     * match the rotation defined in the EXIF.
     *
     * <p> The target rotation is set at the time the image capture was requested.
     *
     * <p> The correct orientation of the image is dependent upon the producer of the image. For
     * example, if the {@link ImageProxy} that contains this instance of ImageInfo is produced
     * by an {@link ImageCapture}, then the rotation will be determined by
     * {@link ImageCapture#setTargetRotation(int)} or
     * {@link ImageCapture.Builder#setTargetRotation(int)}.
     *
     * @return The rotation in degrees which will be a value in {0, 90, 180, 270}.
     * @see ImageCapture#setTargetRotation(int)
     * @see ImageCapture.Builder#setTargetRotation(int)
     * @see ImageAnalysis#setTargetRotation(int)
     * @see ImageAnalysis.Builder#setTargetRotation(int)
     */
    // TODO(b/122806727) Need to correctly set EXIF in JPEG images
    int getRotationDegrees();

    /**
     * Returns the sensor to image buffer transform matrix.
     *
     * <p>The returned matrix is a mapping from sensor coordinates to buffer coordinates,
     * which is, from the value of {@link CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE} to
     * {@code (0, 0, image.getWidth, image.getHeight)}. The matrix can be used to map the
     * coordinates from one {@link UseCase} to another. For example, mapping coordinates of the
     * face detected with {@link ImageAnalysis} to {@link ImageCapture}.
     *
     * If {@link ImageAnalysis.Builder#setOutputImageRotationEnabled} is set to false,
     * {@link ImageInfo#getRotationDegrees()} will return the rotation degree that needs to be
     * applied to the image buffer to user. In this case, the transform matrix can be
     * calculated using rotation degrees.
     *
     * If {@link ImageAnalysis.Builder#setOutputImageRotationEnabled} is set to true, the
     * ImageAnalysis pipeline will apply the rotation to the image buffer and
     * {@link ImageInfo#getRotationDegrees()} will always return 0. In this case, the transform
     * matrix cannot be calculated.
     *
     * This API provides the transform matrix which could handle both cases.
     *
     * <pre>
     *     <code>
     *         // Calculate the matrix
     *         Matrix analysisToSensor = new Matrix();
     *         analysisToSensor.invert(
     *             imageAnalysisImageProxy.getImageInfo()
     *                                    .getSensorToBufferTransformMatrix());
     *         Matrix sensorToCapture = captureImageProxy.getImageInfo()
     *                                                   .getSensorToBufferTransformMatrix();
     *         Matrix analysisToCapture = new Matrix();
     *         analysisToCapture.setConcat(analysisToSensor, sensorToCapture);
     *
     *         // Transforming the coordinates
     *         Rect faceBoundingBoxInAnalysis;
     *         Rect faceBoundingBoxInCapture;
     *         analysisToCapture.mapRect(faceBoundingBoxInAnalysis, faceBoundingBoxInCapture);
     *
     *         // faceBoundingBoxInCapture is the desired value
     *     </code>
     * </pre>
     *
     * @return the transform matrix.
     */
    @NonNull
    default Matrix getSensorToBufferTransformMatrix() {
        return new Matrix();
    }


    /**
     * Adds any stored EXIF information in this ImageInfo into the provided ExifData builder.
     *
     */

//    void populateExifData(@NonNull ExifData.Builder exifBuilder);

}
