# MiniGPSBridger

**MiniGPSBridger** is an Android app that reads GPS data from multiple sources (USB GPS, TCP NMEA streams, etc.) and provides it as mock GPS locations for testing or development purposes. It supports high-frequency updates, foreground operation, and automatic recovery when backgrounded.

---

## Features

* Read GPS data from multiple providers:

  * **USB GPS** via `UsbSerialPort`
  * **TCP streams** (simulated NMEA data)
* Mock GPS location on the device using Android’s test provider API.
* Self-recoverable background operation via a foreground service.
* Configurable update rate with throttling to avoid “too fast” system blocks.
* Rate-limited and monotonic timestamp updates for reliable delivery.
* Kotlin + Jetpack Compose UI.

---

## Requirements

* Android SDK: 36 (or compatible)
* Permissions:

  * `ACCESS_FINE_LOCATION`
  * `ACCESS_COARSE_LOCATION`
  * `FOREGROUND_SERVICE`
  * `FOREGROUND_SERVICE_LOCATION` (for location foreground service)
  * `INTERNET` (for TCP provider)
* USB Host support (for USB GPS)
* Android Emulator or physical device

---

## Installation

1. Clone the repository:

```bash
git clone https://github.com/yourusername/MiniGPSBridger.git
```

2. Open in Android Studio.
3. Sync Gradle and ensure the project builds.
4. Install on an emulator or a real device.

---

## Usage

1. Launch the app.
2. Start the GPS reader by selecting a provider:

   * TCP stream
   * USB GPS
3. The app will start sending mock locations to the device.
4. Use other apps (e.g., Maps) to see the mocked GPS coordinates.

---

### Example TCP Provider

You can simulate a GPS source via a TCP NMEA server:

```bash
python3 fake_nmea_server.py --host 0.0.0.0 --port 5000
```

`MiniGPSBridger` can connect to this server and read NMEA sentences as if it were a real GPS device.

---

## Code Structure

* **MainActivity.kt** – Starts the GPS reader, initializes UI, and interacts with the foreground service.
* **GPSReader.kt** – Consumes NMEA sentences and updates mock GPS locations.
* **USBSerController.kt / TcpNmeaProvider.kt** – Interfaces to USB GPS or TCP stream.
* **GpsForegroundService.kt** – Keeps the app running in the background and ensures self-recovery.
* **UI (Jetpack Compose)** – Simple interface showing live coordinates and status.

---

## Notes / Tips

* Ensure the app has **mock location permission** in developer settings.
* Rate-limit GPS updates to \~1–5 Hz to avoid “location blocked - too fast” messages.
* Foreground service ensures continuous operation in background; otherwise, Android may suspend updates.
* The mock location provider is tested on the emulator and physical devices.

---

## License

GPLv3
