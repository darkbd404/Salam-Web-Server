# Salam Web Server Pro

A mobile-first Android LAN HTTP server and server-control panel.

## Included
- Start/stop local HTTP server
- Port control (1024–65535)
- Copyable local URL and QR code
- Foreground server notification
- File manager: upload, create, edit, rename, delete
- ZIP extraction from the Android file picker
- Browser admin dashboard at `/__admin`
- Browser file upload
- Live request log polling
- Request/client counters and uptime
- IP allow-list and block-list, including CIDR rules
- Optional HTTP Basic password protection (`admin` user)
- Custom web URL prefix
- Path traversal protection
- Professional dark server-control UI
- Custom server application icon

## Important
This controls the web server created by this app. It does not grant Android root/system privileges.

## Existing repository upgrade
Copy the files from this package into the matching paths in your existing `darkbd404/Salam-Web-Server` repository and commit them. Do not create a second repository.

## Build
GitHub Actions workflow: `.github/workflows/build-apk.yml`.
The workflow builds `app-debug.apk` and uploads it as an artifact.

## Browser dashboard
When the server is running, open:
`http://PHONE_IP:PORT/__admin`

If a password is enabled, the browser uses HTTP Basic Authentication with username `admin`.

## Access rules
- `Blocked IPs`: comma separated exact IPv4/IPv6 addresses or CIDR blocks.
- `Allow-list only`: if enabled, only IPs in `Allowed IPs` can connect (loopback is always allowed).

## Updating the installed APK
Changing source code in GitHub does not update an installed APK automatically. A new APK must be built and installed. A future signed release channel can add an in-app updater; Android still requires a valid package signature, and normal devices require user confirmation for installation.
