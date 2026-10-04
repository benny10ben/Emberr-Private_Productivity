# Privacy Policy

Last updated: October 4, 2026

Emberr is an offline-first notes, calendar and reminder app for Android and Desktop. This policy explains what happens to your data when you use it.

## The Short Version

* Emberr has no accounts, no analytics, no ads and no tracking.
* Your notes, tasks, reminders, attachments and chats are stored only on your device.
* Nothing is sent to me (the developer) or to any server I run. I have no way to see your data.
* Emberr only connects to the internet for features you turn on or use, listed below.

## What Is Stored on Your Device

Everything you create in Emberr stays on your device:

* Notes, tasks, calendar events, reminders, spaces and folders
* Images, documents and voice recordings you attach to notes
* AI chat history and the AI search index built from your notes
* Your settings

On Android this data lives in the app's private storage, and the database is encrypted. On Desktop it lives in the `.emberr` folder in your home folder, and the database is not encrypted. See [SECURITY.md](SECURITY.md) for details.

Saved passwords and keys (sync credentials, AI provider API keys) are kept in your device's secure storage: the Android Keystore on Android, and your operating system's credential manager on Desktop where one is available.

## When Emberr Connects to the Internet

Emberr works fully offline. It only makes network requests in these cases:

| Feature | What is sent | Where it goes |
| --- | --- | --- |
| Update check | A request for the latest version number. No personal data. | GitHub. Can be turned off in Settings. |
| Local AI model download | A download request for the model file. | Hugging Face. Only when you choose to download a model. |
| External AI providers | Your chat message and the parts of your notes needed to answer it. | The provider you set up (OpenAI, Anthropic, Google Gemini, or a custom endpoint), using your own API key. Only when you choose that provider. |
| Bookmarks and web images | A normal web request for the page or image. | The website you linked to. |
| Self-hosted sync | Your notes and attachments, encrypted on your device before upload. | Your own WebDAV server. |
| Device sync | Your notes and attachments, encrypted, over your local network. | Your own paired device. Nothing leaves your local network. |

If you use an external AI provider, that provider's own privacy policy applies to what you send them. Local AI models run entirely on your device and send nothing.

## Android Permissions

| Permission | Why |
| --- | --- |
| Notifications, exact alarms, run at startup | To show reminders on time, including after your phone restarts. |
| Microphone | To record voice notes and for voice input. Speech is recognized on your device when your phone supports it. Otherwise, your phone's speech service handles it under its own privacy policy. |
| Camera | To scan the QR code when pairing with Desktop, and to take photos for your notes. |
| Internet | For the features listed above. |
| Background data sync | To run sync and model downloads in the background. |

## Deleting Your Data

* Go to Settings → Danger Zone → Clear All Data to erase everything Emberr stores on your device.
* Uninstalling the app also removes its data on Android.
* Copies you made yourself (backup files, your sync server, your other devices) are not affected, and you can delete them yourself.

## Children

Emberr does not collect personal information from anyone, including children.

## Changes to This Policy

If this policy changes, the updated version will be published in this file with a new "Last updated" date.

## Contact

Questions about this policy can be asked by opening an issue on the [GitHub repository](https://github.com/benny10ben/Emberr-Private_Productivity/issues).
