package com.nematjon.edd_client_season_two;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.navigation.NavigationView;
import com.nematjon.edd_client_season_two.services.MainService;
import com.nematjon.edd_client_season_two.smartwatch.SmartwatchActivity;

import java.util.Objects;


public class LocationSetActivity extends AppCompatActivity implements OnMapReadyCallback, LocationListener, GoogleMap.OnMapClickListener, GoogleMap.OnMarkerClickListener, NavigationView.OnNavigationItemSelectedListener {

    //region Constants
    private static final String TAG = LocationSetActivity.class.getSimpleName();
    public static final String ID_HOME = "HOME";
    public static final String ID_WORK = "WORK";
    public static final String ID_UNIV = "UNIV";
    public static final String ID_LIBRARY = "LIBRARY";
    public static final String ID_ADDITIONAL = "ADDITIONAL";

    private static Intent customSensorsService;


    private String TITLE_HOME;
    private String TITLE_WORK;
    private String TITLE_UNIV;
    private String TITLE_LIBRARY;
    private String TITLE_ADDITIONAL;

    static final StoreLocation[] ALL_LOCATIONS = new StoreLocation[]
            {
                    new StoreLocation(ID_HOME, null),
                    new StoreLocation(ID_WORK, null),
                    new StoreLocation(ID_UNIV, null),
                    new StoreLocation(ID_LIBRARY, null),
                    new StoreLocation(ID_ADDITIONAL, null)
            };

    //endregion

    private GoogleMap mMap;
    private Marker currentMarker;
    private StoreLocation currentStoringLocation;

    private AlertDialog dialog;

    LinearLayout loadingLayout;
    NavigationView navigationView;
    DrawerLayout drawerLayout;
    Toolbar toolbar;

    SharedPreferences configPrefs;

    //region Inner classes
    static class StoreLocation {
        LatLng mLatLng;
        String mId;

        StoreLocation(String id, LatLng latlng) {
            mLatLng = latlng;
            mId = id;
        }

        LatLng getmLatLng() {
            return mLatLng;
        }

        String getmId() {
            return mId;
        }
    }
    //endregion


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_locations_setting);
        configPrefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);

        customSensorsService = new Intent(LocationSetActivity.this, MainService.class);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        navigationView.bringToFront();
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_location);


        //init DbMgr if it's null
        if (DbMgr.getDB() == null)
            DbMgr.init(getApplicationContext());


        loadingLayout = findViewById(R.id.loading_frame);
        loadingLayout.setVisibility(View.VISIBLE);
        loadingLayout.bringToFront();
        Tools.disable_touch(this);

        TITLE_HOME = getString(R.string.set_home_location);
        TITLE_WORK = getString(R.string.set_work_location);
        TITLE_UNIV = getString(R.string.set_university_location);
        TITLE_LIBRARY = getString(R.string.set_library_location);
        TITLE_ADDITIONAL = getString(R.string.set_additional_location);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null)
            mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.e(TAG, "onMapReady: ");

        mMap = googleMap;
        mMap.clear();
        mMap.setOnMapClickListener(this);
        mMap.setOnMarkerClickListener(this);

        for (StoreLocation location : ALL_LOCATIONS) {
            if (getLocationData(getApplicationContext(), location) != null) {
                addLocationMarker(Objects.requireNonNull(getLocationData(getApplicationContext(), location)));
            }
        }

        try {
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager != null) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 20, this);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        markerForGeofence(latLng);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        removeLocation(marker.getTitle());
        return false;
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.e(TAG, "onLocationChanged: ");
        loadingLayout.setVisibility(View.GONE);
        Tools.enable_touch(this);

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        markerForGeofence(latLng);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void markerForGeofence(LatLng latLng) {
        MarkerOptions optionsMarker = new MarkerOptions()
                .position(latLng)
                .title("Current Location");
        if (mMap != null) {
            if (currentMarker != null)
                currentMarker.remove();
            currentMarker = mMap.addMarker(optionsMarker);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17));
        }
    }

    private void addLocationMarker(StoreLocation location) {
        Drawable iconDrawable;
        String location_title;
        switch (location.getmId()) {
            case ID_HOME:
                iconDrawable = ContextCompat.getDrawable(this, R.drawable.home);
                location_title = TITLE_HOME;
                break;
            case ID_WORK:
                iconDrawable = ContextCompat.getDrawable(this, R.drawable.workplace);
                location_title = TITLE_WORK;
                break;
            case ID_UNIV:
                iconDrawable = ContextCompat.getDrawable(this, R.drawable.university);
                location_title = TITLE_UNIV;
                break;
            case ID_LIBRARY:
                iconDrawable = ContextCompat.getDrawable(this, R.drawable.library);
                location_title = TITLE_LIBRARY;
                break;
            case ID_ADDITIONAL:
                iconDrawable = ContextCompat.getDrawable(this, R.drawable.location_marker);
                location_title = TITLE_ADDITIONAL;
                break;
            default:
                iconDrawable = ContextCompat.getDrawable(this, R.mipmap.ic_launcher_no_bg);
                location_title = "My location";
                break;
        }

        assert iconDrawable != null;
        Bitmap iconBmp = ((BitmapDrawable) iconDrawable).getBitmap();
        mMap.addMarker(new MarkerOptions()
                .title(location_title)
                .position(location.getmLatLng())
                .icon(BitmapDescriptorFactory.fromBitmap(iconBmp)));
    }

    static StoreLocation getLocationData(Context context, StoreLocation location) {
        SharedPreferences locationPrefs = context.getSharedPreferences("UserLocations", MODE_PRIVATE);
        float lat = locationPrefs.getFloat(location.getmId() + "_LAT", 0);
        float lng = locationPrefs.getFloat(location.getmId() + "_LNG", 0);
        if (lat != 0 && lng != 0) {
            return new StoreLocation(location.getmId(), new LatLng(lat, lng));
        }
        return null;
    }

    private void setLocation(String locationText, StoreLocation location) {
        final SharedPreferences locationPrefs = getSharedPreferences("UserLocations", MODE_PRIVATE);
        SharedPreferences.Editor editor = locationPrefs.edit();
        editor.putFloat(location.getmId() + "_LAT", (float) location.getmLatLng().latitude);
        editor.putFloat(location.getmId() + "_LNG", (float) location.getmLatLng().longitude);
        editor.apply();
        Toast.makeText(getApplicationContext(), locationText + " " + getString(R.string.location_set), Toast.LENGTH_SHORT).show();


        SharedPreferences confPrefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        int dataSourceId = confPrefs.getInt("LOCATIONS_MANUAL", -1);
        if (dataSourceId != -1) {
            long nowTime = System.currentTimeMillis();
            DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, location.getmId(), (float) location.getmLatLng().latitude, (float) location.getmLatLng().longitude);
        }
        onMapReady(mMap);
    }

    private void removeLocation(String markerTitle) {
        if (markerTitle.equals(TITLE_HOME))
            displayRemoveDialog(ID_HOME);
        else if (markerTitle.equals(TITLE_WORK))
            displayRemoveDialog(ID_WORK);
        else if (markerTitle.equals(TITLE_UNIV))
            displayRemoveDialog(ID_UNIV);
        else if (markerTitle.equals(TITLE_LIBRARY))
            displayRemoveDialog(ID_LIBRARY);
        else if (markerTitle.equals(TITLE_ADDITIONAL))
            displayRemoveDialog(ID_ADDITIONAL);
    }

    public void displayRemoveDialog(final String locationId) {
        final SharedPreferences locationPrefs = getSharedPreferences("UserLocations", MODE_PRIVATE);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.location_remove_confirmation));
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences.Editor editor = locationPrefs.edit();
                editor.remove(locationId + "_LAT");
                editor.remove(locationId + "_LNG");
                editor.apply();
                Toast.makeText(LocationSetActivity.this, getString(R.string.location_removed), Toast.LENGTH_SHORT).show();
                onMapReady(mMap);
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    //region Buttons click listeners
    public void setHomeClick(View view) {
        currentStoringLocation = new StoreLocation(ID_HOME, new LatLng(currentMarker.getPosition().latitude, currentMarker.getPosition().longitude));
        displayDialog(TITLE_HOME, currentStoringLocation);
    }

    public void setWorkClick(View view) {
        currentStoringLocation = new StoreLocation(ID_WORK, new LatLng(currentMarker.getPosition().latitude, currentMarker.getPosition().longitude));
        displayDialog(TITLE_WORK, currentStoringLocation);
    }

    public void setUnivClick(View view) {
        currentStoringLocation = new StoreLocation(ID_UNIV, new LatLng(currentMarker.getPosition().latitude, currentMarker.getPosition().longitude));
        displayDialog(TITLE_UNIV, currentStoringLocation);
    }

    public void setLibraryClick(View view) {
        currentStoringLocation = new StoreLocation(ID_LIBRARY, new LatLng(currentMarker.getPosition().latitude, currentMarker.getPosition().longitude));
        displayDialog(TITLE_LIBRARY, currentStoringLocation);
    }

    public void setAdditionalPlaceClick(View view) {
        currentStoringLocation = new StoreLocation(ID_ADDITIONAL, new LatLng(currentMarker.getPosition().latitude, currentMarker.getPosition().longitude));
        displayDialog(TITLE_ADDITIONAL, currentStoringLocation);
    }
    //endregion

    public void displayDialog(final String locationText, final StoreLocation location) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.location_set_confirmation, locationText));
        builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                setLocation(locationText, location);
            }
        });

        builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }


    @Override
    public void onBackPressed() {

        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
            navigationView.setCheckedItem(R.id.nav_home);

        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.nav_home:
                finish();
                startActivity(new Intent(LocationSetActivity.this, MainActivity.class));
                navigationView.setCheckedItem(R.id.nav_home);
                break;
            case R.id.nav_location:
                break;
            case R.id.nav_sns:
                finish();
                navigationView.setCheckedItem(R.id.nav_sns);
                SharedPreferences instagramPrefs = getSharedPreferences("InstagramPrefs", Context.MODE_PRIVATE);
                boolean isLoggedIn = instagramPrefs.getBoolean("is_logged_in", false);

                if (isLoggedIn) {
                    startActivity(new Intent(LocationSetActivity.this, InstagramLoggedInActivity.class));
                } else {
                    startActivity(new Intent(LocationSetActivity.this, MediaSetActivity.class));
                }
                break;
            case R.id.nav_photos:
                finish();
                startActivity(new Intent(LocationSetActivity.this, CapturedPhotosActivity.class));
                navigationView.setCheckedItem(R.id.nav_photos);
                break;
            case R.id.nav_smartwatch:
                startActivity(new Intent(LocationSetActivity.this, SmartwatchActivity.class));
                navigationView.setCheckedItem(R.id.nav_smartwatch);
                break;
            case R.id.nav_restart:
                navigationView.setCheckedItem(R.id.nav_restart);
                customSensorsService = new Intent(this, MainService.class);
                finish();

                //when the function is called by clicking the button
                stopService(customSensorsService);
                if (!Tools.hasPermissions(this, Tools.PERMISSIONS)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dialog = Tools.requestPermissions(LocationSetActivity.this);
                        }
                    });
                } else {
                    Log.e(TAG, "restartServiceClick: 3");
                    if (configPrefs.getLong("startTimestamp", 0) <= System.currentTimeMillis()) {
                        Log.e(TAG, "RESTART SERVICE");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startActivity(new Intent(LocationSetActivity.this, MainActivity.class));
                            startForegroundService(customSensorsService);
                        } else {
                            startActivity(new Intent(LocationSetActivity.this, MainActivity.class));
                            startService(customSensorsService);
                        }
                    }
                }
                break;

            case R.id.nav_logout:
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
                alertDialog.setMessage(getString(R.string.log_out_confirmation));
                alertDialog.setPositiveButton(
                        getString(R.string.yes), (dialog, which) -> {
                            Tools.perform_logout(getApplicationContext());
                            stopService(LocationSetActivity.customSensorsService);
                            finish();
                        });
                alertDialog.setNegativeButton(
                        getString(R.string.cancel), (dialog, which) -> dialog.cancel());
                alertDialog.show();
                break;
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }
}