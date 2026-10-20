# Wrist Skin Temperature Wear

An isolated Wear OS prototype for Samsung Galaxy Watch skin-temperature readings. This repository replaces the retired ZhixueWear app; it is unrelated to the Motion Photo project.

## Features

- Reads Samsung skin-temperature samples and labels them as wrist skin temperature, not core body temperature.
- Checks sample ranges and short-window stability. The 0.4 °C spread is an engineering quality filter, not a clinical accuracy claim.
- Optional local research log for pairing a stable skin-temperature reading with an independent reference-thermometer reading.
- Does not calculate or display a body-temperature estimate. No validated model or training dataset is included.

## Hardware and SDK limitations

The adapter uses Samsung's platform `SemSensorManager` API by reflection and sensor type `65578`, based on inspection of the supplied APK. This is not a public cross-vendor Android API. Runtime operation requires a compatible Galaxy Watch, Samsung Health Platform / sensor service, firmware support, and body-sensor / skin-temperature permissions. Emulator and non-Samsung watch testing cannot validate sensor access.

The Samsung Health Sensor SDK AAR was not supplied. Reflected Samsung classes may be absent at runtime, even on a compatible watch. Obtain and link the currently licensed SDK, verify the official API contract, and test on physical target watches before distribution.

## Research data cautions

Paired observations are stored in app-private preferences. They are not uploaded or separately encrypted. The prototype does not enforce a research protocol, participant consent, or reference-instrument calibration. Do not use observations for diagnosis or clinical model training without an approved protocol, suitable reference measurements, independent subject-level validation, and regulatory review.

## Build

Requirements: JDK 17 and Android SDK 35. Locally run:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions builds and uploads a debug APK artifact on pushes and pull requests. The build artifact is not a signed medical product.
