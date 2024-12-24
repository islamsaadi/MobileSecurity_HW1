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

    private static final String PASSWORD_PREFIX = "10s20w30q";
    private static final float DISTANCE_THRESHOLD = 5000f; // 5 km
    private static final double ARRABAH_LAT = 32.85254314059482;
    private static final double ARRABAH_LNG = 35.33675279027549;

    private static final double TEL_AVIV_LAT = 32.08684812926745;
    private static final double TEL_AVIV_LNG = 34.7895403545493;

    private EditText passwordField;
    private Button loginButton;

    private FusedLocationProviderClient fusedLocationClient;
    private SettingsClient settingsClient;
    private Location currentLocation;

    // Permissions
    private boolean locationPermissionGranted = false;
    private boolean finePermissionRequestedOnce = false;
    private int timesDenied = 0; // increments on each denial

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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        settingsClient = LocationServices.getSettingsClient(this);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer  = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        locationPermissionGranted = true;
                        checkLocationSettings();
                    } else {
                        timesDenied++;
                        if (timesDenied >= 2) {
                            showFinalInstructionsScreen();
                        }
                    }
                }
        );

        loginButton.setOnClickListener(view -> attemptLogin());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        }

        checkPermissionsStateSilently();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    /**
     * If never requested before, request once. Otherwise, do not show repeated dialogs here.
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

    private void attemptLogin() {
        if (!locationPermissionGranted) {
            handleLocationPermissionOnLoginAttempt();
            return;
        }


        if (!isBrightnessSufficient()) {
            showErrorDialog("Screen brightness must be at least 50%.");
            return;
        }

        String password = passwordField.getText().toString();
        if (!isPasswordValid(password)) {
            showErrorDialog("Password must contain the last digit of the current battery level.");
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

        Toast.makeText(this, "Login Successful!", Toast.LENGTH_LONG).show();
    }

    /**
     * Distinguish between no location permission or only approximate location.
     * If user has approximate only, ask them to enable precise location.
     */
    private void handleLocationPermissionOnLoginAttempt() {
        int fineStatus = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        int coarseStatus = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (fineStatus == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true;
            attemptLogin();
            return;
        }

        // If user has only approximate (coarse) but not fine:
        if (coarseStatus == PackageManager.PERMISSION_GRANTED && fineStatus != PackageManager.PERMISSION_GRANTED) {
            // Explain they need to enable precise location
            // Possibly re-request or direct them to settings.
            timesDenied++;
            if (timesDenied >= 2) {
                showFinalInstructionsScreen();
            } else {
                showPreciseLocationRequiredDialog();
            }
        } else {
            // No fine, possibly no coarse
            boolean shouldRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION);
            if (shouldRationale) {
                showRationaleDialog();
            } else {
                timesDenied++;
                if (timesDenied >= 2) {
                    showFinalInstructionsScreen();
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                }
            }
        }
    }

    private void showRationaleDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Location Permission Needed")
                .setMessage("This app requires precise location permission. " +
                        "If you use 'Ask every time,' please allow precise location.")
                .setCancelable(false)
                .setPositiveButton("Grant", (dialog, which) ->
                        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION))
                .setNegativeButton("Exit", (dialog, which) -> finish())
                .show();
    }

    /**
     * If user has only approximate location, we ask them to switch to precise location.
     * They can be re-requested or sent to Settings.
     */
    private void showPreciseLocationRequiredDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Precise Location Required")
                .setMessage("You've only granted approximate location. Please enable 'Use precise location' in the app's permissions.")
                .setCancelable(false)
                .setPositiveButton("Request Again", (dialog, which) -> {
                    // Attempt requesting fine location again
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

    private void showFinalInstructionsScreen() {
        startActivity(new Intent(this, FinalPermissionActivity.class));
        finish(); // Don't allow returning here without permission
    }

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
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
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
                        Toast.makeText(MainActivity.this, "Failed to get location.", Toast.LENGTH_SHORT).show());
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
            if(locationResult.getLastLocation() != null) {
                currentLocation = locationResult.getLastLocation();
                fusedLocationClient.removeLocationUpdates(this);
            }
        }
    };

    private boolean isBrightnessSufficient() {
        try {
            ContentResolver cr = getContentResolver();
            int brightness = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS);
            return brightness >= 128;
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean isPasswordValid(String inputPassword) {
        // Sum all digits of the battery level
        int batteryDigitsSum = sumOfBatteryDigits();

        // Build the correct password: constant prefix + sum of all digits of battery level
        String correctPassword = PASSWORD_PREFIX + batteryDigitsSum;

        return inputPassword.equals(correctPassword);
    }

    private int sumOfBatteryDigits() {
        int batteryLevel = getBatteryLevel(); // e.g., 87
        int sum = 0;
        while (batteryLevel > 0) {
            sum += (batteryLevel % 10); // Add last digit
            batteryLevel /= 10;         // Remove last digit
        }
        return sum; // e.g., 8 + 7 = 15
    }

    private int getBatteryLevel() {
        BatteryManager bm = (BatteryManager)getSystemService(BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    private boolean isWifiConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = cm != null ? cm.getActiveNetworkInfo() : null;
        return info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI;
    }

    private boolean isDeviceCharging() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        if (batteryStatus == null) {
            // Fallback if we somehow couldn’t retrieve the broadcast
            return false;
        }

        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

        // If it's either charging or fully charged, we consider the device "charging"
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

    // Sensor handling
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

                isDeviceFlat = (Math.abs(pitch) < 10 && Math.abs(roll) < 10);
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
