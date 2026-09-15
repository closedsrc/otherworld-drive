# Otherworld Drive — Mobile

Google-Photos-style Android client for a **ddrive** (Discord-Free-Cloud) server.
Photos and videos back up automatically and the gallery streams them straight
off your own server, in the same design language as the web dashboard.

[Back to the desktop repo](../Discord-Free-Cloud/README.md)

---

## What it does

- **Auto backup**: scans MediaStore every 15 minutes (WorkManager) and uploads
  new photos and videos to `Mobile Backup/<date>` on the server, sequentially,
  one file at a time. Wi-Fi-only by default, battery-not-low gate, exponential
  backoff, max 40 files per run.
- **Background sync**: also re-arms after reboot (BootReceiver) and backs up on
  every app open when there is something waiting.
- **Gallery**: a 3-column grid of everything backed up, thumbnails streamed
  from the server's `/api/thumb`, images opened as `/api/preview`, videos
  streamed from `/api/download/file` with range support.
- **Same look as the web dashboard**: warm paper palette, terracotta accent,
  Instrument Serif wordmark + Hanken Grotesk UI type, light and dark themes.

## Setup

1. In the ddrive dashboard, mint a **write** API token (Create token → write).
2. Install `app-debug.apk` on the phone, open the app, tap the gear.
3. Enter the server URL (e.g. `https://drive.example.com`) and the write token,
   pick Wi-Fi-only or not, tap **Connect and start backup**.
4. Grant photo and notification permissions when asked. The app creates the
   `Mobile Backup/` folder tree on the server and schedules the periodic job.

## Low-usage design

- Sequential uploads, 2-concurrent-decode cap for thumbnails, LRU bitmap cache
  at 1/8 heap, no disk cache (the server caches renditions itself).
- The periodic job exits as soon as there is nothing pending; the app holds
  ~30 MB and does not run anything while idle.

## Build

```powershell
cd dfc-mobile
.\gradlew.bat assembleDebug
```

APK lands in `app\build\outputs\apk\debug\`. Android SDK at `C:\Android`,
JDK 17 (Adoptium). Min SDK 26, target SDK 34.
