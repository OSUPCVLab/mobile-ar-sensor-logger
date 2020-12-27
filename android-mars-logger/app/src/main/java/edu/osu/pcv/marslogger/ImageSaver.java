package edu.osu.pcv.marslogger;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import timber.log.Timber;
/**
 * Saves a JPEG/YUV_420_888 {@link Image} into the specified {@link File}.
 * Preliminary tests shows that saving YUV_420_888 takes on average 70ms
 * while saving JPEG takes 2.5ms which is 27 times faster.
 * Also the compressToJpeg method has been found to subject to a bug, see
 * https://blog.csdn.net/q979713444/article/details/80446404
 */
public class ImageSaver implements Runnable {

    /**
     * The image
     */
    private final Image mImage;
    /**
     * The file we save the image into.
     */
    private final File mFile;

    public ImageSaver(Image image, File file) {
        mImage = image;
        mFile = file;
    }

    @Override
    public void run() {
        long startTime = System.nanoTime();

        if (mImage.getFormat() == ImageFormat.JPEG) {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            long endTime = System.nanoTime();
            long duration = (endTime - startTime);
            Timber.d("ImageSaver saving jpeg takes %d", duration);
        } else if (mImage.getFormat() == ImageFormat.YUV_420_888) {
            // Collection of bytes of the image
            byte[] rez;

            // Convert to NV21 format
            // https://github.com/bytedeco/javacv/issues/298#issuecomment-169100091
            ByteBuffer buffer0 = mImage.getPlanes()[0].getBuffer();
            ByteBuffer buffer2 = mImage.getPlanes()[2].getBuffer();
            int buffer0_size = buffer0.remaining();
            int buffer2_size = buffer2.remaining();
            rez = new byte[buffer0_size + buffer2_size];

            // Load the final data var with the actual bytes
            buffer0.get(rez, 0, buffer0_size);
            buffer2.get(rez, buffer0_size, buffer2_size);

            // Byte output stream, so we can save the file
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Create YUV image file
            YuvImage yuvImage = new YuvImage(
                    rez, ImageFormat.NV21, mImage.getWidth(),
                    mImage.getHeight(), null);
            yuvImage.compressToJpeg(
                    new Rect(0, 0, mImage.getWidth(), mImage.getHeight()),
                    90, out);
            byte[] imageBytes = out.toByteArray();

            // Display for the end user
            Bitmap bmp = BitmapFactory.decodeByteArray(
                    imageBytes, 0, imageBytes.length);

            // Export the file to disk
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, output);
                output.flush();
                output.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            long endTime = System.nanoTime();
            long duration = (endTime - startTime);
            Timber.d("ImageSaver saving YUV takes %d", duration);
        } else {
            mImage.close();
        }
    }
}
