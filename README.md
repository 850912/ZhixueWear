# Wrist Skin Temperature Wear

An isolated Wear OS prototype for Samsung Galaxy Watch skin-temperature readings. This repository replaces the retired ZhixueWear app and is unrelated to the Motion Photo project.

## Features

- **Wrist skin temperature monitoring**: Reads Samsung skin-temperature samples and labels them as wrist skin temperature, **not** core body temperature.
- **Two measurement modes**:
  - **快速测量** (~25 s): Single sample, fast but less precise. For reference only.
  - **准确测量** (~105 s): Requires 5 stable samples (spread ≤ 0.3 °C) for a more reliable reading.
- **Object temperature**: Also displays object temperature measured by the same sensor (for reference only).
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

## Power consumption notes

- Sensors are only active during an active measurement session; they are stopped immediately after a result is returned.
- Sampling interval is throttled to 2 seconds to reduce sensor wake-ups.
- Motion tracking uses a small rolling window (5 samples) to minimize memory and CPU usage.

## Build

Requirements: JDK 17 and Android SDK 35. Locally run:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions builds and uploads a debug APK artifact on pushes and pull requests. The build artifact is not a signed medical product.

## Important measurement disclaimers

- **腕部皮肤温度 ≠ 核心体温**: 腕部皮肤温度通常比腋下/口腔低 1-2°C，且受环境温度、佩戴松紧、血流影响极大。
- **快速测量**：单次采样，精度较低，仅供参考。
- **准确测量**：需要连续 5 个样本且波动 ≤0.3°C，结果更稳定，但仍不代表核心体温。
- 仅供趋势观察，不用于发烧判断、诊断或治疗。
