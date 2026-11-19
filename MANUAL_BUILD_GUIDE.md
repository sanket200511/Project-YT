# Vista Project — Complete Offline & Independent Architecture Guide
**Author:** Sanket Kurve  
**System:** Vista YouTube Fleet & Engine  

---

## Table of Contents
1. [Will the App Stop Working in the Future? (The Truth About YouTube Breaks)](#1-will-the-app-stop-working-in-the-future)
2. [Prerequisites & Tools Needed](#2-prerequisites--tools-needed)
3. [Step 1: Extracting Clean Base YouTube APK Directly From Any Phone](#3-step-1-extracting-clean-base-youtube-apk-directly-from-any-phone)
4. [Step 2: Building Vista Patches Locally](#4-step-2-building-vista-patches-locally)
5. [Step 3: Patching Into the Final Vista.apk (100% Offline)](#5-step-3-patching-into-the-final-vistaapk-100-offline)
6. [Step 4: Installing & Signing In with Vista-Services (MicroG)](#6-step-4-installing--signing-in-with-vista-services-microg)
7. [How to Fix YouTube Changes in the Future](#7-how-to-fix-youtube-changes-in-the-future)

---

## 1. Will the App Stop Working in the Future?

### Why do modded YouTube apps sometimes encounter issues after a few months?
Unlike standard apps (like calculators or offline games), YouTube is a **live streaming service run by Google**. Google periodically changes two things on their servers:

1. **Streaming Player Protocol Changes (Buffering / Playback after 30s):**
   * *What Google does:* Google occasionally changes how their media servers authenticate video requests to block third-party clients, causing playback to freeze at the 30-to-60-second mark.
   * *How Vista handles it:* Vista includes a built-in patch called **`SpoofVideoStreamsPatch`** (Client Spoofing). When Google changes its requirements, Vista automatically spoofs its client identity as an Android TV, iOS app, or web embed. This prevents the playback buffer issue without requiring a complete app rewrite.

2. **Google Play Services (MicroG) Authentication:**
   * *What Google does:* Google updates its OAuth endpoints.
   * *How Vista handles it:* As long as your **Vista-Services (MicroG)** is installed, your login session persists permanently.

### Will your current APK work for a long time?
**Yes.** Under normal circumstances, a stable YouTube base version runs smoothly for **1 to 2+ years** without requiring any updates. You only ever need to rebuild if Google introduces a breaking server-side streaming change that client spoofing cannot bypass.

---

## 2. Prerequisites & Tools Needed

To build everything manually on your PC without relying on GitHub Actions or external websites, you only need:

1. **Java JDK 21 (or 17+)**:
   * Check if installed: open PowerShell or Terminal and type `java -version`.
   * If not installed, download the free [Eclipse Temurin OpenJDK 21](https://adoptium.net/).
2. **Android Platform Tools (ADB)** *(Optional, only if extracting from phone via USB)*:
   * Download the free [Google Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools).
3. **Your Vista Codebase (`d:\yt-morphe`)**:
   * Already on your local drive!

---

## 3. Step 1: Extracting Clean Base YouTube APK Directly From Any Phone

You **never** need third-party APK websites (like Aptoide or APKMirror). You can pull the official Google YouTube app directly from any Android phone that has YouTube installed from the Google Play Store.

### Method A: Using an App on Your Phone (Easiest — 30 Seconds)
1. On your phone, install any free open-source APK extractor from F-Droid or Play Store (e.g., **"ML Manager"** or **"Inware"**).
2. Open the app, search for **YouTube**, and tap **"Extract"** (or "Share APK").
3. Send the extracted `.apk` file to your PC (via USB cable, Google Drive, WhatsApp, or Quick Share).
4. Rename it to `clean-youtube.apk` and place it in your `d:\yt-morphe` directory.

### Method B: Using USB Debugging (ADB on PC)
1. Enable **Developer Options** and **USB Debugging** on your phone.
2. Plug your phone into your PC with a USB cable.
3. Open PowerShell in your platform-tools folder and run:
   ```powershell
   adb devices
   ```
   *(Accept the prompt on your phone screen to authorize).*
4. Find the YouTube installation path on your phone:
   ```powershell
   adb shell pm path com.google.android.youtube
   ```
   You will see an output like:
   `package:/data/app/~~.../com.google.android.youtube-.../base.apk`
5. Pull the file directly to your PC:
   ```powershell
   adb pull /data/app/~~.../com.google.android.youtube-.../base.apk d:\yt-morphe\clean-youtube.apk
   ```

You now have the **100% genuine, unmodified Google base APK**.

---

## 4. Step 2: Building Vista Patches Locally

All your custom code (Vista branding, no ads, background playback, Cloudflare remote telemetry, SponsorBlock) compiles into a single file called a **patch bundle (`.mpp`)**.

1. Open PowerShell and navigate to your project directory:
   ```powershell
   cd d:\yt-morphe
   ```
2. Run the Gradle build command:
   ```powershell
   .\gradlew.bat :patches:buildAndroid
   ```
3. Once the build finishes (`BUILD SUCCESSFUL`), your compiled Vista patches will be located at:
   `d:\yt-morphe\patches\build\libs\patches-1.47.0.mpp`

---

## 5. Step 3: Patching Into the Final Vista.apk (100% Offline)

Now combine your compiled patch bundle with your clean base YouTube APK using the standalone patcher CLI (`morphe-cli.jar`):

```powershell
java -jar morphe-cli.jar patch `
  --patches patches\build\libs\patches-1.47.0.mpp `
  --out Vista.apk `
  --force `
  clean-youtube.apk
```

### What the patcher does:
1. Decompiles `clean-youtube.apk`.
2. Injects your Vista icons, branding, ad blocker, and remote control hooks.
3. Removes Google's built-in trackers and ads.
4. Repackages and cryptographically signs the new application.
5. Saves the final result as **`Vista.apk`** in your current directory.

**Done!** Transfer `Vista.apk` to your phone and install it.

---

## 6. Step 4: Installing & Signing In with Vista-Services (MicroG)

Because Google's official YouTube app relies on proprietary Google Play Services (which normally blocks non-root modified apps from signing in), non-root modified apps use **MicroG (Vista-Services)** to handle Google account login:

1. **Install Vista-Services:**
   * Download and install `Vista-Services.apk` (available from your GitHub Releases).
   * Open Vista Services -> tap **Account** -> Sign in with your Google account.
2. **Install Vista:**
   * Install `Vista.apk`.
   * Open the app. You are now logged in with all your subscriptions, history, and playlists intact, with zero ads and background playback enabled.

---

## 7. How to Fix YouTube Changes in the Future

If Google changes its streaming format 6 or 12 months from now:

1. **If videos stop playing after 30-60 seconds:**
   * Open Vista YouTube -> tap your profile avatar / Settings -> **Vista Settings** -> **Misc / Video Streams**.
   * Toggle the **Client Spoof** setting (e.g., switch between *iOS*, *Android TV*, or *Web* client). This immediately fixes buffering without needing any update.
2. **If Google updates YouTube UI significantly:**
   * Extract a newer base YouTube version from your phone (using Step 1).
   * Update the version number in `Constants.kt` if required.
   * Re-run `./gradlew.bat :patches:buildAndroid` and patch the new base (Steps 2 & 3).
