# Telegram Drive 📱 ☁️

An Android app that automatically backs up your photos and videos to a Telegram group, using Telegram's unlimited storage for groups.

## Features 🌟

- Automatic photo and video backup
- Configurable sync settings
- Battery and network-aware syncing
- Separate topics for images and videos
- Background operation
- Progress tracking and notifications
- Force resume capability for interrupted syncs
- Easy group selection with chat picker

## Setup 🚀

### 1. Create a Telegram Bot
1. Message [@BotFather](https://t.me/botfather) on Telegram
2. Use `/newbot` command to create a new bot
3. Save the bot token provided by BotFather

### 2. Prepare Your Group
1. Create a new group in Telegram
2. Add your bot to the group as an administrator
3. Enable topics in group settings
4. Send at least one message in the group

### 3. Configure the App
1. Install the app
2. Enter your bot token
3. Use "Get Chat ID" to select your group
4. Configure sync settings as needed
5. Start sync!

## Requirements 📋

- Android 7.0 (API 24) or higher
- Internet connection
- Storage permission for media access
- Notification permission for sync status

## Building from Source 🛠️

1. Clone the repository:
```bash
git clone https://gitlab.com/your-username/telegram-drive.git
```
2. Open in Android Studio
3. Build the app
```bash
./gradlew assembleDebug
```

## CI/CD Pipeline

The project uses GitLab CI/CD for automated building and releases. To create a release:

1. Tag a commit:
```
bash
git tag v1.0.0
git push origin v1.0.0
```


2. The pipeline will:
   - Build debug APK
   - Run tests
   - Create release APK (manual trigger)
   - Create GitLab release (manual trigger)

## Configuration ⚙️

### Sync Settings
- WiFi-only mode
- Battery level threshold
- Sync frequency
- File type filters

### Performance Settings
- Background sync limits
- Retry behavior
- Network timeout

## Privacy 🔒

- All data is sent directly to your Telegram group
- No third-party servers involved
- Bot token stays on your device
- Open source for transparency

## Contributing 🤝

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Merge Request

## Acknowledgments 👏

- [Telegram Bot API](https://core.telegram.org/bots/api)
- [Android WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
- [Material Design Components](https://material.io/develop/android)

## Support 💬

For support, feature requests, or bug reports:
1. Check existing [Issues](../../issues)
2. Create a new issue if needed
3. Join our [Telegram Group](https://t.me/your_support_group) for discussions

---
Made with ❤️ for the Telegram community