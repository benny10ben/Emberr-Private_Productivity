# Emberr: Private Productivity

Emberr is a local-first, privacy-focused, 100% open source notes and productivity app. It combines a **block-based editor, tasks, reminders, and a calendar** in one place, with your data stored on your device by default.

> **Beta:** Emberr is still in beta. Expect some rough edges and occasional bugs.

<img src=".github/assets/mobile_screenshots.png" width="100%" />
<img src=".github/assets/desktop_screenshots.png" width="100%" />

### Why I Built This

Why spend months building this when apps like Notion and Obsidian already exist? Honestly, I just got annoyed.

Most of the popular apps are closed-source, so who knows what they're doing with your data. The open-source ones are cool, but Linux and Android always feel like an afterthought. On top of that, a lot of them are just clunky, slow, and look old.

I started Emberr as a side project to learn more about backend systems and mess around with full-stack stuff. But mainly, I just wanted an app for myself - something private, clean, and fast on the devices I actually use every day.

Figured I might as well share it. If you're also tired of ugly UIs, privacy headaches, or getting ignored as an Android or Linux user, please give Emberr a shot.

## Features

- Block-based editor for notes, checklists, tables, and more
- Attach images, documents, and voice notes to any note
- Daily notes with automatic task rollover
- Built-in calendar and reminders
- Mobile to desktop sync (the data is encrypted so don't worry about using through public wifi)
- Self-host your data on your own server (the data is encrypted on the server)
- Local AI that runs entirely on-device for more privacy or add your own API key for cloud AI providers (Can turn off)
- End-to-end encryption for your data 
- No trackers, no ads, no sharing data to third parties

## Roadmap

### Short term

- UI / UX and performance improvements
- Keyboard shortcuts for the desktop app
- Support for ARM based desktops
- Improved Kanban and gallery views for the database block
- Login/credentials block to store important info (hopefully with autofill too)
- Improved local AI
- Code cleanup

### Long term (Hopefully by end of 2026)

- Online account with cloud backup (end to end encrypted, of course)
- Make each note shareable on the web
- Share the same note, folder, or space with other users
- Support for community plugins (if this project gets many users) (2027/28)
- Release for iOS and macOS (if I'm not broke TT) (2027/28)

## Platforms

Android, Linux and Windows. macOS support is planned.

## Tech Stack

- **UI:** Compose Multiplatform (Android & Desktop)
- **Language:** Kotlin, with Coroutines and Flow for async work
- **Local storage:** Room (KMP) for metadata, SQLCipher for encrypted on-device data
- **Dependency injection:** Koin
- **Networking:** Ktor
- **On-device AI:** llama.cpp based local inference (via llamatik)

## Installation

Every release is on the [Releases](https://github.com/benny10ben/Emberr-Private_Productivity/releases) page.

### Android

**Option 1: Download the APK**

Download [Emberr-android.apk](https://github.com/benny10ben/Emberr-Private_Productivity/releases) and open it on your phone to install it. Android may ask you to allow installing apps from your browser.

**Option 2: Obtainium**

Emberr can be tracked and auto-updated with [Obtainium](https://github.com/ImranR98/Obtainium):

1. Open Obtainium and tap **Add App**.
2. Paste this repository's URL: `https://github.com/benny10ben/Emberr-Private_Productivity`
3. Tap **Add** and Obtainium will pull the latest release and keep it updated.

### Linux

**Option 1: AppImage (one file, nothing to install)**

Download [Emberr-x86_64.AppImage](https://github.com/benny10ben/Emberr-Private_Productivity/releases), then run:

```sh
chmod +x Emberr-x86_64.AppImage
./Emberr-x86_64.AppImage
```

**Option 2: Tarball (adds Emberr to your app menu)**

Download [emberr-x86_64.tar.gz](https://github.com/benny10ben/Emberr-Private_Productivity/releases), then run:

```sh
tar -xzf emberr-x86_64.tar.gz
./emberr-*-x86_64/install.sh
```

It installs for your user only, with no root needed. To remove it, run `uninstall.sh` from the same extracted folder.

### Windows

Download [Emberr-Setup-x86_64.exe](https://github.com/benny10ben/Emberr-Private_Productivity/releases) and run it.

The installer isn't code-signed yet, so Windows may show "Windows protected your PC". Click **More info**, then **Run anyway**.

### Updates

In-App updates are available for linux and windows.

### Build from source

1. Clone the repository: `git clone https://github.com/benny10ben/Emberr-Private_Productivity.git`
2. Open the project in Android Studio and let Gradle sync.
3. Run `./gradlew :app:assembleRelease` to build the APK, or `./gradlew :shared:run` to start the desktop app.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) if you would like to help out.

## License

Emberr is licensed under the GNU Affero General Public License v3.0. See [LICENSE](LICENSE) for details.


Made by - [Benny](https://github.com/benny10ben)
