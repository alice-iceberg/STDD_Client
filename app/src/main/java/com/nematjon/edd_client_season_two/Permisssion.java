package com.nematjon.edd_client_season_two;

import android.graphics.drawable.Drawable;

public class Permisssion {
   private  String permission;
   private  String  content;
   private Drawable drawable;




   public  Permisssion(String  permission,  String  content){
       this.permission = permission;
       this.content = content;
   }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Drawable getDrawable() {
        return drawable;
    }

    public void setDrawable(Drawable drawable) {
        this.drawable = drawable;
    }
}
