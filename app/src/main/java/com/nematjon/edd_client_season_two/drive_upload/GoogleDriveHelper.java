package com.nematjon.edd_client_season_two.drive_upload;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


public class GoogleDriveHelper {
    public static Executor executor = Executors.newSingleThreadExecutor();
    private Drive drive;


    public GoogleDriveHelper(Drive drive) {

        this.drive = drive;
    }

    public Task<String> createDBFile(Context context, String filePath) {
        return Tasks.call(executor, () -> {
            File file = new File();

            SharedPreferences loginPref = context.getSharedPreferences("UserLogin", Context.MODE_PRIVATE);

            file.setName(loginPref.getString("fullname", null) + System.currentTimeMillis());

            java.io.File file1 = new java.io.File(filePath);

            FileContent content = new FileContent("application/db", file1);


            File myFile = null;

            try {
                myFile = drive.files().create(file, content).execute();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (myFile == null) {
                throw new IOException("File is null!");
            }


           String fileId = myFile.getId();
            JsonBatchCallback<Permission> callback  = new JsonBatchCallback<Permission>() {
                @Override
                public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
                    Log.d("TAG", "onFailure: " + e.getMessage());
                }
                @Override
                public void onSuccess(Permission permission, HttpHeaders responseHeaders) {
                    Log.d("TAG", "onSuccess: "  + permission.getDisplayName());
                }
            };


            //give permission to the user
            BatchRequest batchRequest = drive.batch();
            Permission userPermission = new Permission().setType("user").setRole("writer").setEmailAddress("aliceblackwood123@gmail.com");
            drive.permissions().create(fileId,  userPermission).setFields("id").queue(batchRequest,  callback);
            batchRequest.execute();

            return myFile.getId();
        });

    }

}
