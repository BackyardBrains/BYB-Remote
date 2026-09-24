# Bluetooth protocol

The signal generator's firmware defines this protocol, and units already in the field cannot be
updated, so the app must keep speaking it exactly. The source of truth is the firmware in
[BackyardBrains/RoboRoach](https://github.com/BackyardBrains/RoboRoach) (`Firmware/TI/Source/roboRoach.h`).
The Android side lives in `RemoteProtocol.java` and is locked by `RemoteProtocolTest`.

## Finding the device

- The hardware advertises the name **`RoboRoach`**, and some units **`RoboHuman`**. The app accepts
  any advertised name that contains either string (the iOS app does the same).
- The scan is unfiltered and uses low-latency mode. After 4 seconds the app connects to the
  strongest-signal match it has seen; with no match it keeps scanning until the user taps Stop.
- The name comes from the advertisement's scan record, falling back to the cached device name.
- The app connects with `TRANSPORT_LE` and discovers services.

## Services and characteristics

All UUIDs are Bluetooth SIG 16-bit short forms: `0000xxxx-0000-1000-8000-00805f9b34fb`.

| UUID | Name in code | Value | App use |
|---|---|---|---|
| `B2B0` | `BYB_ROBOROACH_SERVICE` | service | stimulation settings |
| `B2B1` | `ROBOROACH_FREQUENCY` | uint8, Hz | read + write (slider 1–150) |
| `B2B2` | `ROBOROACH_PULSE_WIDTH` | uint8, ms | read + write (max = 1000 / frequency) |
| `B2B3` | `ROBOROACH_DURATION_IN_5MS` | uint8, **units of 5 ms** | read + write (0–1000 ms, sent as ms / 5) |
| `B2B4` | `ROBOROACH_RANDOM_MODE` | uint8, 0 / 1 | read + write |
| `B2B5` | `ROBOROACH_STIMULATE_LEFT` | write `0x01` | fire one output (swipe left, "Go Left!") |
| `B2B6` | `ROBOROACH_STIMULATE_RIGHT` | write `0x01` | fire the other output (swipe right, "Go Right!") |
| `B2B7` | `ROBOROACH_GAIN` | uint8, % | read + write (0–100, rounded to 5) |
| `B2B8`–`B2BD` | freq / pulse width / gain min & max | uint8 | exposed by firmware, not used by the app |
| `180F` / `2A19` | battery service / level | uint8, % | read after the settings |
| `180A` | device information | — | not used |

Every setting is one unsigned byte. The app clamps values to 0–255 rather than letting them wrap.

## Sequences

**After connecting**, the app reads one characteristic at a time, because Android allows only one
outstanding GATT request: frequency → pulse width → duration → random mode → gain → battery
level. The UI updates once the chain finishes. If the battery service is missing, the chain ends
after gain.

**A swipe** writes `0x01` to STIMULATE_LEFT or STIMULATE_RIGHT. When the write is acknowledged the
app shows "Go Left!" or "Go Right!" for the configured duration and ignores more swipes until then.

**Changing a setting** writes that characteristic right away. If a new frequency makes the current
pulse width longer than half the period, the app writes a halved pulse width 500 ms later.
