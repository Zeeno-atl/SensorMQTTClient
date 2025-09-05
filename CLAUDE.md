# Claude Code Instructions for SensorServer

## Project Overview
SensorServer is an Android application written in Kotlin that transforms Android devices into versatile sensor hubs. It provides real-time access to device sensors via WebSocket connections, allowing multiple clients to simultaneously connect and retrieve live sensor data.

### Key Features
- WebSocket server for real-time sensor data streaming
- HTTP server with web dashboard built in Flutter
- Support for all Android sensors (accelerometer, gyroscope, GPS, etc.)
- Multiple simultaneous connections
- Touch screen event streaming
- Zero-configuration networking (mDNS/Zeroconf)
- USB connection via ADB
- Hotspot connectivity

## Project Structure
- **Main Android App**: `/app/src/main/java/github/umer0586/sensorserver/`
- **Web Dashboard**: `/sensors_dashboard/` (Flutter app)
- **Build System**: Gradle with Android plugin
- **Language**: Kotlin with some Java dependencies

## Key Components
1. **WebSocket Server**: `SensorWebSocketServer.kt`
2. **HTTP Server**: `HttpServer.kt` 
3. **Services**: `HttpService.kt`, `WebsocketService.kt`
4. **Activities**: MainActivity, SettingsActivity, etc.
5. **Settings**: `AppSettings.kt` for app configuration

## Development Commands

### Local Development Setup
1. **Java 17 Required**: Set JAVA_HOME to Java 17
   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-amd64
   export PATH=$JAVA_HOME/bin:$PATH
   ```

2. **Android SDK**: Install command line tools and set paths
   ```bash
   # Download and extract Android command line tools
   # Create android-sdk/cmdline-tools/latest/ structure
   # Install required packages: platform-tools, platforms;android-34, build-tools;34.0.0
   
   export ANDROID_HOME=$PWD/android-sdk
   export PATH=$ANDROID_HOME/platform-tools:$PATH
   ```

3. **Create local.properties**:
   ```
   sdk.dir=/path/to/your/android-sdk
   ```

### Build Commands
```bash
# Set environment for build
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
export ANDROID_HOME=$PWD/android-sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH

# Build debug APK
./gradlew assembleDebug --no-daemon

# Build release APK  
./gradlew assembleRelease --no-daemon

# Run tests
./gradlew test

# Check for lint issues
./gradlew lint

# Clean build
./gradlew clean
```

### Flutter Web Dashboard
```bash
# Navigate to dashboard directory
cd sensors_dashboard/

# Install dependencies
flutter pub get

# Build for web
flutter build web --web-renderer canvaskit

# Deploy to Android assets
cd .. && python deploy_web_app.py
```

## Architecture Notes
- Uses AndServer library for HTTP server functionality
- WebSocket implementation via Java-WebSocket library
- JSON parsing with Jackson databind
- Android sensor API integration
- Service-based architecture for background operations

## Development Guidelines
- Follow existing Kotlin code style and conventions
- Use ViewBinding for UI components
- Maintain backward compatibility (minSdk 21)
- Test WebSocket connectivity on local network
- Ensure proper service lifecycle management

## Testing
- Test on physical devices for sensor access
- Verify WebSocket connections from external clients
- Check HTTP server accessibility via browser
- Test various sensor types and data formats

## Dependencies
- Target SDK: 34
- Min SDK: 21
- Kotlin: 1.8.20
- Java WebSocket: 1.5.3
- AndServer: 2.1.12
- Material Design Components

## Common Tasks
1. Adding new sensor support: Modify sensor enumeration and WebSocket handlers
2. UI changes: Update activities/fragments and corresponding layouts
3. Network configuration: Modify server components and settings
4. Web dashboard updates: Work in Flutter project and redeploy assets