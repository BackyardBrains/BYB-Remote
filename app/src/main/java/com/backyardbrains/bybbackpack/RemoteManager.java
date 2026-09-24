package com.backyardbrains.bybbackpack;

import static com.backyardbrains.bybbackpack.RemoteProtocol.BATTERY_LEVEL;
import static com.backyardbrains.bybbackpack.RemoteProtocol.BATTERY_SERVICE;
import static com.backyardbrains.bybbackpack.RemoteProtocol.BYB_ROBOROACH_SERVICE;
import static com.backyardbrains.bybbackpack.RemoteProtocol.ROBOROACH_DURATION_IN_5MS;
import static com.backyardbrains.bybbackpack.RemoteProtocol.ROBOROACH_FREQUENCY;
import static com.backyardbrains.bybbackpack.RemoteProtocol.ROBOROACH_GAIN;
import static com.backyardbrains.bybbackpack.RemoteProtocol.ROBOROACH_PULSE_WIDTH;
import static com.backyardbrains.bybbackpack.RemoteProtocol.ROBOROACH_RANDOM_MODE;
import static com.backyardbrains.bybbackpack.RemoteProtocol.ROBOROACH_STIMULATE_LEFT;
import static com.backyardbrains.bybbackpack.RemoteProtocol.ROBOROACH_STIMULATE_RIGHT;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import com.backyardbrains.bybbackpack.utils.BluetoothUtils;
import java.util.UUID;

/**
 * Talks to the signal generator over Bluetooth LE.
 *
 * <p>MainActivity only calls in here once {@link BluetoothUtils#hasPermissions(Context)} is true, and the scan and
 * connect entry points re-check it, which is why the Bluetooth permission lint check is suppressed for the class.
 */
@SuppressLint("MissingPermission")
public class RemoteManager {

    private final static String TAG = RemoteManager.class.getSimpleName();

    /* callback object through which we are returning results to the caller */
    private RemoteManagerCallbacks mUiCallback = null;
    /* define NULL object for UI callbacks */
    private static final RemoteManagerCallbacks NULL_CALLBACK = new RemoteManagerCallbacks.Null();

    private Activity mParent = null;
    private boolean mConnected = false;
    private String mDeviceAddress = "";

    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothDevice mBluetoothDevice = null;
    private BluetoothGatt mBluetoothGatt = null;

    private BluetoothGattService mRemoteService;
    private BluetoothGattService mBatteryService;

    private int rrFrequency;
    private int rrPulseWidth;
    private int rrDuration;
    private int rrGain;
    private boolean rrRandomMode = false;
    private int rrBatteryLevel = 0;

    /* creates BleWrapper object, set its parent activity and callback object */
    public RemoteManager(Activity parent, RemoteManagerCallbacks callback) {
        this.mParent = parent;
        mUiCallback = callback;
        if (mUiCallback == null) mUiCallback = NULL_CALLBACK;
    }

    public boolean isConnected() {
        return mConnected;
    }

    public int getRemoteFrequency() {
        return rrFrequency;
    }

    public int getRemoteGain() {
        return rrGain;
    }

    public int getRemotePulseWidth() {
        return rrPulseWidth;
    }

    public int getRemoteDuration() {
        return rrDuration;
    }

    public boolean getRemoteRandomMode() {
        return rrRandomMode;
    }

    public int getRemoteBatteryLevel() {
        return rrBatteryLevel;
    }

    public String getConfigurationString() {
        return RemoteProtocol.configurationString(rrRandomMode, rrFrequency, rrPulseWidth, rrDuration, rrGain);
    }

    public void requestRemoteParameters() {
        if (mRemoteService == null) return;

        requestCharacteristicValue(mRemoteService.getCharacteristic(ROBOROACH_FREQUENCY));
    }

    /* set new value for turn right */
    public void turnRight() {
        writeSetting(ROBOROACH_STIMULATE_RIGHT, new byte[] { (byte) 0x01 });
    }

    /* set new value for turn left */
    public void turnLeft() {
        writeSetting(ROBOROACH_STIMULATE_LEFT, new byte[] { (byte) 0x01 });
    }

    public void updateGain(int gain) {
        writeSetting(ROBOROACH_GAIN, RemoteProtocol.uint8(gain));
        rrGain = gain;
    }

    public void updateFrequency(int freq) {
        writeSetting(ROBOROACH_FREQUENCY, RemoteProtocol.uint8(freq));
        rrFrequency = freq;
    }

    public void updateRandomMode(boolean randomMode) {
        writeSetting(ROBOROACH_RANDOM_MODE, RemoteProtocol.uint8(randomMode ? 1 : 0));
        rrRandomMode = randomMode;
    }

    public void updateDuration(int dur) {
        writeSetting(ROBOROACH_DURATION_IN_5MS, RemoteProtocol.durationToWire(dur));
        rrDuration = dur;
    }

    public void updatePulseWidth(int pw) {
        writeSetting(ROBOROACH_PULSE_WIDTH, RemoteProtocol.uint8(pw));
        rrPulseWidth = pw;
    }

    /* start scanning for BT LE devices around */
    public void startScanning() {
        Log.d(TAG, "startScanning()");
        final BluetoothLeScanner scanner = getScanner();
        if (scanner == null) return;

        final ScanSettings settings =
            new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        scanner.startScan(null, settings, mScanCallback);
    }

    /* stops current scanning */
    public void stopScanning() {
        Log.d(TAG, "stopScanning()");
        final BluetoothLeScanner scanner = getScanner();
        if (scanner != null) scanner.stopScan(mScanCallback);
    }

    private BluetoothLeScanner getScanner() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) return null;
        if (!BluetoothUtils.hasPermissions(mParent)) return null;
        return mBluetoothAdapter.getBluetoothLeScanner();
    }

    /**
     * Initialize BLE and get BT Manager & Adapter.
     *
     * @return {@code True} if initialization was successful, {@code false} otherwise.
     */
    public boolean initialize() {
        Log.d(TAG, "initialize()");

        final BluetoothManager btManager = (BluetoothManager) mParent.getSystemService(Context.BLUETOOTH_SERVICE);
        if (btManager == null) return false;

        if (mBluetoothAdapter == null) mBluetoothAdapter = btManager.getAdapter();
        return mBluetoothAdapter != null;
    }

    /* connect to the device with specified address */
    public boolean connect(final String deviceAddress) {
        if (mBluetoothAdapter == null || deviceAddress == null) return false;
        if (!BluetoothUtils.hasPermissions(mParent)) return false;
        mDeviceAddress = deviceAddress;

        Log.d(TAG, "connect()");

        // check if we need to connect from scratch or just reconnect to previous device
        if (mBluetoothGatt != null && mBluetoothGatt.getDevice().getAddress().equals(deviceAddress)) {
            // just reconnect
            return mBluetoothGatt.connect();
        } else {
            // connect from scratch
            // get BluetoothDevice object for specified address
            mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(mDeviceAddress);
            // connect over LE explicitly; dual-mode phones otherwise sometimes try classic Bluetooth and fail
            mBluetoothGatt = mBluetoothDevice.connectGatt(mParent, false, mBleCallback, BluetoothDevice.TRANSPORT_LE);
        }
        if (mBluetoothGatt == null) Log.e(TAG, "mBluetoothGatt is null!");
        return true;
    }

    /* disconnect the device. It is still possible to reconnect to it later with this Gatt client */
    public void disconnect() {
        Log.d(TAG, "disconnect()");

        if (mBluetoothGatt != null) mBluetoothGatt.disconnect();
    }

    /* close GATT client completely */
    public void close() {
        Log.d(TAG, "close()");
        if (mBluetoothGatt != null) mBluetoothGatt.close();
        mBluetoothGatt = null;
        mConnected = false;
    }

    /* request to discover all services available on the remote devices
     * results are delivered through callback object */
    public void startServicesDiscovery() {
        Log.d(TAG, "startServicesDiscovery()");
        if (mBluetoothGatt != null) mBluetoothGatt.discoverServices();
    }

    /* gets services and calls UI callback to handle them
     * before calling getServices() make sure service discovery is finished! */
    public void getSupportedServices() {
        Log.d(TAG, "getSupportedServices()");
        if (mBluetoothGatt == null) return;

        mRemoteService = mBluetoothGatt.getService(BYB_ROBOROACH_SERVICE);
        mBatteryService = mBluetoothGatt.getService(BATTERY_SERVICE);

        mUiCallback.uiServicesFound();
    }

    /* request to fetch newest value stored on the remote device for particular characteristic */
    public void requestCharacteristicValue(BluetoothGattCharacteristic ch) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null || ch == null) return;

        mBluetoothGatt.readCharacteristic(ch);

        Log.d(TAG, "requestCharacteristicValue()");
        // new value available will be notified in Callback Object
    }

    /* store a value the peripheral sent us (read response or notification) */
    private void onCharacteristicValue(BluetoothGattCharacteristic ch, byte[] value) {
        final int v = RemoteProtocol.readUint8(value);
        if (ch == null || v < 0) return;

        final UUID uuid = ch.getUuid();
        if (uuid.equals(ROBOROACH_FREQUENCY)) rrFrequency = v;
        if (uuid.equals(ROBOROACH_PULSE_WIDTH)) rrPulseWidth = v;
        if (uuid.equals(ROBOROACH_DURATION_IN_5MS)) rrDuration = RemoteProtocol.durationFromWire(v);
        if (uuid.equals(ROBOROACH_GAIN)) rrGain = v;
        if (uuid.equals(ROBOROACH_RANDOM_MODE)) rrRandomMode = v == 1;
        if (uuid.equals(BATTERY_LEVEL)) rrBatteryLevel = v;

        Log.d(TAG, "F=[" + rrFrequency + "]PW=[" + rrPulseWidth + "]");
    }

    /* write one setting to the signal generator */
    @SuppressWarnings("deprecation")
    private void writeSetting(UUID uuid, byte[] dataToWrite) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null || mRemoteService == null) return;
        final BluetoothGattCharacteristic ch = mRemoteService.getCharacteristic(uuid);
        if (ch == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            final int result =
                mBluetoothGatt.writeCharacteristic(ch, dataToWrite, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            if (result != BluetoothStatusCodes.SUCCESS) Log.e(TAG, "writeCharacteristic failed: " + result);
        } else {
            // first set it locally....
            ch.setValue(dataToWrite);
            // ... and then "commit" changes to the peripheral
            mBluetoothGatt.writeCharacteristic(ch);
        }
    }

    /* reads the settings one after another; Android allows only one outstanding GATT request */
    private void onReadComplete(BluetoothGattCharacteristic characteristic) {
        if (mRemoteService == null) return;

        final UUID uuid = characteristic.getUuid();
        if (uuid.equals(ROBOROACH_FREQUENCY)) {
            requestCharacteristicValue(mRemoteService.getCharacteristic(ROBOROACH_PULSE_WIDTH));
        } else if (uuid.equals(ROBOROACH_PULSE_WIDTH)) {
            requestCharacteristicValue(mRemoteService.getCharacteristic(ROBOROACH_DURATION_IN_5MS));
        } else if (uuid.equals(ROBOROACH_DURATION_IN_5MS)) {
            requestCharacteristicValue(mRemoteService.getCharacteristic(ROBOROACH_RANDOM_MODE));
        } else if (uuid.equals(ROBOROACH_RANDOM_MODE)) {
            requestCharacteristicValue(mRemoteService.getCharacteristic(ROBOROACH_GAIN));
        } else if (uuid.equals(ROBOROACH_GAIN)) {
            final BluetoothGattCharacteristic battery =
                mBatteryService == null ? null : mBatteryService.getCharacteristic(BATTERY_LEVEL);
            if (battery != null) {
                requestCharacteristicValue(battery);
            } else {
                mUiCallback.uiRemotePropertiesUpdated();
            }
        } else if (uuid.equals(BATTERY_LEVEL)) {
            mUiCallback.uiRemotePropertiesUpdated();
        }
    }

    /* defines callback for scanning results */
    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            final BluetoothDevice device = result.getDevice();
            if (device == null || device.getAddress() == null) return;

            // Prefer the name in the advertisement; the cached device name can be empty the first time we see it.
            final ScanRecord record = result.getScanRecord();
            String name = record != null ? record.getDeviceName() : null;
            if (name == null) name = device.getName();
            if (!RemoteProtocol.isRemoteName(name)) return;

            mUiCallback.uiDeviceFound(device, result.getRssi());
        }

        @Override public void onScanFailed(int errorCode) {
            Log.e(TAG, "onScanFailed(" + errorCode + ")");
        }
    };

    /* callbacks called for any action on particular Ble Device */
    private final BluetoothGattCallback mBleCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {

                mConnected = true;
                mUiCallback.uiDeviceConnected(mBluetoothDevice);

                // in our case we would also like automatically to call for services discovery
                startServicesDiscovery();

                Log.d(TAG, "onConnectionStateChange()");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnected = false;
                mUiCallback.uiDeviceDisconnected(mBluetoothDevice);
            }
        }

        @Override public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // now, when services discovery is finished, we can call getServices() for Gatt
                getSupportedServices();
            }
        }

        // Android 13+ delivers the value with the callback.
        @Override public void onCharacteristicRead(@NonNull BluetoothGatt gatt,
            @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int status) {
            Log.d(TAG, "onCharacteristicRead()");
            // we got response regarding our request to fetch characteristic value
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onCharacteristicValue(characteristic, value);
                onReadComplete(characteristic);
            }
        }

        // Android 12 and older.
        @SuppressWarnings("deprecation")
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            onCharacteristicRead(gatt, characteristic, characteristic.getValue(), status);
        }

        @Override public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
            @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
            Log.d(TAG, "onCharacteristicChanged()");
            // characteristic's value was updated due to enabled notification, lets get this value
            onCharacteristicValue(characteristic, value);
        }

        @SuppressWarnings("deprecation")
        @Override public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            onCharacteristicChanged(gatt, characteristic, characteristic.getValue());
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "onCharacteristicWrite(" + status + ")");
            // we got response regarding our request to write new value to the characteristic
            // let see if it failed or not
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.getUuid().equals(ROBOROACH_STIMULATE_LEFT)) {
                    mUiCallback.uiLeftTurnSentSuccessfully(rrDuration);
                }
                if (characteristic.getUuid().equals(ROBOROACH_STIMULATE_RIGHT)) {
                    mUiCallback.uiRightTurnSentSuccessfully(rrDuration);
                }
            }
        }
    };
}
