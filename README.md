# Wrist Skin Temperature Wear

An isolated Wear OS prototype for Samsung Galaxy Watch skin-temperature readings. This repository replaces the retired ZhixueWear app and is unrelated to the Motion Photo project.

## Features

- **Wrist skin temperature monitoring**: Reads Samsung skin-temperature samples and labels them as wrist skin temperature, **not** core body temperature.
- **Two measurement modes**:
  - **快速测量** (~25 s): Single sample, fast but less precise. For reference only.
  - **准确测量** (~105 s): Requires 5 stable samples (spread ≤ 0.4 °C) for a more reliable reading.
- **Wear detection**: Detects whether the watch is snug against the wrist using skin temperature sensor feedback.
- **Motion detection**: Uses accelerometer data to detect wrist movement; measurements pause if motion is detected.
- **Auto-measure**: When enabled, automatically starts a measurement when the watch is worn and the wrist is still.
- **Personal trend view**: Selectable time ranges (1 hour / 1 day / 1 week / 1 month) with mean, min/max, and per-sample list.
- Shows separate Health Sensor Service connection, device capability, permission, SDK-policy, tracker-error, and timeout states.
- Local history storage (up to 200 readings) with mean and range summary per selected period.

## Samsung SDK and hardware

The project includes Samsung Health Sensor SDK 1.4.1 in `app/libs/` and uses `HealthTrackingService`, `HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND`, and `ValueKey.SkinTemperatureSet`. Runtime operation requires a supported Galaxy Watch, current Samsung Health Sensor Service / Health Platform, supported firmware, and the appropriate runtime body-sensor permission. Emulator and non-Samsung watch testing cannot validate the hardware path.

The SDK archive's announcement and license text is included at `licenses/samsung-health-sensor-sdk-1.4.1-Announcement.txt`. Review it before redistribution.

## Research data cautions

Any paired research observations are local-only. They are not uploaded or separately encrypted. The prototype does not enforce a research protocol, participant consent, or reference-instrument calibration. Do not use results for diagnosis, treatment, or clinical model training without an approved protocol, suitable reference measurements, independent subject-level validation, and regulatory review.

## Wear OS 6 / Android 16 permissions

This app requests:

- `android.permission.health.READ_SKIN_TEMPERATURE` — skin temperature data
- `android.permission.BODY_SENSORS` — general body sensor access (compatibility)
- `com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA` — Samsung-specific health data
- `android.permission.ACTIVITY_RECOGNITION` — motion detection

## Build

Requirements: JDK 17 and Android SDK 35. Locally run:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions builds and uploads a debug APK artifact on pushes and pull requests. The build artifact is not a signed medical product.
