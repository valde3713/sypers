package dk.sypers.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SypersLocationService extends Service {
    private static final String CHANNEL_ID = "sypers_location";
    private static final int NOTIFICATION_ID = 1401;
    private static final String TRACKING_URL = "https://catnap-frill-jolliness.ngrok-free.dev/api/location";
    private static final String DEVICE_KEY = "sypers-device-2026";
    private LocationManager locationManager;
    private final LocationListener listener = this::sendLocation;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "stop".equals(intent.getAction())) {
            stopTracking();
            stopSelf();
            return START_NOT_STICKY;
        }
        createChannel();
        startForeground(NOTIFICATION_ID, notification());
        startTracking();
        return START_STICKY;
    }

    private void startTracking() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) return;
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000, 25, listener);
            Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last != null) sendLocation(last);
        } catch (SecurityException ignored) {
            stopSelf();
        }
    }

    private void stopTracking() {
        if (locationManager != null) locationManager.removeUpdates(listener);
    }

    private void sendLocation(Location location) {
        new Thread(() -> {
            try {
                Intent batteryIntent = registerReceiver(null,
                        new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int battery = batteryIntent == null ? -1 : batteryIntent.getIntExtra(
                        BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryIntent == null ? 100 : batteryIntent.getIntExtra(
                        BatteryManager.EXTRA_SCALE, 100);
                int batteryPercent = scale > 0 && battery >= 0 ? Math.round(battery * 100f / scale) : -1;
                boolean charging = batteryIntent != null
                        && batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        == BatteryManager.BATTERY_STATUS_CHARGING;
                HttpURLConnection connection = (HttpURLConnection) new URL(TRACKING_URL).openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("Content-Type", "application/json");
                String payload = "{\"deviceKey\":\"" + DEVICE_KEY
                        + "\",\"pairingToken\":\"" + getSharedPreferences("sypers_settings", MODE_PRIVATE)
                        .getString("gps_pairing_token", "") + "\",\"latitude\":" + location.getLatitude()
                        + ",\"longitude\":" + location.getLongitude()
                        + ",\"accuracy\":" + location.getAccuracy()
                        + ",\"battery\":" + batteryPercent
                        + ",\"charging\":" + charging + "}";
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(payload.getBytes(StandardCharsets.UTF_8));
                }
                connection.getResponseCode();
                connection.disconnect();
            } catch (Exception ignored) {
                // The next location update retries when the phone is offline.
            }
        }).start();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Sypers GPS", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification notification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder.setContentTitle("Sypers GPS")
                .setContentText("GPS-sporing er aktiv")
                .setSmallIcon(R.drawable.ic_sypers)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        stopTracking();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
