package com.nematjon.edd_client_season_two.services;

import android.Manifest;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

public class Camera2Service {
    protected static final int CAMERA_CALIBRATION_DELAY = 2000; //
    protected static final String TAG = "myLog";
    protected static final int CAMERACHOICE = CameraCharacteristics.LENS_FACING_FRONT;
    protected static long cameraCaptureStartTime;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSession;
    protected ImageReader imageReader;
    protected boolean isCameraClosed = false;
    protected Context mContext;

    public Camera2Service(Context context) {
        this.mContext = context;
    }


    protected CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "CameraDevice.StateCallback onOpened");
            cameraDevice = camera;
            actOnReadyCameraDevice();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.w(TAG, "CameraDevice.StateCallback onDisconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "CameraDevice.StateCallback onError " + error);
        }

//        @Override
//        public void onClosed(@NonNull CameraDevice camera) {
//
//
//            super.onClosed(camera);
//            isCameraClosed = true;
//        }
    };

    //todo add checking camera availability
    protected CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onReady(CameraCaptureSession session) {
            Camera2Service.this.cameraCaptureSession = session;

            cameraCaptureStartTime = System.currentTimeMillis();
            readyCamera();


//            try {
//                session.setRepeatingRequest(createCaptureRequest(), null, null);
//
//                cameraCaptureStartTime = System.currentTimeMillis();
//            } catch (CameraAccessException e) {
//                Log.e(TAG, Objects.requireNonNull(e.getMessage()));
//            }
        }


        @Override
        public void onConfigured(CameraCaptureSession session) {

            cameraCaptureSession = session;
            createCaptureRequest();

        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
        }
    };

    protected ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "onImageAvailable");
            //reader.setOnImageAvailableListener(onImageAvailableListener, null);
            Image img = reader.acquireNextImage();


            if (img != null) {
                if (System.currentTimeMillis() > cameraCaptureStartTime + CAMERA_CALIBRATION_DELAY) {
                    try {

                        processImage(img);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }

                }
                img.close();


//                try {
//                    Thread.sleep(5000);
//                    session.stopRepeating();
//                    session.capture(createCaptureRequest(), null, null);
//                } catch (CameraAccessException | InterruptedException e) {
//                    e.printStackTrace();
//                }

            } else {
                Log.e(TAG, "onImageAvailable: IMAGE IS NULL");
            }
        }
    };

    public void readyCamera() {
        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            String pickedCamera = getCamera(manager);
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            getCamera(manager);


            imageReader = ImageReader.newInstance(1920, 1088, ImageFormat.JPEG, 2 /* images buffered */);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, null);
            Log.d(TAG, "imageReader created");
           // actOnReadyCameraDevice();
            manager.openCamera(pickedCamera, cameraStateCallback, null);


        } catch (CameraAccessException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public String getCamera(CameraManager manager) {
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                int cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (cOrientation == CAMERACHOICE) {
                    return cameraId;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        Log.e(TAG, "PIED PIPER flags " + flags + " startId " + startId);
//
//
//        readyCamera();
//
//
//        return super.onStartCommand(intent, flags, startId);
//    }


    public void actOnReadyCameraDevice() {
        try {
            cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()), sessionStateCallback, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, e.getMessage());
        }
    }

//    @Override
//    public void onDestroy() {
//        try {
//            cameraCaptureSession.abortCaptures();
//        } catch (CameraAccessException e) {
//            Log.e(TAG, e.getMessage());
//        }
//        if(!isCameraClosed){
//        cameraDevice.close();}
//        cameraCaptureSession.close();
//       // stopSelf();
//    }


    private void processImage(Image image) throws CameraAccessException {
        //Process image data
        Log.e(TAG, "processImage: IS BEING PROCESSED");
        ByteBuffer buffer;
        byte[] bytes;
        boolean success = false;
        File file = new File(mContext.getExternalFilesDir("Photos") + File.separator + System.currentTimeMillis() + ".jpg");
        FileOutputStream output = null;

        if (image.getFormat() == ImageFormat.JPEG) {

            Log.e(TAG, "processImage:JPEG ");
            buffer = image.getPlanes()[0].getBuffer();
            bytes = new byte[buffer.remaining()]; // makes byte array large enough to hold image
            buffer.get(bytes); // copies image from buffer to byte array
            try {
                output = new FileOutputStream(file);
                output.write(bytes);    // write the byte array to file
                success = true;
                Log.e(TAG, "processImage: DOLJEN BIT SUCcESS");

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                image.close(); // close this to free up buffer for other images
                Log.e(TAG, "processImage: DONE SAVING");

//                if (!isCameraClosed){
//                    cameraDevice.close();}
////                session.close();
//           //     stopSelf();
//            }
//                try {
//                    Thread.sleep(5000);
//                    session.stopRepeating();
//                    session.close();
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }


                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
    }


    protected void createCaptureRequest() {
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(imageReader.getSurface());
            // Focus
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            cameraCaptureSession.capture(builder.build(), null, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, Objects.requireNonNull(e.getMessage()));
        }
    }


    private void saveImageToStorage(File imageFile) throws IOException {


        Uri contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        OutputStream stream = null;
        final ContentResolver resolver = mContext.getContentResolver();
        final String relativeLocation = Environment.DIRECTORY_PICTURES;

        final ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, imageFile.getName());
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, relativeLocation);
            contentValues.put(MediaStore.Images.ImageColumns.RELATIVE_PATH, relativeLocation);
        }

        try {
            contentUri = resolver.insert(contentUri, contentValues);
            if (contentUri == null) {
                throw new IOException("Failed to create new MediaStore record.");
            }

            stream = resolver.openOutputStream(contentUri);

            if (stream == null) {
                throw new IOException("Failed to get output stream.");
            }

        } catch (IOException e) {
            if (contentUri != null) {
                resolver.delete(contentUri, null, null);
            }

            throw new IOException(e);

        } finally {
            if (stream != null)
                stream.close();
        }
    }

}