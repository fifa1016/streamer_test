package com.example.javacv05streamtest;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;

/**
 * Created by wangyanxiang on 2015/11/3.
 */
public class YkCameraHelper {
    private static final String TAG = YkCameraHelper.class.getSimpleName();
    private static Camera camera;
    private static int cameraType = Camera.CameraInfo.CAMERA_FACING_BACK;

    public static int getCameraType() {
        return cameraType;
    }

    private static DisplayMetrics displayMetrics;
    private static float displayRatio;// height/width ratio

    public static void setDisplayMetrics(DisplayMetrics metrics) {
        displayMetrics = metrics;
    }

    public static DisplayMetrics getDisplayMetrics() {
        return displayMetrics;
    }

    /**
     * get best camera preview size according to devices
     *
     * @param sizeList width>=height
     * @return
     */
    public static Camera.Size getOptimalPreviewSize(List<Camera.Size> sizeList) {
        float tolerance = 0.1f;
        if (displayMetrics != null) {
            // portrait
            int screenWidth = displayMetrics.widthPixels;
            int screenHeight = displayMetrics.heightPixels;
            displayRatio = screenHeight / screenWidth;

            for (Camera.Size size : sizeList) {
                float ratio = size.width / size.height;
                if (Math.abs(displayRatio - ratio) > tolerance) {
                    continue;
                }
                if (Math.abs(size.height - 480) < 10) {
                    Log.d(TAG, "getOptimalPreviewSize() w=" + size.width + ",h=" + size.height);
                    return size;
                }
            }
        }
        return null;
    }

    public static Camera.Size getRecordPreviewSize(List<Camera.Size> sizeList) {
        float ratio = (float) 1280 / 720; // width / height
        float tolerance = 0.1f;

        for (Camera.Size size : sizeList) {
            if (Math.abs((float) size.width / size.height - ratio) < tolerance) {
                return size;
            }
        }

        return null;
    }

    public static void releaseCamera() {
        Log.d(TAG, "releaseCamera()");
        if (camera != null) {
            try {
                camera.setPreviewCallback(null);
                camera.stopPreview();
            } catch (Exception e) {
            }
            camera.release();
            camera = null;
        }
    }

    public static Camera openCamera() {
        return openCamera(cameraType);
    }

    /**
     * @param type Camera.CameraInfo.CAMERA_FACING_BACK
     *             Camera.CameraInfo.CAMERA_FACING_FRONT
     * @return
     */
    private static Camera openCamera(int type) {
        Log.d(TAG, "openCamera()");
        if (camera != null) {
            if (type != cameraType) {
                releaseCamera();
            } else {
                return camera;
            }
        }

        int num = Camera.getNumberOfCameras();
        if (num > 0) {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            for (int i = 0; i < num; i++) {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == type) {
                    camera = Camera.open(i);
                }
            }
        } else {
            camera = Camera.open();
        }

        if (camera != null) {
            Log.d(TAG, " camera not null 126 ");
            Camera.Parameters para = camera.getParameters();

            //focus mode
            if (para.getSupportedFocusModes().contains(
                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                para.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (para.getSupportedFocusModes().contains(
                    Camera.Parameters.FOCUS_MODE_AUTO)) {
                para.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }

            // preview size
            List<Camera.Size> sizeList = para.getSupportedPreviewSizes();
            Log.d(TAG, "supported preview size:");
            for (Camera.Size size : sizeList) {
                Log.d(TAG, "size w=" + size.width + ", h=" + size.height);
            }
            Camera.Size previewSize = getOptimalPreviewSize(sizeList);
            if (previewSize != null) {
                para.setPreviewSize(previewSize.width, previewSize.height);
            }

//            // frame fps
//            List<int[]> rangesList = para.getSupportedPreviewFpsRange();
//            int[] range = rangesList.get(0);
//            Log.d(TAG, "fps range[" + range[0] + "," + range[1] + "]");
//
//            int rate = para.getPreviewFrameRate();
//            para.setPreviewFrameRate(rate);
//            Log.d(TAG, " rate " + rate);
//
//            if (range[1] > 24000) {
//                para.setPreviewFpsRange(range[0], 24000);
//            }

            para.setRecordingHint(true);

            camera.setParameters(para);

        } else {
            Log.d(TAG, "camera null");
            throw new RuntimeException("Can't open camera");
        }
        Log.d(TAG, "openCamera() end");

        cameraType = type;
        return camera;
    }

    public static Camera switchCamera() {
        Log.d(TAG, "switchCamera()");
        releaseCamera();
        if (cameraType == Camera.CameraInfo.CAMERA_FACING_BACK) {
            cameraType = Camera.CameraInfo.CAMERA_FACING_FRONT;
        } else {
            cameraType = Camera.CameraInfo.CAMERA_FACING_BACK;
        }
        return openCamera(cameraType);
    }

    public static final int SWITCH_FLASH_FAILED = -1;
    public static final int SWITCH_FLASH_OFF = 0;
    public static final int SWITCH_FLASH_TORCH = 1;

    /**
     * @return -1 if set failed, see SWITCH_FLASH_*
     */
    public static int switchFlashModes() {
        Log.d(TAG, "switchFlashModes()");
        int flag = SWITCH_FLASH_FAILED;

        Camera cam = openCamera();
        if (cam == null) {
            return SWITCH_FLASH_FAILED;
        }

        Camera.Parameters para = cam.getParameters();
        List<String> flashModesList = para.getSupportedFlashModes();
        if (flashModesList == null) {
            return SWITCH_FLASH_FAILED;
        }

        String oldMode = para.getFlashMode();

        if (oldMode.equals(Camera.Parameters.FLASH_MODE_TORCH)) {
            if (flashModesList.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                para.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                flag = SWITCH_FLASH_OFF;
            }
        } else {
            if (flashModesList.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                para.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                flag = SWITCH_FLASH_TORCH;
            }
        }
        cam.setParameters(para);

        return flag;
    }
}
