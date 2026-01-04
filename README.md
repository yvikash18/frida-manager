# Frida Manager

A modern Android application for managing Frida server and detecting anti-Frida techniques on rooted devices.

[![Android CI](https://img.shields.io/github/actions/workflow/status/yvikash18/frida-server-manager-android/android.yml?logo=frida&logoColor=white)](https://github.com/yvikash18/frida-server-manager-android/actions/workflows/android.yml)
[![Release](https://img.shields.io/github/v/release/yvikash18/frida-server-manager-android)](https://github.com/yvikash18/frida-server-manager-android/releases)

## Features

### 🚀 Server Management
- **Automatic Installation**: Download Frida server from GitHub releases
- **Manual Installation**: Install from local binaries
- **Version Selection**: Choose and save specific Frida versions
- **Version Switching**: Switch between saved versions instantly
- **Custom Port**: Configure server listening port
- **Start on Boot**: Auto-start Frida server on device boot
- **Real-time Logs**: Monitor server output with filtering

### 🛡️ RASP Detection Module
Native-level detection of anti-Frida techniques:
- `/proc/self/maps` scanning for Frida libraries
- `/proc/self/smaps` anonymous executable memory detection
- File descriptor inspection
- Frida default port (27042) detection
- Process tracing (ptrace) detection
- Thread name analysis (gmain, gum-js-loop, pool-frida)
- D-Bus authentication probing
- Environment variable inspection (LD_PRELOAD)
- Suspicious process enumeration

### 📱 Modern UI
- Bottom navigation: Home, Detection, Logs, Settings
- Material Design 3 with Jetpack Compose
- Dark & Light theme support
- Server status indicators with PID and port display

## Requirements

- Android 8.0 (API 26) or higher
- Rooted device with superuser access
- NDK for native detection features

## Installation

### Download APK
1. Go to [Releases](https://github.com/yvikash18/frida-server-manager-android/releases)
2. Download the latest APK
3. Install and grant root permissions

### Build from Source
```bash
git clone https://github.com/yvikash18/frida-server-manager-android.git
cd frida-server-manager-android
./gradlew assembleDebug
```

## Architecture Support

| Architecture | Description |
|--------------|-------------|
| ARM64 | Modern 64-bit devices |
| ARM | Older 32-bit devices |
| x86 | Android emulators |
| x86_64 | 64-bit emulators |

## Permissions

- `INTERNET` - Download from GitHub
- `RECEIVE_BOOT_COMPLETED` - Auto-start feature
- `FOREGROUND_SERVICE` - Background server operation
- `POST_NOTIFICATIONS` - Service notifications
- Superuser access - Server management

## Project Structure

```
app/src/main/
├── java/com/prapps/fridaserverinstaller/
│   ├── FridaInstaller.kt       # Core installation logic
│   ├── FridaInstallerViewModel.kt
│   ├── MainActivity.kt          # Navigation host
│   ├── PreferencesManager.kt    # Settings storage
│   ├── BootReceiver.kt          # Auto-start handler
│   ├── FridaServerService.kt    # Foreground service
│   ├── rasp/                    # Detection module
│   │   ├── RaspDetector.kt
│   │   └── DetectionResult.kt
│   └── ui/                      # Screen composables
│       ├── HomeScreen.kt
│       ├── DetectionScreen.kt
│       ├── LogsScreen.kt
│       └── SettingsScreen.kt
└── cpp/
    ├── CMakeLists.txt
    └── rasp_detector.cpp        # Native detection
```

## Dependencies

- Jetpack Compose - UI framework
- OkHttp - HTTP client
- Gson - JSON parsing
- XZ for Java - Archive extraction
- Navigation Compose - Screen navigation

## Security Notice

This tool is for security research and educational purposes. Use responsibly and only on devices you own or have permission to test.

## Author

**yvikash18** - [GitHub](https://github.com/yvikash18)

## License

MIT License - See [LICENSE](LICENSE)

## Acknowledgments

- [Frida](https://frida.re/) - Dynamic instrumentation toolkit
- Android security research community

---
⭐ Star this repository if you find it useful!