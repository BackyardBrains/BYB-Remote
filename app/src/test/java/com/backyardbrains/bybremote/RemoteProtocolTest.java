package com.backyardbrains.bybremote;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import org.junit.Test;

/** Locks the wire protocol the signal generator firmware expects. See docs/PROTOCOL.md. */
public class RemoteProtocolTest {

    @Test public void uuidsMatchFirmware() {
        assertEquals(UUID.fromString("0000b2b0-0000-1000-8000-00805f9b34fb"), RemoteProtocol.BYB_ROBOROACH_SERVICE);
        assertEquals(UUID.fromString("0000b2b1-0000-1000-8000-00805f9b34fb"), RemoteProtocol.ROBOROACH_FREQUENCY);
        assertEquals(UUID.fromString("0000b2b2-0000-1000-8000-00805f9b34fb"), RemoteProtocol.ROBOROACH_PULSE_WIDTH);
        assertEquals(UUID.fromString("0000b2b3-0000-1000-8000-00805f9b34fb"),
            RemoteProtocol.ROBOROACH_DURATION_IN_5MS);
        assertEquals(UUID.fromString("0000b2b4-0000-1000-8000-00805f9b34fb"), RemoteProtocol.ROBOROACH_RANDOM_MODE);
        assertEquals(UUID.fromString("0000b2b5-0000-1000-8000-00805f9b34fb"),
            RemoteProtocol.ROBOROACH_STIMULATE_LEFT);
        assertEquals(UUID.fromString("0000b2b6-0000-1000-8000-00805f9b34fb"),
            RemoteProtocol.ROBOROACH_STIMULATE_RIGHT);
        assertEquals(UUID.fromString("0000b2b7-0000-1000-8000-00805f9b34fb"), RemoteProtocol.ROBOROACH_GAIN);
        assertEquals(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"), RemoteProtocol.BATTERY_SERVICE);
        assertEquals(UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb"), RemoteProtocol.BATTERY_LEVEL);
    }

    @Test public void matchesAdvertisedNamesLikeIos() {
        assertTrue(RemoteProtocol.isRemoteName("RoboRoach"));
        assertTrue(RemoteProtocol.isRemoteName("RoboRoach2"));
        assertTrue(RemoteProtocol.isRemoteName("RoboHuman"));
        assertFalse(RemoteProtocol.isRemoteName(null));
        assertFalse(RemoteProtocol.isRemoteName(""));
        assertFalse(RemoteProtocol.isRemoteName("SpikerBot"));
    }

    @Test public void settingsAreSingleUnsignedBytes() {
        assertArrayEquals(new byte[] { 55 }, RemoteProtocol.uint8(55));
        assertArrayEquals(new byte[] { (byte) 150 }, RemoteProtocol.uint8(150));
        // out of range values clamp instead of wrapping around
        assertArrayEquals(new byte[] { (byte) 255 }, RemoteProtocol.uint8(1000));
        assertArrayEquals(new byte[] { 0 }, RemoteProtocol.uint8(-3));

        assertEquals(150, RemoteProtocol.readUint8(new byte[] { (byte) 150 }));
        assertEquals(-1, RemoteProtocol.readUint8(new byte[0]));
        assertEquals(-1, RemoteProtocol.readUint8(null));
    }

    @Test public void durationTravelsInFiveMillisecondUnits() {
        assertArrayEquals(new byte[] { 100 }, RemoteProtocol.durationToWire(500));
        assertArrayEquals(new byte[] { (byte) 200 }, RemoteProtocol.durationToWire(1000));
        assertEquals(500, RemoteProtocol.durationFromWire(100));
    }

    @Test public void configurationStringMatchesOriginalApp() {
        assertEquals("55Hz, 10ms Pulse, for 500ms. 50%", RemoteProtocol.configurationString(false, 55, 10, 500, 50));
        assertEquals("Randomized Stimulus. 75%", RemoteProtocol.configurationString(true, 55, 10, 500, 75));
    }
}
