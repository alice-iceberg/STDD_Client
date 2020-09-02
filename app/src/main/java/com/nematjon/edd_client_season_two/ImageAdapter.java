package com.nematjon.edd_client_season_two;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;
import java.util.ArrayList;

public class ImageAdapter extends BaseAdapter {

    private Context context;
    ArrayList<String> imagesPaths;

    public ImageAdapter(Context mContext) {
        this.context = mContext;
        this.imagesPaths = getAllImagesPath(mContext);
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


    private ArrayList<String> getAllImagesPath(Context activity) {
        ArrayList<String> imagesPaths = new ArrayList<>();
        String path = context.getExternalFilesDir("Cropped Faces") + File.separator;
        File folder = new File(path); //getting the app folder

        if (folder.exists()) {
            File[] allImages = folder.listFiles();

            for (int i = 0; i < allImages.length; i++) {
              imagesPaths.add(folder.toString() + File.separator + allImages[i].getName());
                Log.e("TAG", "getAllImagesPath: Name of the file:" + imagesPaths.get(i) );
            }
        }else {
            Log.e("TAG", "getAllImagesPath: Folder does not exist");
        }
        return imagesPaths;
    }
}
