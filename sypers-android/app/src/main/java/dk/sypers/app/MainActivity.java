package dk.sypers.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Build;
import android.app.role.RoleManager;
import android.content.Intent;
import android.app.AlertDialog;
import android.provider.ContactsContract;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;

public class MainActivity extends Activity {
    private static final int CONTACTS_PERMISSION_REQUEST = 1001;
    private static final String APP_URL = "file:///android_asset/sypers_login.html";
    private WebView webView;
    private LocationBridge locationBridge;
    private boolean pendingLocationStart;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return openExternalUrl(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return openExternalUrl(request.getUrl().toString());
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setSaveFormData(true);
        webView.addJavascriptInterface(new ContactsBridge(), "SypersContacts");
        webView.addJavascriptInterface(new SettingsBridge(), "SypersSettings");
        locationBridge = new LocationBridge();
        webView.addJavascriptInterface(locationBridge, "SypersLocation");
        setContentView(webView);
        requestContactsPermission();
        requestDefaultDialerRole();
        webView.loadUrl(APP_URL);
    }

    private boolean openExternalUrl(String url) {
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if ("tel".equalsIgnoreCase(scheme)) {
            startActivity(new Intent(Intent.ACTION_DIAL, uri));
            return true;
        }
        if ("mailto".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)
                || "http".equalsIgnoreCase(scheme)) {
            if (url.startsWith("file:///android_asset/")) return false;
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
            return true;
        }
        return false;
    }

    private void requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager != null
                    && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
                    && !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
                startActivityForResult(intent, 1002);
            }
        }
    }

    private void requestDefaultDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                    requestCallScreeningRole();
                } else {
                    startActivityForResult(
                            roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER), 1004);
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1002) {
            return;
        }
        if (requestCode == 1004) {
            requestCallScreeningRole();
        }
    }

    private void requestContactsPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, CONTACTS_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CONTACTS_PERMISSION_REQUEST && webView != null) {
            webView.evaluateJavascript(
                    "window.dispatchEvent(new Event('sypersContactsPermission'))", null);
        }
        if (requestCode == 1003 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && locationBridge != null) {
            locationBridge.start();
        }
        if (requestCode == 1005 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && pendingLocationStart) {
            pendingLocationStart = false;
            locationBridge.start();
        }
    }

    private class ContactsBridge {
        @JavascriptInterface
        public void requestPermission() {
            runOnUiThread(MainActivity.this::requestContactsPermission);
        }

        @JavascriptInterface
        public String getContacts() {
            JSONArray contacts = new JSONArray();
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return contacts.toString();
            }

            try (Cursor cursor = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    },
                    null,
                    null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {
                if (cursor == null) return contacts.toString();
                int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                while (cursor.moveToNext()) {
                    JSONObject contact = new JSONObject();
                    contact.put("name", cursor.getString(nameIndex));
                    contact.put("number", cursor.getString(numberIndex));
                    contacts.put(contact);
                }
            } catch (SecurityException | org.json.JSONException ignored) {
                return new JSONArray().toString();
            }
            return contacts.toString();
        }

        @JavascriptInterface
        public void syncBlockedNumbers(String numbersJson) {
            try {
                JSONArray numbers = new JSONArray(numbersJson);
                HashSet<String> blocked = new HashSet<>();
                for (int index = 0; index < numbers.length(); index++) {
                    blocked.add(numbers.getString(index).replaceAll("\\D", ""));
                }
                getSharedPreferences("sypers", MODE_PRIVATE)
                        .edit()
                        .putStringSet("blockedNumbers", blocked)
                        .apply();
            } catch (org.json.JSONException ignored) {
                // Ignore malformed data from the web layer.
            }
        }

    }

    private class LocationBridge {
        @JavascriptInterface
        public void start() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 23
                        && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, 1003);
                    return;
                }
                if (!getSharedPreferences("sypers_settings", MODE_PRIVATE)
                        .getBoolean("gps_pair_confirmed_1_4_9", false)) {
                    requestPairingCode();
                    return;
                }
                startService();
            });
        }

        private void requestPairingCode() {
            new Thread(() -> {
                try {
                    java.net.HttpURLConnection connection = (java.net.HttpURLConnection)
                            new java.net.URL("https://catnap-frill-jolliness.ngrok-free.dev/api/pair/start").openConnection();
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.getOutputStream().write(("{\"deviceKey\":\"sypers-device-2026\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(connection.getInputStream(),
                                    java.nio.charset.StandardCharsets.UTF_8));
                    StringBuilder responseBody = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBody.append(line);
                    }
                    String body = responseBody.toString();
                    String code = new org.json.JSONObject(body).getString("code");
                    runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Parr Syper Family")
                            .setMessage("Skriv denne kode i Syper Family:\n\n" + code)
                            .setNegativeButton("Annuller", null)
                            .setPositiveButton("Fortsæt", (dialog, which) -> {
                                getSharedPreferences("sypers_settings", MODE_PRIVATE).edit()
                                        .putBoolean("gps_pair_confirmed_1_4_9", true).apply();
                                startService();
                            }).show());
                } catch (Exception error) {
                    runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Parring mislykkedes")
                            .setMessage("Kunne ikke oprette parringskode.")
                            .setPositiveButton("OK", null).show());
                }
            }).start();
        }

        private void startService() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    pendingLocationStart = true;
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1005);
                    return;
                }
                startLocationService();
            });
        }

        private void startLocationService() {
            Intent intent = new Intent(MainActivity.this, SypersLocationService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                MainActivity.this.startForegroundService(intent);
            } else {
                MainActivity.this.startService(intent);
            }
        }

        @JavascriptInterface
        public void stop() {
            Intent intent = new Intent(MainActivity.this, SypersLocationService.class);
            intent.setAction("stop");
            MainActivity.this.startService(intent);
        }
    }

    private class SettingsBridge {
        @JavascriptInterface
        public String get(String key) {
            return getSharedPreferences("sypers_settings", MODE_PRIVATE)
                    .getString(key, null);
        }

        @JavascriptInterface
        public void set(String key, String value) {
            getSharedPreferences("sypers_settings", MODE_PRIVATE)
                    .edit()
                    .putString(key, value)
                    .apply();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
