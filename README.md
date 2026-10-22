# Wrist Skin Temperature Wear

An isolated Wear OS prototype for Samsung Galaxy Watch skin-temperature readings. This repository replaces the retired ZhixueWear app and is unrelated to the Motion Photo project.

## Features

- **Wrist skin temperature monitoring**: Reads Samsung skin-temperature samples and labels them as wrist skin temperature, **not** core body temperature.
- **Two measurement modes**:
  - **快速测量** (~20 s): Single sample, fast but less precise. For reference only.
  - **准确测量** (~45 s): Requires 5 stable samples (spread ≤ 0.3 °C) for a more reliable reading.
- **Ambient temperature**: Shows the ambient temperature reported together with the skin temperature by the same sensor.
- **Wear detection**: Detects whether the watch is snug against the wrist by comparing skin temperature against ambient temperature (off-wrist the two converge).
- **Motion detection**: Uses the system accelerometer (5 Hz) to detect wrist movement; samples are skipped while the wrist is moving.
- **Background auto-measure**: When enabled, a system `JobScheduler` job takes **one** sample every 15 / 30 / 60 minutes, even when the app is closed.
- **Personal trend view**: Selectable time ranges (1 hour / 1 day / 1 week / 1 month) with a real time-axis chart (max / mean / min reference lines, gradient area, data points) and a per-sample list.
- Shows separate Health Sensor Service connection, device capability, permission, SDK-policy, tracker-error, not-worn, motion and timeout states.
- Local history storage (up to 1000 readings) with mean and range summary per selected period.

## Samsung SDK and hardware

The project includes Samsung Health Sensor SDK 1.4.1 in `app/libs/` and uses `HealthTrackingService`, `HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS` and `ValueKey.SkinTemperatureSet`. Runtime operation requires a supported Galaxy Watch, current Samsung Health Sensor Service / Health Platform, supported firmware, and the appropriate runtime body-sensor permission. Emulator and non-Samsung watch testing cannot validate the hardware path.

`ValueKey.SkinTemperatureSet` exposes exactly three values:

| Key | Meaning |
| --- | --- |
| `OBJECT_TEMPERATURE` | Temperature of the object touching the watch — i.e. the **wrist skin temperature** while worn |
| `AMBIENT_TEMPERATURE` | Ambient temperature around the watch |
| `STATUS` | `0` = normal, `-1` = error (erroneous data points are discarded) |

There is **no** separate "skin temperature" value and no third temperature channel; earlier builds displayed `OBJECT_TEMPERATURE` twice under two different labels.

The SDK archive's announcement and license text is included at `licenses/samsung-health-sensor-sdk-1.4.1-Announcement.txt`. Review it before redistribution.

## Research data cautions

Any paired research observations are local-only. They are not uploaded or separately encrypted. The prototype does not enforce a research protocol, participant consent, or reference-instrument calibration. Do not use results for diagnosis, treatment, or clinical model training without an approved protocol, suitable reference measurements, independent subject-level validation, and regulatory review.

## Wear OS 6 / Android 16 permissions

This app requests:

- `android.permission.BODY_SENSORS` — skin temperature data (targetSdk ≤ 35)
- `android.permission.health.READ_SKIN_TEMPERATURE` — skin temperature data (targetSdk ≥ 36)
- `com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA` — Samsung-specific health data
- `android.permission.ACTIVITY_RECOGNITION` — motion detection (optional: the measurement still runs without it)
- `android.permission.RECEIVE_BOOT_COMPLETED` — restores the background job after a reboot

## Power consumption notes

Background measurement is designed around *not* keeping the radio/CPU/sensor alive:

- **No foreground service.** There is no persistent notification and no wakelock; the process can be reclaimed as soon as the job ends.
- **`JobScheduler`, not exact alarms.** Periodic jobs are batched by the system into Doze / App Standby friendly wake-up windows, so the number of wake-ups is minimal and no exact-alarm quota is consumed.
- **One sample per wake-up.** A background run connects, takes a single reading, disconnects and exits. The skin-temperature sensor is therefore powered for roughly ten seconds per run instead of staying hot.
- **No motion detection in the background.** The accelerometer is only registered during a foreground measurement.
- **`setRequiresBatteryNotLow(true)`.** The system postpones background sampling while the battery is low.
- **The sensor is released as soon as the app leaves the foreground** (`onStop`), so the watch never keeps the sensor running in the background by accident.
- **Motion tracking uses a small rolling window (5 samples)** and is unregistered immediately after a measurement.
- A longer background interval (60 min) costs proportionally less than 15 min.

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
