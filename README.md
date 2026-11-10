# Toolbox+

A cleaner replacement for the stock Toolbox apps on the **8849 TANK 4 Pro** (OB-A98).

- **Lamp** — camping lamp: quarter, half, full (continuous, with a 45 °C safety drop to half), flash, SOS
- **Signal** — warning lights: red / blue / green, flashing or steady, 31 sirens with preview
- **Projector** — power, brightness, auto keystone, focus, plus the cooling fan
- **Distance** — the built-in infrared rangefinder, single or live readings
- Quick Settings tiles for the camping lamp and warning lights

> Only works on the TANK 4 Pro (and possibly other phones with the same Agui firmware). It talks to the manufacturer's system services, which other phones don't have.

## Install

1. Download `Toolbox-plus.apk` from the [latest release](https://github.com/Stoin3/Toolbox-plus/releases/latest) and install it.
2. Install [Shizuku](https://github.com/RikkaApps/Shizuku/releases) and start it (see below).
3. Open Toolbox+ and tap **Grant permission**.

## Why Shizuku?

The lights, projector and sensors are driven by system services from the manufacturer (`AService`, `ProjectorService`, `agui_common`). Android's SELinux rules only let system apps and the ADB shell look them up, so Toolbox+ asks Shizuku (which runs with ADB rights) for the connection once. After that it talks to the services directly until the phone restarts.

## Setting up Shizuku

The app has the same guide under **Guide: set up Shizuku**.

### One-time setup (about 5 minutes, needs Wi-Fi, no computer)

1. Install Shizuku from the Play Store or GitHub.
2. Turn on developer options: *Settings → About phone*, tap *Build number* 7 times.
3. Connect to Wi-Fi (your own hotspot doesn't count).
4. Open Shizuku and tap *Pairing* under *Start via Wireless debugging*. Allow notifications.
5. Open *Settings → System → Developer options → Wireless debugging*, turn it on and tap *Pair device with pairing code*.
6. In the Shizuku notification, tap *Enter pairing code* and type the 6 digits.
7. Back in Shizuku, tap *Start*.
8. In Toolbox+, tap *Grant permission* → *Allow all the time*.

### After every restart

Connect to Wi-Fi, tap **Start Shizuku** in Toolbox+ (or a Toolbox+ tile), tap *Start* in Shizuku and go back.

### Tips

- In developer options, turn on *Disable adb authorization timeout* so you don't have to pair again after 7 days.
- If the lock screen shows up on the projected image, unlock with your fingerprint, or turn off *Power button instantly locks* and set a lock delay of 30 seconds or more.

## Build

Requires JDK 17+ and the Android SDK (path in `local.properties`).

```powershell
.\gradlew.bat assembleRelease
```

The APK ends up in `app/build/outputs/apk/release/`.
