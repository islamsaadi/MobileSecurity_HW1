Secure Login App
================

This project is an Android application that enforces a robust login mechanism based on multiple environmental and device conditions. The user must satisfy a variety of checks before a successful login can occur.

Table of Contents
-----------------

*   [Features](#features)
    
*   [Setup & Installation](#setup--installation)
    
*   [Usage Flow](#usage-flow)
    
*   [Configuration](#configuration)
    
*   [Troubleshooting](#troubleshooting)
    
*   [License](#license)
    

Features
--------

1.  **Permission Handling**
    
    *   Dynamically requests ACCESS\_FINE\_LOCATION using ActivityResultContracts.RequestPermission.
        
    *   Multiple denials lead to a final instructions screen directing the user to manually enable permissions in Settings.
        
2.  **Location Checks**
    
    *   Enforces GPS enablement (high-accuracy mode).
        
    *   User must be near one of two locations (Arrabah or Tel Aviv), within a configurable distance (e.g., 10 km).
        
3.  **Charging State**
    
    *   Device must be plugged in (charging).
        
4.  **Screen Brightness**
    
    *   Checks if the display brightness is at least 50% (≥ 128 out of 255).
        
5.  **WiFi Connectivity**
    
    *   Validates if the device is connected to a WiFi network.
        
6.  **Sensors**
    
    *   Uses the accelerometer to confirm the device is lying flat.
        
    *   Uses the magnetometer to confirm the device is pointing north.
        
7.  **Password Logic**
    
    *   Constant prefix: 10s20w30q.
        
    *   Followed by **the sum of all digits** in the current battery percentage.
        
        *   Example: If battery is 87%, then 8 + 7 = 15; the correct password is 10s20w30q15.
            

Setup & Installation
--------------------

1.  Clone the repo
    
2.  **Open in Android Studio**
    
    *   Start Android Studio and select **File > Open**.
        
    *   Navigate to the cloned folder and open it.
        
3.  Copy: 'implementation 'com.google.android.gms:play-services-location:21.0.1' This library handles location settings prompts (GPS enablement).
    
4.  **Run the App**
    
    *   Use an **Android device** or **emulator**.
        
    *   Press **Run** (▶) in Android Studio.
        
    *   Grant location permission when prompted.
        

Usage Flow
----------

1.  **Permissions**
    
    *   The app silently checks whether ACCESS\_FINE\_LOCATION is granted.
        
    *   If not granted, it requests once automatically.
        
    *   If the user denies multiple times, a final instructions screen (or dialog) directs them to Settings.
        
2.  **Location & GPS**
    
    *   The app ensures GPS is enabled (high accuracy).
        
    *   The user must be within DISTANCE\_THRESHOLD (e.g., 10 km) of Arrabah or Tel Aviv.
        
3.  **Charging & Brightness**
    
    *   The phone must be plugged in (charging).
        
    *   Screen brightness must be ≥ 128 (on a 0–255 scale).
        
4.  **WiFi Check**
    
    *   Must be connected to a WiFi network (ConnectivityManager + NetworkInfo).
        
5.  **Orientation & Direction**
    
    *   Accelerometer confirms device is flat (pitch & roll near 0).
        
    *   Magnetometer confirms device is pointing north (azimuth near 0).
        
6.  **Password**
    
    *   User enters: 10s20w30q + sum of battery digits.
        
    *   If battery is 87%, sum is 8 + 7 = 15, so password is 10s20w30q15.
        
7.  **Successful Login**
    
    *   If all checks pass, displays "Login Successful!".
        

Configuration
-------------

*   **Distance Threshold** Change private static final float DISTANCE\_THRESHOLD = 10000f; for 10 km, or another distance to suit your needs.
    
*   **Sensor Tolerance** Modify how strictly you detect “flat” or “north” by changing pitch/roll and azimuth thresholds.
    
*   **Password Logic** In isPasswordValid(), adjust the prefix (10s20w30q) or how you combine battery digits (sum of digits, last digit, etc.).
    

Troubleshooting
---------------

*   **Location Permission**
    
    *   If the user selects “Don’t Ask Again,” the app will direct them to enable permission in Settings.
        
    *   Multiple denials also lead to a final instructions screen.
        
*   **Battery Checks**
    
    *   On some devices, BatteryManager.isCharging() might not accurately reflect charging. Consider the broadcast method (ACTION\_BATTERY\_CHANGED) if needed.
        
*   **Emulator**
    
    *   Use adb emu power ac on to simulate charging.
        
    *   Use adb emu power status charging or adb emu power level 50 to set battery level.
        
    *   The emulator might not reliably provide sensor data (accelerometer/magnetometer).
        

License
-------

This project is free to use and modify. Include an open-source license (e.g., MIT) or mark it as proprietary, depending on your needs.
