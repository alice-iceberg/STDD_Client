//package com.nematjon.edd_client_season_two.services;
//
//import android.Manifest;
//import android.content.ContentResolver;
//import android.content.ContentValues;
//import android.content.Context;
//import android.content.Intent;
//import android.content.SharedPreferences;
//import android.content.pm.PackageManager;
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
//import android.net.Uri;
//import android.os.Build;
//import android.os.Environment;
//import android.os.Handler;
//import android.os.IBinder;
//import android.os.Looper;
//import android.provider.MediaStore;
//import android.util.Log;
//import android.util.SparseArray;
//import android.widget.Toast;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import androidx.core.app.ActivityCompat;
//
//

//import com.androidhiddencamera.CameraConfig;
//import com.androidhiddencamera.CameraError;
//import com.androidhiddencamera.HiddenCameraService;
//import com.androidhiddencamera.HiddenCameraUtils;
//import com.androidhiddencamera.config.CameraFacing;
//import com.androidhiddencamera.config.CameraImageFormat;
//import com.androidhiddencamera.config.CameraResolution;
//import com.androidhiddencamera.config.CameraRotation;
//import com.google.android.gms.vision.Frame;
//import com.google.android.gms.vision.face.Face;
//
//
//
//import com.google.android.gms.vision.face.FaceDetector;
//import com.nematjon.edd_client_season_two.R;
//
//import java.io.BufferedOutputStream;
//import java.io.ByteArrayOutputStream;
//import java.io.File;
//import java.io.FileNotFoundException;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.io.OutputStream;
//import java.util.BitSet;
//
//public class CameraService extends HiddenCameraService {
//
//
//    SharedPreferences unlockPrefs;
//    public static float y_value_gravity = 0;
//
//    @Nullable
//    @Override
//    public IBinder onBind(Intent intent) {
//        return null;
//    }
//
//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        Log.e("TAG", "onStartCommand: INIT CAMERA");
//        unlockPrefs = getSharedPreferences("SecreenVariables", Context.MODE_PRIVATE);
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
//                == PackageManager.PERMISSION_GRANTED) {
//
//            if (HiddenCameraUtils.canOverDrawOtherApps(this)) {
//                Log.e("TAG", "onStartCommand: HERE1");
//
//
//                CameraConfig cameraConfig = new CameraConfig()
//                        .getBuilder(this)
//                        .setCameraFacing(CameraFacing.FRONT_FACING_CAMERA)
//                        .setCameraResolution(CameraResolution.MEDIUM_RESOLUTION)
//                        .setImageFormat(CameraImageFormat.FORMAT_JPEG)
//                        .setImageRotation(CameraRotation.ROTATION_270)
//                        .build();
//
//                startCamera(cameraConfig); // when this function is called we cannot open camera until it finishes
//
//
//                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//
//                       // boolean phoneUnlocked = unlockPrefs.getBoolean("unlocked", false);
//
//
//                                    Log.e("TAG", "CAMERAHIDDEN: taking picture of you");
//                                    takePicture(); //taking picture is a long process that is why it needs a separate thread
//
//
//
//
//                    }
//
//
//                }, 2000L);
//
//            } else {
//
//                //Open settings to grant permission for "Draw other apps".
//                HiddenCameraUtils.openDrawOverPermissionSetting(this);
//            }
//        }
//        return START_STICKY;
//    }
//
//
//    @Override
//    public void onImageCapture(@NonNull File imageFile) {
//        Log.e("TAG", "onImageCapture: Captured image size is " + imageFile.length());
//
//
//        // Do something with the image...
////        try { // saving taken image to local storage
////            MediaStore.Images.Media.insertImage(getContentResolver(), imageFile.getAbsolutePath(), imageFile.getName(), imageFile.getName());
////        } catch (FileNotFoundException e) {
////            e.printStackTrace();
////        }
//
//        try {
//            saveImageToStorage(imageFile);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//
//        //detect face
//
//        BitmapFactory.Options options = new BitmapFactory.Options();
//        options.inMutable = true;
//
//        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
//
//        FaceDetector detector = new FaceDetector.Builder(getApplicationContext())
//                .setTrackingEnabled(false).setClassificationType(1).
//                        build();
//
//        if (!detector.isOperational()) {
//            Log.e("TAG", "onImageCapture: COULD NOT SET UP FACE DETECTOR");
//            return;
//        }
//
//        Frame frame = new Frame.Builder().setBitmap(bitmap).build();
//        SparseArray<Face> faces = detector.detect(frame);
//        Bitmap croppedBitmap = null;
//
//        for (int i = 0; i < faces.size(); i++) {
//
//            Face thisFace = faces.valueAt(i);
//            float x1 = thisFace.getPosition().x;
//            float y1 = thisFace.getPosition().y;
//            //detect smile
//            float smile = thisFace.getIsSmilingProbability();
//            Log.e("TAG", "onClick: SMILE: " + smile);
//
//            //crop face
//            croppedBitmap = Bitmap.createBitmap(bitmap, Math.round(x1) + 1, Math.round(y1) + 1, Math.round(thisFace.getWidth()) - 5, Math.round(thisFace.getHeight()) - 5);
//            Log.e("TAG", "onImageCapture: CROPPED IMAGE SUCCESS");
//            long nowTime = System.currentTimeMillis();
//            String filename = nowTime + ".png";
//
//            MediaStore.Images.Media.insertImage(getContentResolver(), croppedBitmap, filename, "This is description");
//
//        }
//
//
//        stopSelf();
//    }
//
//    @Override
//    public void onCameraError(@CameraError.CameraErrorCodes int errorCode) {
//        switch (errorCode) {
//            case CameraError.ERROR_CAMERA_OPEN_FAILED:
//                //Camera open failed. Probably because another application
//                //is using the camera
//                Toast.makeText(this, R.string.error_cannot_open, Toast.LENGTH_LONG).show();
//                break;
//            case CameraError.ERROR_IMAGE_WRITE_FAILED:
//                //Image write failed. Please check if you have provided WRITE_EXTERNAL_STORAGE permission
//                Toast.makeText(this, R.string.error_cannot_write, Toast.LENGTH_LONG).show();
//                break;
//            case CameraError.ERROR_CAMERA_PERMISSION_NOT_AVAILABLE:
//                //camera permission is not available
//                //Ask for the camera permission before initializing it.
//                Toast.makeText(this, R.string.error_cannot_get_permission, Toast.LENGTH_LONG).show();
//                break;
//            case CameraError.ERROR_DOES_NOT_HAVE_OVERDRAW_PERMISSION:
//                //Display information dialog to the user with steps to grant "Draw over other app"
//                //permission for the app.
//                HiddenCameraUtils.openDrawOverPermissionSetting(this);
//                break;
//            case CameraError.ERROR_DOES_NOT_HAVE_FRONT_CAMERA:
//                Toast.makeText(this, R.string.error_not_having_camera, Toast.LENGTH_LONG).show();
//                break;
//        }
//
//        stopSelf();
//    }
//
//    private void saveImageToStorage(File imageFile) throws IOException {
//
//
//        Uri contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
//        OutputStream stream = null;
//        final ContentResolver resolver = getApplicationContext().getContentResolver();
//        final String relativeLocation = Environment.DIRECTORY_PICTURES;
//
//        final ContentValues contentValues = new ContentValues();
//        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, imageFile.getName());
//        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
//
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, relativeLocation);
//            contentValues.put(MediaStore.Images.ImageColumns.RELATIVE_PATH, relativeLocation);
//        }
//
//        try {
//            contentUri = resolver.insert(contentUri, contentValues);
//            if (contentUri == null) {
//                throw new IOException("Failed to create new MediaStore record.");
//            }
//
//            stream = resolver.openOutputStream(contentUri);
//
//            if (stream == null) {
//                throw new IOException("Failed to get output stream.");
//            }
//
//        } catch (IOException e) {
//            if (contentUri != null) {
//                resolver.delete(contentUri, null, null);
//            }
//
//            throw new IOException(e);
//
//        } finally {
//            if (stream != null)
//                stream.close();
//        }
//    }
//
//}
