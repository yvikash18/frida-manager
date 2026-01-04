# Changelog

All notable changes to Frida Manager will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2024-07-19

### Added
- 🚀 **Initial Release** of Frida Manager
- 📥 **Automatic Installation**: Download Frida server from GitHub releases
- 📁 **Manual Installation**: Install from local Frida server binaries (.xz or raw)
- 🔄 **Version Selection**: Choose from available Frida releases (stable and pre-release)
- 📱 **Architecture Detection**: Automatic detection of device architecture (ARM64, ARM, x86, x86_64)
- 🖥️ **Server Management**: Start, stop, and monitor Frida server
- 📊 **Real-time Progress**: Live installation progress and server output logs
- 🎨 **Modern UI**: Material Design 3 interface built with Jetpack Compose
- ✅ **Current Version Display**: Shows currently installed Frida server version
- 🔐 **Root Permission Handling**: Proper superuser access management
- 🔄 **Automatic Replacement**: Removes existing installation when installing new version
- ⚠️ **Error Handling**: Comprehensive error handling with retry functionality
- 📋 **Progress Logs**: Chronological installation and server logs with color coding

### Features
- **Installation Methods**:
  - Download latest from GitHub releases
  - Download specific version from releases
  - Install from local file (supports .xz compressed and raw binaries)
  
- **Server Management**:
  - Start server with real-time output
  - Stop server functionality
  - Monitor server status
  
- **User Interface**:
  - Clean, modern Material Design 3 interface
  - Dynamic current installation status display
  - Progress indicators with download percentages
  - Colored log messages for better readability
  - Responsive layout for different screen sizes

- **Architecture Support**:
  - ARM64 (arm64-v8a) - Modern Android devices
  - ARM (armeabi-v7a) - Older Android devices  
  - x86 - Android emulators and x86 devices
  - x86_64 - 64-bit Android emulators

### Technical Details
- **Built with**: Kotlin, Jetpack Compose, Material Design 3
- **Minimum Android**: API 24 (Android 7.0)
- **Target Android**: API 34 (Android 14)
- **Dependencies**: OkHttp for networking, Gson for JSON parsing, XZ for Java for archive extraction
- **Permissions**: Internet, storage access, superuser access

### Security
- Verifies file integrity during downloads
- Validates Frida binary format before installation
- Secure handling of root permissions
- No sensitive data storage

## [Unreleased]

### Planned Features
- APK signature verification
- Scheduled Frida server updates
- Multiple server configurations
- Export/import settings
- Dark/light theme toggle
- Network interface selection for server binding

---

**Repository**: https://github.com/piyush2947/frida-server-manager  
**License**: MIT  
**Author**: Piyush