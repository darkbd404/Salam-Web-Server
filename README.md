# Salam Web Server v10.0 — UI Final Update

This update keeps the existing server/file/security system and focuses on the requested final UI/runtime fixes:

- Premium centered Start/Stop server control so the button no longer clips or pushes outside the card.
- 7 server LEDs: sequential startup, then independent continuous pulse/glow; red low-glow when OFF.
- Real-time telemetry cards: CPU, RAM, storage, battery/charging, clients, total requests, requests/min, traffic, uptime and active network interface.
- Live Wi-Fi/mobile/network information and automatic active-interface detection.
- Colorful server-rack app/server icon plus vector icons in the home tiles and bottom navigation.
- Prominent Copy URL action in the server panel.
- cPanel-style file manager layout with root/storage summary, search, file/folder metadata, upload and ZIP tools.
- Foreground-service notification with server status, Open App and Stop Server action.
- Partial wake lock while the server is running to reduce sleep-related interruptions.
- Existing browser control panel, logs/history, security, ZIP and server functions are retained.

## Build
Use the included GitHub Actions workflow (`Build Salam Web Server v10`) with **Run workflow**.

Android background execution is still subject to Android/OEM battery-management policies; foreground service + wake lock improves persistence but cannot guarantee an absolute 24/7 runtime on every device.
