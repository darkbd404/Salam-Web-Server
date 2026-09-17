# Salam SIP Server v10

Android local HTTP web server with native dashboard and browser control center.

## Included
- Multi-theme premium UI + animations
- Real server start/stop + foreground notification
- CPU/RAM/storage/network metrics
- Live clients, request log and access history
- Basic web login/password
- IP allowlist/blocklist/unblock, CIDR matching
- Per-IP request rate limiting and client limit
- File manager: upload/download/create/rename/delete/copy/move
- Text editor for website files
- ZIP extraction with path traversal protection
- QR sharing, URL copy, configurable custom display URL
- Browser-side admin panel with 2-second auto refresh
- Developer contact and Messenger link

Custom URL is only a display/alias field; a real public hostname requires DNS/tunnel infrastructure.

The project targets Android 14 (API 34) while compiling against API 35. Android foreground-service rules require the service type and permission.
