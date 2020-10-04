package com.nematjon.edd_client_season_two;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.SparseArray;
import android.view.Surface;


import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.vision.Frame;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.nematjon.edd_client_season_two.services.MainService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;

import static com.google.mlkit.vision.face.FaceDetection.getClient;

public class Camera2Capture {

    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private Context mContext;

    private static final int HOURS24 = 24 * 60 * 60; //in sec
    protected static final int CAMERA_CALIBRATION_DELAY = 1000; //in miliseconds
    protected static long cameraCaptureStartTime;
    private static long prevCapturetime = 0;
    private static long prevCapturetimeCropped = 0;

    static int capturedPhotoDataSrcId;
    static SharedPreferences confPrefs;


    public Camera2Capture(Context context) {
        this.mContext = context;
    }


    public void setupCamera2() {
        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);

        try {

            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                if (characteristics.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                this.cameraId = cameraId;
                imageReader = ImageReader.newInstance(1080, 1440, ImageFormat.JPEG, 2); // 3 x 4 aspect ratio
                imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
            }
            openCamera2();
        } catch (CameraAccessException | NullPointerException e) {
            e.printStackTrace();
        }
    }


    private void openCamera2() {
        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {

            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                //todo request permissions
                Log.e("TAG", "openCamera2: Camera permission not granted");
                return;
            }
            manager.openCamera(cameraId, cameraStateCallback, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice device) {
            cameraDevice = device;
            createCaptureSession();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
        }
    };

    private void createCaptureRequest() {
        try {

            CaptureRequest.Builder requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            requestBuilder.addTarget(imageReader.getSurface());

            // Focus
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // Orientation
            requestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 270); //get vertical rotation

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cameraCaptureSession.capture(requestBuilder.build(), null, null);
            }
            //region Android 9 and below
            else {
                requestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, getRange());
                cameraCaptureSession.setRepeatingRequest(requestBuilder.build(), null, null);
                cameraCaptureStartTime = System.currentTimeMillis();
            }
            //endregion
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void createCaptureSession() {
        List<Surface> outputSurfaces = new LinkedList<>();
        outputSurfaces.add(imageReader.getSurface());

        try {

            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    cameraCaptureSession = session;
                    createCaptureRequest();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private final ImageReader.OnImageAvailableListener onImageAvailableListener = mImageReader -> {


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            processImageAndroidQandMore(mImageReader);
        }else{
            processImageAndroidQandLess(mImageReader);
        }
    };


    public void cropFace(byte[] byteArrayImage, Context mContext) throws IOException {

        Bitmap tempBitmap = BitmapFactory.decodeByteArray(byteArrayImage, 0, byteArrayImage.length);
        Bitmap rotatedBitmap = Tools.rotateBitmap(tempBitmap, 270);
        final InputImage image = InputImage.fromBitmap(rotatedBitmap, 0);


        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                        .build();

        final FaceDetector detector = getClient(options);
        detector.process(image).addOnSuccessListener(faces -> {
            Log.e("TAG", "onSuccess: Face detected. Number of faces: " + faces.size());

            if (faces.size() == 1) { //when there are more than 1 faces, the app crashes

                //region saving not cropped photo to phone once every 24 hours
                File fileFull = new File(mContext.getExternalFilesDir("Taken photos") + File.separator + System.currentTimeMillis() + ".jpg"); // todo: remove saving images to the app folder
                //File file = new File(mContext.getExternalFilesDir("Photos") + File.separator + System.currentTimeMillis() + ".jpg"); // todo: remove saving images to the app folder
                FileOutputStream outputFull = null;

                long currentTime = System.currentTimeMillis();
                if (currentTime > prevCapturetime + HOURS24 * 1000) {
                    try {
                        outputFull = new FileOutputStream(fileFull);
                        outputFull.write(byteArrayImage);    // write the byte array to file

                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            outputFull.close();
                            prevCapturetime = currentTime;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        Log.e("TAG", "processImage: DONE SAVING");
                    }
                }

                //endregion


                for (Face face : faces) {
                    //Getting smiling probability
                    float smile = 0f;
                    try {
                        smile = face.getSmilingProbability();
                        Log.e("FACE", "SMILE: " + smile);
                    } catch (Exception e) {
                        Log.e("TAG", "Could not find smile");
                        smile = 0f;
                    }

                    FaceContour contour = face.getContour(FaceContour.FACE);
                    Path path = new Path();
                    assert contour != null;
                    path.moveTo(contour.getPoints().get(0).x, contour.getPoints().get(0).y);
                    for (PointF item : contour.getPoints()) {
                        path.lineTo(item.x, item.y);
                    }
                    path.close();
                    detector.close();

                    Bitmap output = Bitmap.createBitmap(rotatedBitmap.getWidth(), rotatedBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(output);
                    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    paint.setColor(Color.BLUE);
                    canvas.drawPath(path, paint);
                    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                    canvas.drawBitmap(rotatedBitmap, 0, 0, paint);

                    // saving the cropped face
                    File file;
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    OutputStream ous;

                    file = new File(mContext.getExternalFilesDir("Taken photos") + File.separator + System.currentTimeMillis() + ".jpg");
                    //file = new File(mContext.getExternalFilesDir("Cropped Faces") + File.separator + System.currentTimeMillis() + ".jpg"); // todo: remove saving images to the app folder
                    output.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    byte[] faceByteArray = stream.toByteArray();
                    String faceInString = (Base64.getEncoder().encodeToString(faceByteArray));
                    Log.e("TAG", "cropFace: STRING" + faceInString.length());
                    output.recycle();

                    // region save image to phone only every 24hours (once per day)
                    long nowtime = System.currentTimeMillis();
                    if (nowtime > prevCapturetimeCropped + HOURS24 * 1000) {
                        try {
                            ous = new FileOutputStream(file);
                            ous.write(faceByteArray);
                            Log.e("TAG", "cropFace: Cropped face saved");
                            ous.close();
                            prevCapturetimeCropped = nowtime;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    //endregion

                    //submitting data to server
                    submitPhotoData(smile, faceInString);
                }


            }
        })
                .addOnFailureListener(e -> Log.e("TAG", "onFailure: Failed to detect face"));
    }


    public void submitPhotoData(float smile, String photo) {

        confPrefs = mContext.getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        capturedPhotoDataSrcId = confPrefs.getInt("CAPTURED_PHOTOS", -1);

        long timestamp = System.currentTimeMillis();
        String smile_type = "SMILE";
        String photo_byteArray_type = "PHOTO";

        //gravity sensor values
        float x_value = MainService.x_value_gravity;
        float y_value = MainService.y_value_gravity;
        float z_value = MainService.z_value_gravity;

        assert capturedPhotoDataSrcId != -1;
        DbMgr.saveMixedData(capturedPhotoDataSrcId, timestamp, 1.0f, timestamp, smile, smile_type);
        timestamp = System.currentTimeMillis();
        DbMgr.saveMixedData(capturedPhotoDataSrcId, timestamp, 1.0f, timestamp, photo, x_value, y_value, z_value, photo_byteArray_type);

    }

    public static Bitmap createImage(int width, int height, int color) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(color);
        canvas.drawRect(0F, 0F, (float) width, (float) height, paint);
        return bitmap;
    }

    private Range<Integer> getRange() {
        CameraManager mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics chars = null;
        try {
            chars = mCameraManager.getCameraCharacteristics(cameraId);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Range<Integer>[] ranges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);

        Range<Integer> result = null;

        for (Range<Integer> range : ranges) {
            int upper = range.getUpper();

            // 10 - min range upper for my needs
            if (upper >= 10) {
                if (result == null || upper < result.getUpper().intValue()) {
                    result = range;
                }
            }
        }
        Log.e("TAG", "getRange: " + result);
        return result;
    }

    private void processImageAndroidQandMore(ImageReader mImageReader) {
        ByteBuffer buffer;
        byte[] bytes;

        Image image = mImageReader.acquireNextImage();
        if (image != null) {
            buffer = image.getPlanes()[0].getBuffer();
            bytes = new byte[buffer.remaining()]; // makes byte array large enough to hold image
            buffer.get(bytes); // copies image from buffer to byte array
            try {
                image.close();
            } catch (Exception e) {
                Log.e("TAG", "onImageAvailable: image could not be closed");
            }


            try {
                cropFace(bytes, mContext);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (cameraDevice != null) {
            image.close();
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void processImageAndroidQandLess(ImageReader mImageReader) {
        ByteBuffer buffer;
        byte[] bytes;

        Image image = mImageReader.acquireLatestImage();
        if (image != null) {
            if (System.currentTimeMillis() > cameraCaptureStartTime + CAMERA_CALIBRATION_DELAY) {
                buffer = image.getPlanes()[0].getBuffer();
                bytes = new byte[buffer.remaining()]; // makes byte array large enough to hold image
                buffer.get(bytes); // copies image from buffer to byte array
                try {
                    image.close();
                } catch (Exception e) {
                    Log.e("TAG", "onImageAvailable: image could not be closed");
                }


                try {
                    cropFace(bytes, mContext);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (cameraDevice != null) {
                image.close();
                cameraDevice.close();
                cameraDevice = null;
            }
        }
    }


}
