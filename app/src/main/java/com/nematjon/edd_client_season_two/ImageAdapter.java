package com.nematjon.edd_client_season_two;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

public class ImageAdapter extends BaseAdapter {

    private Context mContext;
    ArrayList<File> takenPhotos = new ArrayList<File>();
    File folder = new File(Objects.requireNonNull(mContext).getExternalFilesDir("Cropped Faces").toString() + File.separator); //getting the app folder


    public ImageAdapter(Context mContext, ArrayList<File> takenPhotos) {
        this.mContext = mContext;
        this.takenPhotos = takenPhotos;
    }

    @Override
    public int getCount() {
        return 0;
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        return null;
    }
}
