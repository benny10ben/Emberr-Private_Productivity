# Emberr: Private Productivity

Emberr is a local-first, privacy-focused, 100% open source notes and productivity app. It combines a **block-based editor, tasks, reminders, and a calendar** in one place, with your data stored on your device by default.

> **Beta:** Emberr is still in beta. Expect some rough edges and occasional bugs.

<img src=".github/assets/mobile_screenshots.png" width="100%" />
<img src=".github/assets/desktop_screenshots.png" width="100%" />

### Why I Built This

Why did I spend months building this project when there are already alternatives like Notion, Obsidian, AppFlowy, etc? Well, it comes down to a few reasons.

Some of these apps aren't open source, so you have no real idea what happens to your data behind the scenes. Some are open source and private but neglect entire platforms, Linux and Android are often treated as second class citizens compared to iOS, macOS, and Windows. And some just have bad UI and UX, cluttered, slow, and stuck in a design language from a decade ago.

I started Emberr as a side project, mainly to learn more about full stack development and backend systems (a cloud backup is what I'm building next). It was also just for myself. I wanted something that respected my privacy, ran well on Linux and Android, and actually felt good to use every day.

After thinking about it for a while, I figured, why not put it out there? If you're like me, fed up with apps that don't respect your privacy, apps with bad UI and UX, or developers who treat Linux and Android as an afterthought, Emberr might be for you.

## Features

- Block-based editor for notes, checklists, tables, and more
- Attach images, documents, and voice notes to any note
- Daily notes with automatic task rollover
- Built-in calendar and reminders
- Mobile to desktop sync (the data is encrypted so don't worry about using through public wifi)
- Self-host your it on your own server (the data is encrypted on the server)
- (AI is off by default) Local AI that runs entirely on-device for more privacy or bring your own API key for cloud AI providers
- End-to-end encryption for your data 
- No trackers, no ads, no sharing data to third parties

## Roadmap

### Short term

- UI / UX improvements
- Keyboard shortcuts for the desktop app
- Improved Kanban and gallery views for the database block
- Login/credentials block to store important info (hopefully with autofill too)
- Improved local AI
- Code cleanup

### Long term (Hopefully by end of 2026)

- Online account with cloud backup (end to end encrypted, of course)
- Make each note shareable on the web
- Share the same note, folder, or space with other users
- Support for ARM based desktops
- Support for community plugins (if this project gets many users) (2027)
- Release for iOS and macOS (if I'm not broke TT) (2027)

## Platforms

Android and Desktop. Desktop currently targets Linux, with Windows and macOS support planned.

## Tech Stack

- **UI:** Compose Multiplatform (Android & Desktop)
- **Language:** Kotlin, with Coroutines and Flow for async work
- **Local storage:** Room (KMP) for metadata, SQLCipher for encrypted on-device data
- **Dependency injection:** Koin
- **Networking:** Ktor
- **On-device AI:** llama.cpp based local inference (via llamatik)

## Installation

### Option 1: Download from GitHub

Go to the [Releases](../../releases) page and download the latest APK, then install it on your device.

### Option 2: Obtainium

Emberr can be tracked and auto-updated with [Obtainium](https://github.com/ImranR98/Obtainium):

1. Open Obtainium and tap **Add App**.
2. Paste this repository's URL: `https://github.com/benny10ben/Emberr-Privacy-Notes-Tasks-Calendar`
3. Tap **Add** and Obtainium will pull the latest release and keep it updated.

### Option 3: Build from source

1. Clone the repository: `git clone https://github.com/benny10ben/Emberr-Privacy-Notes-Tasks-Calendar.git`
2. Open the project in Android Studio and let Gradle sync.
3. Run `./gradlew :app:assembleRelease` to build the APK, or run the `app` module directly from Android Studio.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) if you would like to help out.

## License

Emberr is licensed under the GNU Affero General Public License v3.0. See [LICENSE](LICENSE) for details.


Made by - [Benny](https://github.com/benny10ben)
