package com.backyardbrains.bybbackpack;

import android.bluetooth.BluetoothDevice;

public interface RemoteManagerCallbacks {

    /** A signal generator answered the scan. Called only for devices that match the protocol name. */
    void uiDeviceFound(final BluetoothDevice device, int rssi);

    void uiDeviceConnected(final BluetoothDevice device);

    void uiDeviceDisconnected(final BluetoothDevice device);

    void uiServicesFound();

    void uiRemotePropertiesUpdated();

    void uiLeftTurnSentSuccessfully(final int stimulusDuration);

    void uiRightTurnSentSuccessfully(final int stimulusDuration);

    /**
     * Define Null Adapter class for that interface
     */
    class Null implements RemoteManagerCallbacks {

        @Override public void uiDeviceFound(BluetoothDevice device, int rssi) {
        }

        @Override public void uiDeviceConnected(BluetoothDevice device) {
        }

        @Override public void uiDeviceDisconnected(BluetoothDevice device) {
        }

        @Override public void uiServicesFound() {
        }

        @Override public void uiRemotePropertiesUpdated() {
        }

        @Override public void uiLeftTurnSentSuccessfully(int stimulusDuration) {
        }

        @Override public void uiRightTurnSentSuccessfully(int stimulusDuration) {
        }
    }
}
