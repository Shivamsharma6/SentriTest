# Sentri

Sentri is an Android + ESP32 access-control system.

- Android app manages businesses, customers, users, seats, shifts, payments, leaves, cards, notifications, and devices.
- ESP32 firmware handles dual RFID readers, Firestore-backed access checks, provisioning portal, and OTA update flow.

## Repository Contents

- `app/` Android application (Java, Android SDK, Firebase).
- `firmware.ino` ESP32 firmware for device-side access control.

## Key Features

### Android App

- Authentication and signup flows with Firebase Auth.
- Multi-business user access management.
- Customer lifecycle:
  - add customer
  - shift assignment
  - payment capture
  - leave handling
  - profile/history/comments
- Card assignment and replacement flows.
- Business-level payment and notification views.
- Device list and device settings screens.
- Role-oriented management screens for users and businesses.

### Firmware (ESP32)

- Dual MFRC522 RFID reader support (entry/exit).
- Firestore integration for access validation and logging.
- Wi-Fi provisioning portal.
- OTA update support.
- Runtime log buffering/endpoints.

## Architecture Notes

The Android app follows a repository-oriented structure:

- `app/src/main/java/com/sentri/access_control/repositories/`
  - Firestore-backed repositories for business, user, customer, shift, payment, comments, cards, devices, leaves, etc.
- `app/src/main/java/com/sentri/access_control/data/FirestorePaths.java`
  - centralized Firestore collection/subcollection constants.
- `app/src/main/java/com/sentri/access_control/services/`
  - domain/service logic (example: dashboard metrics).
- `app/src/main/java/com/sentri/access_control/utils/`
  - utilities (prefs, date/currency, upload helpers, ID generation).

## Tech Stack

- Android SDK (Java 11 source/target)
- Gradle + AGP
- Firebase:
  - Auth
  - Firestore
  - Storage
- Glide
- ESP32 Arduino stack + Firebase client libraries

Dependencies are version-managed in:

- `gradle/libs.versions.toml`

## Prerequisites

### Android

- Android Studio (recent stable)
- JDK installed and `JAVA_HOME` configured
- Android SDK / platform tools
- Firebase project configured for this app

### Firmware

- Arduino IDE or PlatformIO for ESP32
- ESP32 board package installed
- Required Arduino libraries used in `firmware.ino` installed

## Android Setup

1. Clone/open the project in Android Studio.
2. Ensure `app/google-services.json` is present for your Firebase project.
3. Verify `local.properties` points to your Android SDK.
4. Ensure Java is configured:
   - `JAVA_HOME` points to a valid JDK
   - `java -version` works in terminal
5. Sync Gradle project.

## Build & Run (Android)

From Android Studio:

- Run app on emulator/device.

From terminal:

```powershell
.\gradlew.bat :app:assembleDebug
```

## Firmware Setup (High Level)

1. Open `firmware.ino`.
2. Configure board, port, and required credentials/config in firmware.
3. Install required libraries referenced in the sketch includes.
4. Build and flash to ESP32.

## Project Structure (High Level)

```text
Sentri2/
  app/
    src/main/java/com/sentri/access_control/
      data/
      repositories/
      services/
      utils/
      *.java (activities)
    src/main/res/
  firmware.ino
  gradle/
  build.gradle
  settings.gradle
```

## Notes

- The Android module currently targets:
  - `compileSdk 35`
  - `targetSdk 35`
  - `minSdk 24`
- Firebase security rules and production hardening should be reviewed before deployment.
