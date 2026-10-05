# Security Policy

Emberr is still in beta. This document describes what is actually implemented today, including known gaps, so you can make an informed decision about what to trust it with.

## What Is Encrypted Today

### 1. Local database (Android)

On Android, the Room database is opened through SQLCipher, so the database file on disk is encrypted. The SQLCipher passphrase is a randomly generated 32-byte key, which is itself encrypted with AES-256-GCM using a key held in the Android Keystore (via Tink) and stored in a local file. The passphrase never leaves the device and is not derived from anything an attacker could guess.

**Desktop is different:** the desktop database currently uses a plain, unencrypted SQLite file (`~/.emberr/emberr_database.db`). Anyone with access to that file on disk can read your notes. This is a known gap.

### 2. Self-hosted sync

If you point Emberr at your own WebDAV server:

* The server URL is validated to require HTTPS, with one intentional exception: plain HTTP is allowed for local network addresses (`localhost`, `127.0.0.1`, `192.168.x.x`, and similar private ranges), since that traffic never leaves your LAN.
* Before anything is uploaded, your notes and media are encrypted client-side with AES-256-GCM. The server only ever sees ciphertext, regardless of whether the connection to it is HTTPS or local HTTP.
* The encryption key is derived from your recovery passphrase using PBKDF2-HMAC-SHA256 with 600,000 iterations and a random 16-byte salt.
* Your WebDAV credentials and the derived encryption key are stored using platform secret storage: Android Keystore-backed encryption (Tink) on Android, and the OS credential manager (keyring) on Desktop where available.

### 3. Mobile to desktop sync (local network)

This is a separate mechanism from self-hosted sync. When you pair a phone with the desktop app, the desktop app runs a small local HTTP server on your LAN. That connection is not TLS-encrypted, but every request is:

* Authenticated with an HMAC-SHA256 signature over the request path and a timestamp, checked using a constant-time comparison.
* Protected against replay, requests with an old or future timestamp outside a small window are rejected.
* Encrypted at the application layer: the note and media payloads themselves are encrypted with AES-256-GCM using a shared key exchanged during pairing, independent of the underlying transport.

So while the transport is plain HTTP, the actual content is encrypted and authenticated regardless.

### Other things worth knowing

* API keys for external AI providers (OpenAI, Anthropic, Gemini) are stored through the same secure storage as sync credentials, never in plain preference files.
* On Desktop, if no OS credential manager is available, secret storage falls back to a plaintext file. When this happens, Emberr surfaces a warning in the app so you know your keys are not protected by the OS credential manager on that machine.

## Known Limitations

* Desktop's local database is not encrypted at rest (see above).
* Desktop secret storage can fall back to plaintext if the OS has no usable credential manager.
* LAN sync between mobile and desktop uses plain HTTP as transport (content is still encrypted and authenticated, see above).

These are being tracked and improved as the project matures. If you rely on Emberr for sensitive data on Desktop today, keep this in mind.

## Reporting a Vulnerability

Please do not open a public GitHub issue for security vulnerabilities.

Instead, use GitHub's private vulnerability reporting for this repository: go to the **Security** tab, then **Report a vulnerability**. This opens a private advisory that only maintainers (myself) can see until it is resolved.

Include as much detail as you can: the affected area (database, self-host sync, LAN sync, etc.), steps to reproduce, and the potential impact.

This is currently a small, mostly solo-maintained project, so please be patient. I aim to acknowledge reports within a few days and will keep you updated as a fix is worked on. Once a fix is released, I will credit you in the advisory unless you ask to stay anonymous.
