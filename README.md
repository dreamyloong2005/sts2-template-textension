# STS2 TExtension Template

This project builds TLauncher template extension packages for Slay the Spire 2.
It contains platform-specific extension runtime code for Windows and Android,
plus the `STS2Mobile` .NET patch assembly used by the Android Godot runtime.

## Project Layout

- `build.gradle.kts` - Gradle build and `.textension` packaging tasks.
- `src/commonMain` - Shared template models, state stores, localization, and mod helpers.
- `src/windowsMain` - Windows template entrypoint, pages, Steam depot checks, and local launch support.
- `src/androidMain` - Android template entrypoint, Godot host bridge, runtime checks, and Android assets.
- `STS2Mobile` - .NET 9 Harmony/GodotSharp patch assembly.
- `vendor/maven` - Vendored TLauncher extension API artifacts used by the Gradle build.

## Build

Build all supported extension packages:

```powershell
.\gradlew.bat packageTExtension
```

Build one platform package:

```powershell
.\gradlew.bat packageWindowsTExtension
.\gradlew.bat packageAndroidTExtension
```

Generated packages are written to `build/dist`.

## Requirements

- JDK 17 or newer.
- .NET 9 SDK for building `STS2Mobile`.
- Android SDK and build-tools for Android packaging.
- Slay the Spire 2 game files available locally for the `STS2Mobile` assembly references.

## License

This project is licensed under the MIT License. See `LICENSE` for details.
