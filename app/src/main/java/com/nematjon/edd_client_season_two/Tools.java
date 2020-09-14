package com.nematjon.edd_client_season_two;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.location.LocationManager;

import androidx.core.app.ActivityCompat;

import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;

import com.nematjon.edd_client_season_two.services.MainService;
import com.nematjon.edd_client_season_two.services.KeyLogger;
import com.nematjon.edd_client_season_two.services.NotificationService;

import java.net.InetAddress;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import inha.nsl.easytrack.ETServiceGrpc;
import inha.nsl.easytrack.EtService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import static android.content.Context.MODE_PRIVATE;
import static com.nematjon.edd_client_season_two.EMAActivity.EMA_NOTIF_HOURS;
import static com.nematjon.edd_client_season_two.services.MainService.EMA_RESPONSE_EXPIRE_TIME;

public class Tools {

    private static final String TAG = Tools.class.getSimpleName();

    static final String DATA_SOURCE_SEPARATOR = " ";
    static int PERMISSION_ALL = 1;
    public static String[] PERMISSIONS = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.PROCESS_OUTGOING_CALLS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.CAMERA
    };

    public static boolean canOverDrawOtherApps(Context context) {
        return Settings.canDrawOverlays(context);
    }

    public static boolean hasPermissions(Context con, String... permissions) {
        Context context = con.getApplicationContext();
        if (context != null && permissions != null)
            for (String permission : permissions)
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED)
                    return false;

        assert context != null;
        if (isAppUsageAccessNotGranted(context))
            return false;

        if (isAccessibilityServiceNotEnabled(context))
            return false;

        if (isNotificationServiceNotEnabled(context))
            return false;

        if (!canOverDrawOtherApps(context))
            return false;

        return isGPSLocationOn(context);
    }

    private static boolean isGPSLocationOn(Context con) {
        LocationManager lm = (LocationManager) con.getSystemService(Context.LOCATION_SERVICE);
        boolean gps_enabled;
        boolean network_enabled;
        gps_enabled = Objects.requireNonNull(lm).isProviderEnabled(LocationManager.GPS_PROVIDER);
        network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        return gps_enabled || network_enabled;
    }

    private static boolean isAppUsageAccessNotGranted(Context con) {
        try {
            PackageManager packageManager = con.getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(con.getPackageName(), 0);
            AppOpsManager appOpsManager = (AppOpsManager) con.getSystemService(Context.APP_OPS_SERVICE);
            int mode = Objects.requireNonNull(appOpsManager).checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, applicationInfo.uid, applicationInfo.packageName);
            return (mode != AppOpsManager.MODE_ALLOWED);

        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    private static boolean isAccessibilityServiceNotEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabledServices;
        if (am != null) {
            enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            for (AccessibilityServiceInfo enabledService : enabledServices) {
                ServiceInfo enabledServiceInfo = enabledService.getResolveInfo().serviceInfo;
                if (enabledServiceInfo.packageName.equals(context.getPackageName()) && enabledServiceInfo.name.equals(KeyLogger.class.getName()))
                    return false;
            }
        }
        return true;
    }

    private static boolean isNotificationServiceNotEnabled(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (NotificationService.class.getName().equals(service.service.getClassName())) {
                    return false;
                }
            }
        }
        return true;
    }

    public static AlertDialog requestPermissions(final Activity activity) {
        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.permissions))
                .setMessage(activity.getString(R.string.grant_permissions))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Tools.grantPermissions(activity, PERMISSIONS);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);
        return alertDialog.show();
    }

    private static void grantPermissions(Activity activity, String... permissions) {
        boolean simple_permissions_granted = true;
        for (String permission : permissions)
            if (ActivityCompat.checkSelfPermission(activity.getApplicationContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                simple_permissions_granted = false;
                break;
            }

        if (isAppUsageAccessNotGranted(activity.getApplicationContext())) {
            activity.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Log.e(TAG, "grantPermissions: APP usage");
        }
        if (!isGPSLocationOn(activity.getApplicationContext())) {
            activity.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Log.e(TAG, "grantPermissions: GPS");
        }
        if (!Settings.canDrawOverlays(activity.getApplicationContext())) {
            Log.e(TAG, "grantPermissions: OVERLAY");
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        if (isAccessibilityServiceNotEnabled(activity.getApplicationContext())) {
            Log.e(TAG, "grantPermissions: ACCESSABILITY");
            activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        if (isNotificationServiceNotEnabled(activity.getApplicationContext())) {
            Log.e(TAG, "grantPermissions: APP NOTIF");
            activity.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }

        if (!simple_permissions_granted) {
            Log.e(TAG, "grantPermissions: SIMPLE");
            ActivityCompat.requestPermissions(activity, PERMISSIONS, PERMISSION_ALL);
        }
    }

    public static void checkAndSaveUsageAccessStats(Context con) {
        // Init AppUseDb if it's null
        if (AppUseDb.getDB() == null)
            AppUseDb.init(con);


        SharedPreferences loginPrefs = con.getSharedPreferences("UserLogin", MODE_PRIVATE);
        long lastSavedTimestamp = loginPrefs.getLong("lastUsageSubmissionTime", -1);

        Calendar fromCal = Calendar.getInstance();
        if (lastSavedTimestamp == -1)
            fromCal.add(Calendar.DAY_OF_WEEK, -2);
        else
            fromCal.setTime(new Date(lastSavedTimestamp));

        final Calendar tillCal = Calendar.getInstance();
        tillCal.set(Calendar.MILLISECOND, 0);

        PackageManager localPackageManager = con.getPackageManager();
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.addCategory("android.intent.category.HOME");
        String launcher_packageName = Objects.requireNonNull(localPackageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)).activityInfo.packageName;

        UsageStatsManager usageStatsManager = (UsageStatsManager) con.getSystemService(Context.USAGE_STATS_SERVICE);
        List<UsageStats> stats = Objects.requireNonNull(usageStatsManager).queryUsageStats(UsageStatsManager.INTERVAL_BEST, fromCal.getTimeInMillis(), System.currentTimeMillis());
        for (UsageStats usageStats : stats)
            //do not include launcher's package name
            if (usageStats.getTotalTimeInForeground() > 0 && !usageStats.getPackageName().contains(launcher_packageName))
                AppUseDb.saveAppUsageStat(usageStats.getPackageName(), usageStats.getLastTimeUsed(), usageStats.getTotalTimeInForeground());

        SharedPreferences.Editor editor = loginPrefs.edit();
        editor.putLong("lastUsageSubmissionTime", tillCal.getTimeInMillis());
        editor.apply();
    }

    public static void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static void enable_touch(Activity activity) {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    static void disable_touch(Activity activity) {
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    private static boolean isReachable;

    public static boolean isNetworkAvailable() {
        try {
            Thread thread = new Thread(() -> {
                try {
                    InetAddress address = InetAddress.getByName("www.google.com");
                    isReachable = !address.toString().equals("");
                } catch (Exception e) {
                    e.printStackTrace();
                    isReachable = false;
                }
            });
            thread.start();
            thread.join();
        } catch (Exception e) {
            e.printStackTrace();
            isReachable = false;
        }

        return isReachable;
    }

    static boolean isMainServiceRunning(Context con) {
        ActivityManager manager = (ActivityManager) con.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : Objects.requireNonNull(manager).getRunningServices(Integer.MAX_VALUE)) {
            if (MainService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static synchronized void sendHeartbeat(final Context con) {
        final SharedPreferences loginPrefs = con.getSharedPreferences("UserLogin", MODE_PRIVATE);
        if (Tools.isNetworkAvailable()) {
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    ManagedChannel channel = ManagedChannelBuilder.forAddress(
                            con.getString(R.string.grpc_host),
                            Integer.parseInt(con.getString(R.string.grpc_port))
                    ).usePlaintext().build();
                    ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
                    EtService.SubmitHeartbeat.Request submitHeartbeatRequest = EtService.SubmitHeartbeat.Request.newBuilder()
                            .setUserId(loginPrefs.getInt(AuthActivity.user_id, -1))
                            .setEmail(loginPrefs.getString(AuthActivity.usrEmail, null))
                            .setCampaignId(Integer.parseInt(con.getString(R.string.campaign_id)))
                            .build();
                    try {
                        EtService.SubmitHeartbeat.Response responseMessage = stub.submitHeartbeat(submitHeartbeatRequest);
                        if (responseMessage.getSuccess()) {
                            Log.d("Tools", "Heartbeat sent successfully");
                        }
                    } catch (StatusRuntimeException e) {
                        Log.e("Tools", "DataCollectorService.setUpHeartbeatSubmissionThread() exception: " + e.getMessage());
                        e.printStackTrace();
                    } finally {
                        channel.shutdown();
                    }
                }
            }.start();
        }
    }

    static int getEMAOrderFromRangeAfterEMA(Calendar cal) {
        long t = (cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND)) * 1000;
        for (int i = 0; i < EMA_NOTIF_HOURS.length; i++)
            if ((EMA_NOTIF_HOURS[i] * 3600 * 1000) <= t && t <= (EMA_NOTIF_HOURS[i] * 3600 * 1000) + EMA_RESPONSE_EXPIRE_TIME * 1000)
                return i + 1;

        return 0;
    }

    public static void perform_logout(Context con) {

        SharedPreferences loginPrefs = con.getSharedPreferences("UserLogin", MODE_PRIVATE);
        SharedPreferences locationPrefs = con.getSharedPreferences("UserLocations", MODE_PRIVATE);
        SharedPreferences instagramPrefs = con.getSharedPreferences("InstagramPrefs", MODE_PRIVATE);

        SharedPreferences.Editor editorLocation = locationPrefs.edit();
        editorLocation.clear();
        editorLocation.apply();

        SharedPreferences.Editor editorLogin = loginPrefs.edit();
        editorLogin.remove("username");
        editorLogin.remove("password");
        editorLogin.putBoolean("logged_in", false);
        editorLogin.remove("ema_btn_make_visible");
        editorLogin.clear();
        editorLogin.apply();

        SharedPreferences.Editor editorInstagram = instagramPrefs.edit();
        editorInstagram.remove("instagram_username");
        editorInstagram.remove("instagram_password");
        editorInstagram.putBoolean("is_logged_in", false);
        editorInstagram.clear();
        editorInstagram.apply();
    }

    static String formatMinutes(int minutes) {
        if (minutes > 60) {
            if (minutes > 1440)
                return minutes / 60 / 24 + "days";
            else {
                int h = minutes / 60;
                float dif = (float) minutes / 60 - h;
                //Toast.makeText(MainActivity.this, dif + "", Toast.LENGTH_SHORT).show();
                int m = (int) (dif * 60);
                return h + "h " + m + "m";
            }
        } else
            return minutes + "m";
    }

    public static boolean inRange(long value, long start, long end) {
        return start < value && value < end;
    }

    public static Bitmap rotateBitmap(Bitmap inputBitmap, float degrees) {
        Bitmap outputBitmap;
        Matrix matrix = new Matrix();
        matrix.setRotate(degrees);
        outputBitmap = Bitmap.createBitmap(inputBitmap, 0, 0, inputBitmap.getWidth(), inputBitmap.getHeight(), matrix, true);

        return outputBitmap;
    }


}
