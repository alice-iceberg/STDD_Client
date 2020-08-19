package com.nematjon.edd_client_season_two;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;

public class ImageAdapter extends BaseAdapter {

    private Context mContext;
    ArrayList<File> takenPhotos = new ArrayList<File>();
    File folder = new File(mContext.getExternalFilesDir("Cropped Faces") + File.separator); //getting the app folder
    File[] files = folder.listFiles();


    public ImageAdapter(Context mContext) {
        this.mContext = mContext;
    }


    @Override
    public int getCount() {
        return takenPhotos.size();
    }

    @Override
    public Object getItem(int position) {
        for (File file : files) {
            if (!file.isDirectory()) {
                takenPhotos.add(file);
            }

        }

        return takenPhotos.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View view, ViewGroup viewGroup) {

        ImageView imageView = new ImageView(mContext);
        imageView.setImageURI(Uri.fromFile(takenPhotos.get(position)));
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setLayoutParams(new GridView.LayoutParams(340, 350));

        return imageView;
    }
}
