# Security Policy

Emberr is an open-source, offline-first app, still in beta. This document describes what is actually implemented today, including known gaps, so you can make an informed decision about what to trust it with. Because the code is open, you can check every claim here against it.

## Supported Versions

Only the latest release receives security fixes. Please update to it before reporting an issue.

## What Is Encrypted Today

### 1. Local databases (Android)

On Android, both databases are opened through SQLCipher, so the files on disk are encrypted: the main database (`emberr_database.db`) and the AI search index (`emberr_ai_index.db`). The SQLCipher passphrase is a randomly generated 32-byte key, which is itself encrypted with AES-256-GCM using a key held in the Android Keystore (via Tink) and stored in a local file. The passphrase never leaves the device and is not derived from anything an attacker could guess.

**Desktop is different:** both desktop databases are plain, unencrypted SQLite files in `~/.emberr/`: `emberr_database.db` and `emberr_ai_index.db` (which holds chunks of your note text for AI search). Anyone with access to those files on disk can read your notes. This is a known gap.

**Attachments:** images, documents and voice notes are stored as regular files and are not encrypted on either platform. On Android they live in the app's private storage, which other apps cannot read. On Desktop they live in `~/.emberr/media`.

### 2. Self-hosted sync

If you point Emberr at your own WebDAV server:

* The server URL is validated to require HTTPS, with one intentional exception: plain HTTP is allowed for local network addresses (`localhost`, `127.0.0.1`, `192.168.x.x`, and similar private ranges), since that traffic never leaves your LAN.
* Before anything is uploaded, it is encrypted client-side with AES-256-GCM. This covers notes, daily notes, attachments, AI chat history, spaces, folders, calendar categories, the sync index, and your AI provider settings including their API keys. The server only ever sees ciphertext, regardless of whether the connection to it is HTTPS or local HTTP.
* When you set up sync, Emberr creates a random 32-byte vault key, and this key encrypts your data. Emberr also generates a 16-character recovery passphrase for you. The vault key is locked with a key derived from that passphrase using PBKDF2-HMAC-SHA256 with 600,000 iterations and a random 16-byte salt. Only the locked copy is stored on the server (`vault.txt`), and a new device needs the recovery passphrase to unlock it.
* Your WebDAV credentials and the vault key are stored using platform secret storage: Android Keystore-backed encryption (Tink) on Android, and the OS credential manager (keyring) on Desktop where available.

### 3. Mobile to desktop sync (local network)

This is a separate mechanism from self-hosted sync. You pair a phone with the desktop app by scanning a QR code, which shares a pairing key between the two devices. The desktop app then runs a small local HTTP server on your LAN. That connection is not TLS-encrypted, but every request is:

* Authenticated with an HMAC-SHA256 signature over the request method, the path and query, a timestamp, and the encrypted body, checked using a constant-time comparison. Replies from the desktop are signed the same way, and the phone rejects replies that fail the check.
* Limited against replay: requests with a timestamp more than 30 seconds in the past or future are rejected.
* Encrypted at the application layer: the note and media payloads themselves are encrypted with AES-256-GCM using the pairing key, independent of the underlying transport.

So while the transport is plain HTTP, the actual content is encrypted and authenticated regardless.

### 4. Desktop app updates

On Linux and Windows, Emberr checks every update before installing it. The update manifest must carry a valid signature from the release signing key, whose public half is built into the app, and each downloaded file must match the SHA-256 hash listed in that manifest.

### Other things worth knowing

* API keys for external AI providers (OpenAI, Anthropic, Gemini, or a custom endpoint) are stored through the same secure storage as sync credentials, never in plain preference files.
* On Desktop, if no OS credential manager is available, secret storage falls back to a plaintext file, `~/.emberr/secrets.properties`, which only your user account can read. When this happens, Emberr surfaces a warning in the app so you know your keys are not protected by the OS credential manager on that machine.

## Known Limitations

* Desktop's local databases are not encrypted at rest (see above).
* Attachments are not encrypted at rest on either platform.
* Backup files are not encrypted. A backup (manual, or automatic on Android) is a ZIP file containing a plain copy of the database and all attachments, so anyone who gets the file can read your notes. Keep backups somewhere you trust.
* Desktop secret storage can fall back to plaintext if the OS has no usable credential manager.
* LAN sync between mobile and desktop uses plain HTTP as transport (content is still encrypted and authenticated, see above). There is no per-request nonce, so a captured request could be replayed within the 30-second window.
* A self-hosted server cannot read your content, but it can see metadata: how many notes, chats and attachments you have, the random IDs in their file names, the dates of your daily notes, file sizes, and when files change.

These are being tracked and improved as the project matures. If you rely on Emberr for sensitive data on Desktop today, keep this in mind.

## Reporting a Vulnerability

Please do not open a public GitHub issue for security vulnerabilities.

Instead, use GitHub's private vulnerability reporting for this repository: go to the **Security** tab, then **Report a vulnerability**. This opens a private advisory that only maintainers (myself) can see until it is resolved.

Include as much detail as you can: the affected area (database, self-host sync, LAN sync, etc.), steps to reproduce, and the potential impact.

This is currently a small, mostly solo-maintained project, so please be patient. I aim to acknowledge reports within a few days and will keep you updated as a fix is worked on. Once a fix is released, I will credit you in the advisory unless you ask to stay anonymous.
