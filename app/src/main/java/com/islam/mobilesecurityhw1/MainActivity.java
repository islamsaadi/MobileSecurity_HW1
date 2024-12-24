package com.islam.mobilesecurityhw1;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.*;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // ======= ADJUSTABLE PARAMETERS =======
    private static final String PASSWORD_PREFIX = "10s20w30q";
    private static final float DISTANCE_THRESHOLD = 5000f; // 5 km
    private static final double ARRABAH_LAT = 32.85254314059482;
    private static final double ARRABAH_LNG = 35.33675279027549;
    private static final double TEL_AVIV_LAT = 32.08684812926745;
    private static final double TEL_AVIV_LNG = 34.7895403545493;
    // =====================================

    private EditText passwordField;
    private Button loginButton;

    private FusedLocationProviderClient fusedLocationClient;
    private SettingsClient settingsClient;
    private Location currentLocation;

    // Permissions
    private boolean locationPermissionGranted = false;
    // Track if we have requested fine location permission once automatically (silent request)
    private boolean finePermissionRequestedOnce = false;

    private ActivityResultLauncher<String> requestPermissionLauncher;

    // Sensors
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    private float[] gravity;
    private float[] geomagnetic;
    private boolean isDeviceFlat = false;
    private boolean isDevicePointingNorth = false;

    private LocationSettingsRequest locationSettingsRequest;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        passwordField = findViewById(R.id.password_field);
        loginButton = findViewById(R.id.login_button);

        // Initialize location & sensor managers
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        settingsClient = LocationServices.getSettingsClient(this);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer  = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        // Prepare the permission request launcher
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    // Called when the user responds to the permission dialog
                    if (isGranted) {
                        locationPermissionGranted = true;
                        checkLocationSettings();
                    } else {
                        // User denied. We'll interpret final steps in handleLocationPermissionOnLoginAttempt()
                        locationPermissionGranted = false;
                    }
                }
        );

        // Attempt login on button click
        loginButton.setOnClickListener(view -> attemptLogin());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register sensors
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        }

        // Check permission state silently on every resume
        checkPermissionsStateSilently();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister sensors
        sensorManager.unregisterListener(this);
    }

    /**
     * If we've never requested FINE location before, do a one-time silent request.
     */
    private void checkPermissionsStateSilently() {
        int fineStatus = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        if (fineStatus == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true;
            checkLocationSettings();
        } else {
            locationPermissionGranted = false;
            if (!finePermissionRequestedOnce) {
                finePermissionRequestedOnce = true;
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }
    }

    /**
     * The main login logic: if location is not granted, handle permission.
     * Otherwise, do all checks: brightness, charging, WiFi, etc.
     */
    private void attemptLogin() {
        // If location not granted, handle that first
        if (!locationPermissionGranted) {
            handleLocationPermissionOnLoginAttempt();
            return;
        }

        // Location is granted => proceed with checks
        if (!isBrightnessSufficient()) {
            showErrorDialog("Screen brightness must be at least 50%.");
            return;
        }

        String password = passwordField.getText().toString();
        if (!isPasswordValid(password)) {
            showErrorDialog("Password must contain the sum of your battery digits (e.g., battery=87 => sum=15).");
            return;
        }

        if (!isWifiConnected()) {
            showErrorDialog("You must be connected to a WiFi network.");
            return;
        }

        if (!isDeviceCharging()) {
            showErrorDialog("Device must be charging.");
            return;
        }

        if (!isNearRequiredLocation()) {
            showErrorDialog("You must be near Arrabah or Tel Aviv.");
            return;
        }

        if (!isDeviceFlat) {
            showErrorDialog("Device must be lying flat.");
            return;
        }

        if (!isDevicePointingNorth) {
            showErrorDialog("Device must be pointing north.");
            return;
        }

        // All conditions met
        Toast.makeText(this, "Login Successful!", Toast.LENGTH_LONG).show();
    }

    /**
     * If location is not granted, we check for approximate vs. no permission at all.
     * Then decide whether to:
     *  - Show rationale (if true)
     *  - Go to final instructions (if rationale is false => "Don't Ask Again")
     */
    private void handleLocationPermissionOnLoginAttempt() {
        // Check coarse & fine status
        int fineStatus   = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        int coarseStatus = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION);

        // If user has fine => all good
        if (fineStatus == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true;
            attemptLogin();
            return;
        }

        // If user only has approximate (coarse) but not fine
        if (coarseStatus == PackageManager.PERMISSION_GRANTED && fineStatus != PackageManager.PERMISSION_GRANTED) {
            // They only have approximate location => ask them for precise
            showPreciseLocationRequiredDialog();
            return;
        }

        // If user has no fine, check rationale
        boolean shouldRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION);

        if (shouldRationale) {
            // The user denied once but did not choose "Don't Ask Again"
            showRationaleDialog();
        } else {
            // The user has effectively chosen "Don't Ask Again" or second denial with no rationale
            // => show final instructions
            showFinalInstructionsScreen();
        }
    }

    /**
     * Dialog explaining why we need precise location; user can accept or exit.
     */
    private void showRationaleDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Location Permission Needed")
                .setMessage("This app requires precise location to proceed. Please grant it now.")
                .setCancelable(false)
                .setPositiveButton("Grant", (dialog, which) ->
                        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                )
                .setNegativeButton("Exit", (dialog, which) -> finish())
                .show();
    }

    /**
     * If user has only approximate location, prompt them to enable precise location.
     */
    private void showPreciseLocationRequiredDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Precise Location Required")
                .setMessage("You've only granted approximate location. Please enable 'Use precise location' in the app's permissions.")
                .setCancelable(false)
                .setPositiveButton("Request Again", (dialog, which) -> {
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                })
                .setNegativeButton("Settings", (dialog, which) -> {
                    // Direct user to app settings
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .show();
    }

    /**
     * If user denied with "Don't Ask Again," we show a final instructions screen (or dialog)
     * telling them to enable permissions manually.
     */
    private void showFinalInstructionsScreen() {
        // Could be a dialog, or a separate activity. Example: open a new activity:
        startActivity(new Intent(this, FinalPermissionActivity.class));
        finish();
    }

    /**
     * Checks if the user meets location settings like GPS high-accuracy.
     */
    private void checkLocationSettings() {
        if (!locationPermissionGranted) return;

        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationSettingsRequest = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .build();

        SettingsClient client = LocationServices.getSettingsClient(this);
        client.checkLocationSettings(locationSettingsRequest)
                .addOnSuccessListener(response -> getLocationUpdate())
                .addOnFailureListener(e -> {
                    if (e instanceof ResolvableApiException) {
                        try {
                            ResolvableApiException resolvable = (ResolvableApiException) e;
                            resolvable.startResolutionForResult(MainActivity.this, 1001);
                        } catch (ActivityNotFoundException ex) {
                            Toast.makeText(MainActivity.this, "Please enable GPS manually.", Toast.LENGTH_SHORT).show();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    } else {
                        showGPSDisabledDialog();
                    }
                });
    }

    private void showGPSDisabledDialog() {
        new AlertDialog.Builder(this)
                .setTitle("GPS Required")
                .setMessage("GPS is disabled. Please enable it to proceed.")
                .setCancelable(false)
                .setPositiveButton("Enable GPS", (dialog, which) ->
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                )
                .setNegativeButton("Exit", (dialog, which) -> finish())
                .show();
    }

    private void getLocationUpdate() {
        if (!locationPermissionGranted) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        currentLocation = location;
                    } else {
                        requestNewLocationData();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(MainActivity.this, "Failed to get location.", Toast.LENGTH_SHORT).show()
                );
    }

    private void requestNewLocationData() {
        if (!locationPermissionGranted) return;

        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10000);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);
            if (locationResult.getLastLocation() != null) {
                currentLocation = locationResult.getLastLocation();
                fusedLocationClient.removeLocationUpdates(this);
            }
        }
    };

    // ====== Checks: Brightness, Password, Battery, WiFi, etc. =======

    private boolean isBrightnessSufficient() {
        try {
            ContentResolver cr = getContentResolver();
            int brightness = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS);
            return brightness >= 128; // ~50% of 255
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * The password must be PASSWORD_PREFIX + the sum of the digits of current battery level.
     * e.g. battery=87 => sum=15 => correct password = "10s20w30q15"
     */
    private boolean isPasswordValid(String inputPassword) {
        int sumBatteryDigits = sumOfBatteryDigits();
        String correctPassword = PASSWORD_PREFIX + sumBatteryDigits;
        return inputPassword.equals(correctPassword);
    }

    private int sumOfBatteryDigits() {
        int batteryLevel = getBatteryLevel();
        int sum = 0;
        while (batteryLevel > 0) {
            sum += batteryLevel % 10;
            batteryLevel /= 10;
        }
        return sum;
    }

    private int getBatteryLevel() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    private boolean isWifiConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = cm != null ? cm.getActiveNetworkInfo() : null;
        return info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI;
    }

    /**
     * Use a broadcast check for charging or full
     */
    private boolean isDeviceCharging() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        if (batteryStatus == null) {
            return false;
        }
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    private boolean isNearRequiredLocation() {
        if (currentLocation == null) {
            getLocationUpdate();
            return false;
        }

        Location arrabah = new Location("Arrabah");
        arrabah.setLatitude(ARRABAH_LAT);
        arrabah.setLongitude(ARRABAH_LNG);

        Location telAviv = new Location("TelAviv");
        telAviv.setLatitude(TEL_AVIV_LAT);
        telAviv.setLongitude(TEL_AVIV_LNG);

        float distArrabah = currentLocation.distanceTo(arrabah);
        float distTelAviv = currentLocation.distanceTo(telAviv);

        return (distArrabah <= DISTANCE_THRESHOLD) || (distTelAviv <= DISTANCE_THRESHOLD);
    }

    private void showErrorDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Login Error")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    // ===== Sensor handling for flatness & direction =====
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity = event.values.clone();
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = event.values.clone();
        }

        if (gravity != null && geomagnetic != null) {
            float[] R = new float[9];
            float[] I = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, gravity, geomagnetic);
            if (success) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(R, orientation);

                float azimuth = (float) Math.toDegrees(orientation[0]);
                float pitch   = (float) Math.toDegrees(orientation[1]);
                float roll    = (float) Math.toDegrees(orientation[2]);

                // Device is flat if pitch & roll are near 0
                isDeviceFlat = (Math.abs(pitch) < 10 && Math.abs(roll) < 10);

                // Device pointing north if azimuth is within ±15° of 0
                float normalizedAzimuth = (azimuth + 360) % 360;
                isDevicePointingNorth = (normalizedAzimuth < 15 || normalizedAzimuth > 345);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }
}
