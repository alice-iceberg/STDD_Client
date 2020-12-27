package com.nematjon.edd_client_season_two;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

public class StoredMedia {
    int total_num_of_images = 0;
    int total_num_of_video_files = 0;
    int total_num_of_music = 0;

    public int totalNumberOfImages(ContentResolver CR) {

        String[] projection = {MediaStore.Images.ImageColumns.DATA};
        Uri root;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            root = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            root = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }

        Cursor c = CR.query(root, projection, null, null, MediaStore.Images.Media.DATE_ADDED + " desc");
        if(c!=null) {
            total_num_of_images = c.getCount();
            c.close();
        }else{
            total_num_of_images = 0;
        }

        return total_num_of_images;
    }

    public int totalNumberOfVideoFiles(ContentResolver CR) {

        String[] projection = {MediaStore.Video.VideoColumns.DATA};

        Uri root;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            root = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            root = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        }

        Cursor c = CR.query(root, projection, null, null, MediaStore.Video.Media.DATE_ADDED + " desc");
        if(c!=null) {
            total_num_of_video_files = c.getCount();
            c.close();
        }else{
            total_num_of_video_files = 0;
        }

        return total_num_of_video_files;
    }

    public int totalNumOfMusic (ContentResolver CR){
        Uri songUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";

        Cursor songCursor = CR.query(songUri, null, selection, null, null);

        if(songCursor!=null) {
            total_num_of_music = songCursor.getCount();
            songCursor.close();
        }else{
            total_num_of_music = 0;
        }
        return total_num_of_music;
    }

}
