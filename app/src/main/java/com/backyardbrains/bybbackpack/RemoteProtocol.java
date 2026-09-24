package com.backyardbrains.bybbackpack;

import java.util.UUID;

/**
 * The Bluetooth protocol spoken by the signal generator firmware. The hardware still advertises
 * itself as "RoboRoach" (or "RoboHuman") and uses the original GATT layout, so none of these
 * names or UUIDs may change without a firmware change. See docs/PROTOCOL.md.
 */
public final class RemoteProtocol {

    private RemoteProtocol() {
    }

    /** Base of every Bluetooth SIG 16-bit UUID: 0000xxxx-0000-1000-8000-00805f9b34fb. */
    private static final long BASE_UUID_LSB = 0x800000805f9b34fbL;

    public static UUID uuid16(long assignedNumber) {
        return new UUID((assignedNumber << 32) | 0x1000, BASE_UUID_LSB);
    }

    public static final UUID DEVICE_INFORMATION = uuid16(0x180A);
    public static final UUID BATTERY_SERVICE = uuid16(0x180F);
    public static final UUID BATTERY_LEVEL = uuid16(0x2A19);
    public static final UUID BYB_ROBOROACH_SERVICE = uuid16(0xB2B0);
    public static final UUID ROBOROACH_FREQUENCY = uuid16(0xB2B1);
    public static final UUID ROBOROACH_PULSE_WIDTH = uuid16(0xB2B2);
    public static final UUID ROBOROACH_DURATION_IN_5MS = uuid16(0xB2B3);
    public static final UUID ROBOROACH_RANDOM_MODE = uuid16(0xB2B4);
    public static final UUID ROBOROACH_STIMULATE_LEFT = uuid16(0xB2B5);
    public static final UUID ROBOROACH_STIMULATE_RIGHT = uuid16(0xB2B6);
    public static final UUID ROBOROACH_GAIN = uuid16(0xB2B7);

    /** Advertised names the firmware uses; same match as the iOS app. */
    private static final String[] DEVICE_NAMES = { "RoboRoach", "RoboHuman" };

    public static boolean isRemoteName(String name) {
        if (name == null) return false;
        for (String known : DEVICE_NAMES) {
            if (name.contains(known)) return true;
        }
        return false;
    }

    /** Every setting is a single unsigned byte on the wire; clamp instead of wrapping. */
    public static byte[] uint8(int value) {
        return new byte[] { (byte) Math.max(0, Math.min(255, value)) };
    }

    /** Reads a single unsigned byte, or -1 when the peripheral sent nothing. */
    public static int readUint8(byte[] value) {
        if (value == null || value.length == 0) return -1;
        return value[0] & 0xFF;
    }

    /** Duration travels in 5 ms units. */
    public static byte[] durationToWire(int durationMs) {
        return uint8(durationMs / 5);
    }

    public static int durationFromWire(int units) {
        return units * 5;
    }

    public static String configurationString(boolean randomMode, int frequency, int pulseWidth, int duration,
        int gain) {
        if (randomMode) {
            return "Randomized Stimulus. " + gain + "%";
        }
        return frequency + "Hz, " + pulseWidth + "ms Pulse, for " + duration + "ms. " + gain + "%";
    }
}
