# Otherworld Drive — Mobile

Android client for a **ddrive** (Discord-Free-Cloud) server. Photos and videos
back up automatically and the gallery streams them straight off your own
server.

[Back to the desktop repo](../Discord-Free-Cloud/README.md)

---

## What it does

- **Auto backup**: scans MediaStore every 15 minutes (WorkManager) and uploads
  new photos and videos to a per-album folder on the server, sequentially,
  one file at a time. Wi-Fi-only by default, battery-not-low gate, exponential
  backoff.
- **Per-device credentials**: on first launch the app exchanges your drive
  password for an access key that authorizes only this phone. The key is
  stored in EncryptedSharedPreferences (Android Keystore) and can be revoked
  from the server's device list without touching any other install. No
  credential is baked into the APK.
- **Background sync**: also re-arms after reboot (BootReceiver) and backs up on
  every app open when there is something waiting.
- **Gallery**: timeline of everything backed up, thumbnails streamed from the
  server's `/api/thumb`, images opened as `/api/preview`, videos streamed from
  `/api/download/file` with range support.
- **File management**: browse the drive, open images and videos, save files to
  your device, rename, share links, and a real trash with restore and
  permanent delete.
- **App lock**: optional biometric/PIN gate using the phone's own screen lock.

## Setup

1. Install the APK and open the app.
2. Grant photo and notification permissions when asked (an explainer screen
   comes first).
3. Enter your drive's address and password, pick Wi-Fi-only or not, tap
   **Connect**. The phone registers itself as a device on the server and
   backups start on their own.
4. To connect a self-hosted server, enter its URL on the same screen.

## Build

```powershell
cd dfc-mobile
.\gradlew.bat assembleDebug      # test build
.\gradlew.bat assembleRelease    # signed with the keystore from local.properties
```

The release keystore lives outside the repository (default
`%USERPROFILE%\.dfc-keystore\release.keystore`); its credentials go in the
gitignored `local.properties` as `dfc.keystore.path / alias /
storePassword / keyPassword`. Without a keystore, `assembleRelease` falls back
to the debug key for local testing. Min SDK 26, target SDK 34, JDK 17.
