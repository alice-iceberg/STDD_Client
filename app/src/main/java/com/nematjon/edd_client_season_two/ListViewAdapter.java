package com.nematjon.edd_client_season_two;

import android.view.View;
import android.view.ViewGroup;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.List;

public class ListViewAdapter extends ArrayAdapter<Permisssion>{


List<Permisssion>  permisssions;
Context myContext;
   public ListViewAdapter(Context context, int resource, List<Permisssion> objects) {
        super(context, resource, objects);
        permisssions = objects;
        myContext = context;

    }
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View listItemView = convertView;
        //Person currentPerson = personList.get(position);
        if(listItemView == null){
            listItemView = LayoutInflater.from(getContext()).inflate(R.layout.custom_permission_item, parent, false);
        }
        TextView permissionText = listItemView.findViewById(R.id.permission_textView);
        TextView permissionContent = listItemView.findViewById(R.id.description);
      ImageView imageView = listItemView.findViewById(R.id.permissionIcon);

        permissionText.setText(permisssions.get(position).getPermission());
        permissionContent.setText(permisssions.get(position).getContent());
        if(position == 0)   imageView.setBackground(ContextCompat.getDrawable( myContext, R.drawable.location_icon));
        if(position == 1)   imageView.setBackground(ContextCompat.getDrawable( myContext, R.drawable.ic_baseline_camera_alt_24));
        if(position == 2)   imageView.setBackground(ContextCompat.getDrawable( myContext, R.drawable.ic_baseline_contact_page_24));
        if(position == 3)   imageView.setBackground(ContextCompat.getDrawable( myContext, R.drawable.ic_baseline_wifi_24));
        if(position == 4)   imageView.setBackground(ContextCompat.getDrawable( myContext, R.drawable.ic_baseline_audiotrack_24));
        if(position == 5)   imageView.setBackground(ContextCompat.getDrawable( myContext, R.drawable.ic_baseline_sms_24));
        if(position == 6)   imageView.setBackground(ContextCompat.getDrawable( myContext, R.drawable.ic_baseline_data_usage_24));
        if(position == 7)  imageView.setBackground(ContextCompat.getDrawable(myContext, R.drawable.ic_baseline_sd_storage_24));
        return listItemView;
    }
}