# AppOff

A modern Android app manager and system optimization suite, built with Kotlin, Jetpack Compose and Material Design 3, by **The Blacksheep Software**.

AppOff helps you monitor and control background activity: stop, freeze or disable apps, clean junk, and watch real system metrics. Advanced actions use Shizuku or root access that you grant yourself.

---

## 🚀 User Manual & Features Guide

### 1. 🧹 Memory Optimization Modes
AppOff offers four escalating cleaning strategies depending on the permissions you grant:

| Mode | Icon | Mechanism | Requirements | Strength |
|---|---|---|---|---|
| **Standard Mode** | 🚀 | Trims cached background processes using `ActivityManager.killBackgroundProcesses()`. Safe for everyday use. | No extra permissions | Gentle |
| **System Mode** | ⚙️ | Optimizes system services and active background tasks. | Write Secure Settings / Accessibility | Moderate |
| **Deep Hibernate (Shizuku)** | 🔐 | Force-stops background apps (`am force-stop`) and trims app caches (`pm trim-caches`) via a Shizuku shell service. | [Shizuku](https://shizuku.rikka.app) running | Strong (no root needed) |
| **Root Mode** | ⚡ | Drops caches (`drop_caches`), trims flash storage (`fstrim`) and force-stops background tasks via superuser commands. | Root (Magisk/KernelSU) | Maximum |

> Android manages memory on its own, and killed apps may restart. Results vary by device and usage.

---

### 2. ⚡ Turbo Mode & Auto-Clean
- **Turbo Mode**: High-intensity background cleanup for low-RAM devices or heavy multitasking sessions.
- **Automatic triggers**: Interval-based cleaning, screen-off cleaning, or memory-threshold triggers.

---

### 3. 🛡️ Shield (Whitelist Management)
- Protect essential apps (Phone, Alarm Clock, Navigation, messaging apps and so on) from being closed, hibernated or optimized.
- Customizable whitelist lets you toggle system and user apps.

---

### 4. ❄️ App Freezer
- Freeze (disable) bloatware and background apps so they cannot run or use resources while frozen.
- Unfreeze apps whenever you need them again.
- Apps you freeze yourself are remembered, so automatic features such as Game Mode never re-enable them.

---

### 5. 🎮 Game Mode
- When a game comes to the foreground, AppOff temporarily stops and suspends non-essential background apps to free resources.
- When you leave the game, the apps it stopped are restored. Apps you froze yourself stay frozen, and AppOff's own screens, your launcher and the keyboard do not end the session.
- Actual performance gains depend on your device and the game.

---

### 6. 🧹 Junk Cleaner
- Scans internal storage for temporary cache files, obsolete log files, empty directories and leftover APK packages.
- Cleans unused files with one tap to free storage space.

---

### 7. 🌡️ CPU Cooler
- Real-time CPU load and thermal monitoring.
- Detects CPU-heavy background apps and lets you hibernate them to reduce heat.

---

### 8. 🔍 Security & Privacy Inspector
- Audits high-risk permissions (Location, Camera, Microphone, SMS, Contacts, Storage) granted to installed apps.
- Categorizes apps by risk level and provides privacy insights.

---

### 9. 📊 Diagnostics & System Info
- Hardware and runtime telemetry:
  - **Battery**: Health, temperature, voltage and estimated wear.
  - **Storage & RAM**: Live breakdown of storage partitions, zRAM and RAM distribution.
  - **Network Sockets**: Active TCP connections and local/remote socket inspection.
  - **Runtime**: JVM/ART heap metrics, active threads and CPU core frequencies.

---

### 10. 🛠️ Permission Hub & Setup
- Step-by-step setup guide for advanced Android permissions (Shizuku, Write Secure Settings, Usage Access, Accessibility, Notification Access).
- Copyable ADB command snippets for configuration over USB or Wireless Debugging.

---

### 11. ℹ️ About & Legal
- **About** screen with app version and developer information.
- **Terms of Service**, **Privacy Policy** and **Open-source licenses** are available from the navigation menu.

---

## 🔒 Privacy

- AppOff works on your device and is not built to collect, sell or share personal information.
- It reads app lists, usage statistics and system status only to show them to you and to perform actions you request.
- Settings (whitelist, frozen apps, preferences) are stored locally in the app's private storage.
- Many features need powerful permissions; each is used only for its related feature and can be revoked in Android settings.

---

## ⚠️ Safety

Disabling or removing system or essential apps can cause instability or lost data. Review what you select, keep important apps in the Shield whitelist, and keep backups.

---

## 🛠️ Getting Started for Developers

1. **Open Project**: Open Android Studio (Iguana / Koala / Ladybug or newer) → **Open** → select the AppOff project folder.
2. **Gradle Sync**: Allow Gradle to sync dependencies (AGP 8.5.2 / Gradle 8.7 / Kotlin 1.9.24).
3. **Run App**: Deploy to any device or emulator running `minSdk 26` (Android 8.0+) or higher.

Shizuku features need the [Shizuku](https://shizuku.rikka.app) app installed and its service running (Wireless Debugging or ADB).

---

## 📄 Legal & Licenses

- Developer: **The Blacksheep Software**
- Third-party libraries are listed in the app under **Licenses** (Shizuku API, Jetpack Compose, AndroidX, Kotlin and others, mostly Apache License 2.0).
- Project license: _add your license here (for example MIT or Apache-2.0)_.
- Contact: _add your contact email here_.
