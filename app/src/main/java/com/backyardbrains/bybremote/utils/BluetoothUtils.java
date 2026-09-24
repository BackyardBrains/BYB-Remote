package com.backyardbrains.bybremote.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

/**
 * @author Tihomir Leka <ticapeca at gmail.com.
 */
public class BluetoothUtils {

    /**
     * Checks if this device has necessary BT and BLE hardware available.
     */
    public static boolean checkBleHardwareAvailable(@NonNull Context context) {
        // First check general Bluetooth Hardware:
        // get BluetoothManager...
        final BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) return false;
        // .. and then get adapter from manager
        final BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null) return false;

        // and then check if BT LE is also available
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    /**
     * Check if BT is turned ON and enabled for us. This should be called in {@link Activity#onResume()} to always sure
     * that BT is ON when Your application is put into the foreground.
     */
    @SuppressLint("MissingPermission") // isEnabled() needs only the install-time BLUETOOTH permission (API <= 30)
    public static boolean isBtEnabled(@NonNull Context context) {
        final BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) return false;

        final BluetoothAdapter adapter = manager.getAdapter();
        return adapter != null && adapter.isEnabled();
    }

    /**
     * Runtime permissions needed to scan for and talk to the signal generator. Android 12+ has "Nearby devices";
     * older versions need location access for Bluetooth scans.
     */
    @NonNull public static String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[] { Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT };
        }
        return new String[] { Manifest.permission.ACCESS_FINE_LOCATION };
    }

    public static boolean hasPermissions(@NonNull Context context) {
        for (String permission : requiredPermissions()) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Android 11 and older return no Bluetooth scan results while location services are switched off.
     */
    public static boolean isLocationOffForScanning(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return false;
        final LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return manager != null && !LocationManagerCompat.isLocationEnabled(manager);
    }
}
