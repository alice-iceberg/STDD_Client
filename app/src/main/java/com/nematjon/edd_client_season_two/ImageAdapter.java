package com.nematjon.edd_client_season_two;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Objects;

public class ImageAdapter extends BaseAdapter {

    private Context context;
    ArrayList<String> imagesPaths;
    File folder = new File(Objects.requireNonNull(context).getExternalFilesDir("Cropped Faces").toString() + File.separator); //getting the app folder



    public ImageAdapter(Context mContext) {
        this.context = mContext;
        imagesPaths = getAllImagesPath(context, folder);
    }

    @Override
    public int getCount() {
        return imagesPaths.size();
    }

    @Override
    public Object getItem(int position) {
        return position;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ImageView picturesView;
        if (convertView == null) {
            picturesView = new ImageView(context);
            picturesView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            picturesView
                    .setLayoutParams(new GridView.LayoutParams(270, 270));

        } else {
            picturesView = (ImageView) convertView;
        }

        RequestOptions options = new RequestOptions()
                .centerCrop()
                .placeholder(R.drawable.ic_launcher_no_bg)
                .dontAnimate()
                .dontTransform();

        Glide.with(context).load(imagesPaths.get(position))
                .apply(options)
                .into(picturesView);

        return picturesView;
    }


    private ArrayList<String> getAllImagesPath (Context activity, File folder){
        ArrayList<String> imagesPaths = new ArrayList<>();

        if(folder.exists()){
          File [] allImages = folder.listFiles(new FilenameFilter() {
              @Override
              public boolean accept(File dir, String name) {
                  if(name.endsWith(".jpg"))
                  imagesPaths.add(Objects.requireNonNull(context).getExternalFilesDir("Cropped Faces").toString() + File.separator + name);
                  return (name.endsWith(".jpg"));
              }
          });
        }
        return imagesPaths;
    }
}
