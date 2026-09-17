# Salam Web Server Pro - upgrade patch

Copy these files into the existing `darkbd404/Salam-Web-Server` repository, replacing files with the same paths.

Features in this upgrade:
- Dark professional server dashboard
- Start/stop + configurable port
- LAN URL detection
- Foreground server notification
- Per-request logs: timestamp, IP, method, path, status, user-agent
- Browser admin dashboard at `/__admin`
- Basic-auth password protection
- IP allow-list mode
- Persistent allowed IP list
- File import, new file/folder, edit, delete
- Directory listing
- Custom app icon
- Kotlin dependency conflict workaround

Important:
- Android source changes do NOT update an installed APK automatically.
- Build a new APK after source changes.
- For real automatic update delivery, add a GitHub Releases update checker later.
- "Custom URL" on a LAN can mean a path such as `/site`; a real public domain needs DNS/hosting.
