package io.fotoapparat.image;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public final class BitmapUtils {

    public static ByteBuffer yuv420ThreePlanesToNV21(
            Image.Plane[] yuv420888planes, int width, int height) {
        int imageSize = width * height;
        byte[] out = new byte[imageSize + 2 * (imageSize / 4)];

        if (areUVPlanesNV21(yuv420888planes, width, height)) {
            // Copy the Y values.
            yuv420888planes[0].getBuffer().get(out, 0, imageSize);

            ByteBuffer uBuffer = yuv420888planes[1].getBuffer();
            ByteBuffer vBuffer = yuv420888planes[2].getBuffer();
            // Get the first V value from the V buffer, since the U buffer does not contain it.
            vBuffer.get(out, imageSize, 1);
            // Copy the first U value and the remaining VU values from the U buffer.
            uBuffer.get(out, imageSize + 1, 2 * imageSize / 4 - 1);
        } else {
            // Fallback to copying the UV values one by one, which is slower but also works.
            // Unpack Y.
            unpackPlane(yuv420888planes[0], width, height, out, 0, 1);
            // Unpack U.
            unpackPlane(yuv420888planes[1], width, height, out, imageSize + 1, 2);
            // Unpack V.
            unpackPlane(yuv420888planes[2], width, height, out, imageSize, 2);
        }

        return ByteBuffer.wrap(out);
    }

    /** Checks if the UV plane buffers of a YUV_420_888 image are in the NV21 format. */
    private static boolean areUVPlanesNV21(Image.Plane[] planes, int width, int height) {
        int imageSize = width * height;

        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        // Backup buffer properties.
        int vBufferPosition = vBuffer.position();
        int uBufferLimit = uBuffer.limit();

        // Advance the V buffer by 1 byte, since the U buffer will not contain the first V value.
        vBuffer.position(vBufferPosition + 1);
        // Chop off the last byte of the U buffer, since the V buffer will not contain the last U
        // value.
        uBuffer.limit(uBufferLimit - 1);

        // Check that the buffers are equal and have the expected number of elements.
        boolean areNV21 =
                (vBuffer.remaining() == (2 * imageSize / 4 - 2))
                        && (vBuffer.compareTo(uBuffer) == 0);

        // Restore buffers to their initial state.
        vBuffer.position(vBufferPosition);
        uBuffer.limit(uBufferLimit);

        return areNV21;
    }

    /**
     * Unpack an image plane into a byte array.
     *
     * <p>The input plane data will be copied in 'out', starting at 'offset' and every pixel will be
     * spaced by 'pixelStride'. Note that there is no row padding on the output.
     */
    private static void unpackPlane(
            Image.Plane plane, int width, int height, byte[] out, int offset, int pixelStride) {
        ByteBuffer buffer = plane.getBuffer();
        buffer.rewind();

        // Compute the size of the current plane.
        // We assume that it has the aspect ratio as the original image.
        int numRow = (buffer.limit() + plane.getRowStride() - 1) / plane.getRowStride();
        if (numRow == 0) {
            return;
        }
        int scaleFactor = height / numRow;
        int numCol = width / scaleFactor;

        // Extract the data in the output buffer.
        int outputPos = offset;
        int rowStart = 0;
        for (int row = 0; row < numRow; row++) {
            int inputPos = rowStart;
            for (int col = 0; col < numCol; col++) {
                out[outputPos] = buffer.get(inputPos);
                outputPos += pixelStride;
                inputPos += plane.getPixelStride();
            }
            rowStart += plane.getRowStride();
        }
    }

    public static Bitmap imageToBitmap(Image image, Integer rotation) {
        // Preconditions
        if (image == null ) {
            return null;
        }

        if (image.getFormat() == ImageFormat.YUV_420_888) {
            ByteBuffer nv21Buffer =
                    BitmapUtils.yuv420ThreePlanesToNV21(
                            image.getPlanes(), image.getWidth(), image.getHeight());
            return BitmapUtils.getBitmap(nv21Buffer, image.getWidth(), image.getHeight(), rotation);
        } else if (image.getFormat() == ImageFormat.JPEG) {
            return BitmapUtils.rotateBitmap(
                    getJpegBitmap(image), rotation, false, false);
        }

        return null;
    }

    private static Bitmap getJpegBitmap(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
    }

    public static Bitmap getBitmap(ByteBuffer data, int width, int height, int rotation) {
        data.rewind();
        byte[] imageInBuffer = new byte[data.limit()];
        data.get(imageInBuffer, 0, imageInBuffer.length);
        try {
            YuvImage image =
                    new YuvImage(
                            imageInBuffer,
                            ImageFormat.NV21,
                            width,
                            height,
                            null);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            image.compressToJpeg(
                    new Rect(0, 0, width, height), 80, stream);

            Bitmap bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());

            stream.close();
            return rotateBitmap(bmp, rotation, false, false);
        } catch (Exception e) {
            Log.e("VisionProcessorBase", "Error: " + e.getMessage());
        }
        return null;
    }

    public static Bitmap rotateBitmap(
            Bitmap bitmap, int rotationDegrees, boolean flipX, boolean flipY) {
        Matrix matrix = new Matrix();

        // Rotate the image back to straight.
        matrix.postRotate(rotationDegrees);

        // Mirror the image along the X or Y axis.
        matrix.postScale(flipX ? -1.0f : 1.0f, flipY ? -1.0f : 1.0f);
        Bitmap rotatedBitmap =
                Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        // Recycle the old bitmap if it has changed.
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }
        return rotatedBitmap;
    }

}
