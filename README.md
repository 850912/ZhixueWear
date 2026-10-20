# Wrist Skin Temperature Wear

An isolated Wear OS prototype for Samsung Galaxy Watch skin-temperature readings. This repository replaces the retired ZhixueWear app and is unrelated to the Motion Photo project.

## Features

- Reads Samsung skin-temperature samples and labels them as wrist skin temperature, not core body temperature.
- Uses sample range and short-window stability checks. The 0.4 °C spread is an engineering quality filter, not a clinical accuracy claim.
- Shows separate Health Sensor Service connection, device capability, permission, SDK-policy, tracker-error, and timeout states.
- Keeps a local count of paired research observations. This build does not calculate or display a body-temperature estimate.

## Samsung SDK and hardware

The project includes Samsung Health Sensor SDK 1.4.1 in `app/libs/` and uses `HealthTrackingService`, `HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS`, and `ValueKey.SkinTemperatureSet`. Runtime operation requires a supported Galaxy Watch, current Samsung Health Sensor Service / Health Platform, supported firmware, and the appropriate runtime body-sensor permission. Emulator and non-Samsung watch testing cannot validate the hardware path.

The SDK archive's announcement and license text is included at `licenses/samsung-health-sensor-sdk-1.4.1-Announcement.txt`. Review it before redistribution.

## Research data cautions

Any paired research observations are local-only. They are not uploaded or separately encrypted. The prototype does not enforce a research protocol, participant consent, or reference-instrument calibration. Do not use results for diagnosis, treatment, or clinical model training without an approved protocol, suitable reference measurements, independent subject-level validation, and regulatory review.

## Build

Requirements: JDK 17 and Android SDK 35. Locally run:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions builds and uploads a debug APK artifact on pushes and pull requests. The build artifact is not a signed medical product.
