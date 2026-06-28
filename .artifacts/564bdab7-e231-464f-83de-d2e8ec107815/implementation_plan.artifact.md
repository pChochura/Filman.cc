# Implementation Plan - Splash Screen and Icons

Add a modern splash screen using the `androidx.core:core-splashscreen` API, a TV banner for Android TV compatibility, and updated adaptive icons with a "play button" theme.

## User Review Required

> [!IMPORTANT]
> The app icons will be changed from the default Android logo to a "play button" design.
> A TV banner will be added, which is required for the app to be correctly displayed on Android TV leanback launchers.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///Users/pipistrelus/AndroidStudioProjects/filman.cc/gradle/libs.versions.toml)
- Add `androidx-core-splashscreen` dependency version and library definition.

#### [MODIFY] [build.gradle.kts](file:///Users/pipistrelus/AndroidStudioProjects/filman.cc/app/build.gradle.kts)
- Add `androidx-core-splashscreen` implementation.

### Resources

#### [MODIFY] [ic_launcher_foreground.xml](file:///Users/pipistrelus/AndroidStudioProjects/filman.cc/app/src/main/res/drawable/ic_launcher_foreground.xml)
- Replace the Android logo with a play button vector icon.

#### [MODIFY] [ic_launcher_background.xml](file:///Users/pipistrelus/AndroidStudioProjects/filman.cc/app/src/main/res/drawable/ic_launcher_background.xml)
- Update the background color to a darker theme (e.g., dark grey or black) suitable for a media app.

#### [NEW] [banner.xml](file:///Users/pipistrelus/AndroidStudioProjects/filman.cc/app/src/main/res/drawable/banner.xml)
- Create a vector banner for Android TV (320x180 dp).

#### [MODIFY] [themes.xml](file:///Users/pipistrelus/AndroidStudioProjects/filman.cc/app/src/main/res/values/themes.xml)
- Define `Theme.App.Starting` inheriting from `Theme.SplashScreen`.
- Configure splash screen icon and background.
- Set `postSplashScreenTheme` to the main app theme.

### Manifest and Code

#### [MODIFY] [AndroidManifest.xml](file:///Users/pipistrelus/AndroidStudioProjects/filman.cc/app/src/main/AndroidManifest.xml)
- Set `android:theme` for `MainActivity` to `@style/Theme.App.Starting`.
- Add `android:banner="@drawable/banner"` to the `<application>` tag.

#### [MODIFY] [MainActivity.kt](file:///Users/pipistrelus/AndroidStudioProjects/filman.cc/app/src/main/java/com/example/filman/MainActivity.kt)
- Call `installSplashScreen()` before `super.onCreate()`.

## Verification Plan

### Automated Tests
- Build the project to ensure no resource or dependency conflicts.
- `gradlew assembleDebug`

### Manual Verification
- Deploy to an Android device/emulator to verify the splash screen and adaptive icon.
- Deploy to an Android TV emulator to verify the leanback banner.
