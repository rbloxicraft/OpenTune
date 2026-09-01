# OpenTune — Navidrome Edition

<div align="center">
  <img src="https://github.com/Arturo254/OpenTune/blob/master/fastlane/metadata/android/en-US/images/featureGraphic.png" alt="OpenTune banner" width="100%"/>

  ### Advanced YouTube Music **+ Navidrome** client with Material Design 3 for Android

  [![Release](https://img.shields.io/github/v/release/rbloxicraft/OpenTune?style=flat-square&logo=github&color=0D1117&labelColor=161B22)](https://github.com/rbloxicraft/OpenTune/releases)
  [![Build APK](https://img.shields.io/github/actions/workflow/status/rbloxicraft/OpenTune/build-apk.yml?style=flat-square&label=build&labelColor=161B22)](https://github.com/rbloxicraft/OpenTune/actions)
  [![License](https://img.shields.io/github/license/rbloxicraft/OpenTune?style=flat-square&logo=gnu&color=2B3137&labelColor=161B22)](LICENSE)
  [![Platform](https://img.shields.io/badge/Platform-Android%208.0+-3DDC84.svg?style=flat-square&logo=android&logoColor=white&labelColor=161B22)](https://www.android.com)
</div>

---

> **What is this fork?**
>
> This is a fork of [Arturo254/OpenTune](https://github.com/Arturo254/OpenTune) (itself based on InnerTune) that adds a **complete [Navidrome](https://www.navidrome.org) / Subsonic integration**: stream and manage your self-hosted music library right next to YouTube Music, in a single app.

## 🎵 Navidrome Integration

| Feature | Description |
|:--------|:------------|
| **Server connection** | Settings → Integrations → Navidrome server: address (sub-path reverse proxies supported), username, password, live connection test |
| **Dedicated tab** | A "Navidrome" tab in the bottom navigation showing your server playlist (e.g. the one built from your `playlist.m3u`) as a flat, directly playable song list in its exact order |
| **One-tap playback** | Tap a song to play it — the whole playlist becomes the queue; play & shuffle buttons included |
| **Position badges** | Each row shows its playlist position as a small badge over the cover art, so you always know where you are |
| **Instant filtering** | Type a position number (`800`) to jump straight to it, or any title/artist/album to filter the loaded list instantly — server search runs on top |
| **Offline downloads** | Download songs from your server (full quality) for offline listening, alongside YouTube downloads |
| **Data saver** | Optional streaming bitrate cap (64–320 kbps, server-side transcoding) for metered connections |
| **Synced favorites** | The ♥ on a Navidrome song stars it on the server (visible in Feishin, DSub…), and server-side stars arrive as liked |
| **Display orders** | Toggle the list between playlist order (default), title or artist — playback always starts at the right position |
| **Playlist selector** | If the server exposes several playlists, switch between them; the choice is remembered |
| **Technical details** | Song details (format, bitrate, sample rate, file size) from Subsonic metadata, like YouTube songs |
| **Mixed playlists** | Add Navidrome songs to your local playlists and mix them freely with YouTube songs |

Works with any Subsonic-API-compatible server (Navidrome, Airsonic, Gonic…).

## 📥 Installation

1. Go to [Releases](https://github.com/rbloxicraft/OpenTune/releases) and download the APK from the latest build (`app-arm64-debug.apk` for most phones, `app-universal-debug.apk` otherwise)
2. Enable "Install from unknown sources" for your browser
3. Open the APK to install

> **Notes**
> - This build installs as **`com.Arturo254.opentune.debug`** ("OpenTune Debug"), so it can coexist with the official OpenTune without touching its data.
> - All CI-built APKs share one signature; a locally-built debug APK does not — uninstall first if you switch sources.

Every push to `master` triggers an automatic build ([Actions](https://github.com/rbloxicraft/OpenTune/actions)) and refreshes the release APKs.

## ✨ Upstream features

OpenTune is a full-featured YouTube Music client: ad-free playback, background play, account sync, library management, offline mode, synchronized lyrics, silence skipping, volume normalization, tempo & pitch control, dynamic theming, Android Auto, and more — see the [upstream README](https://github.com/Arturo254/OpenTune) for details.

> OpenTune is an independent project and is not affiliated with, sponsored or endorsed by YouTube, Google or Navidrome.

## 🔨 Building from source

```bash
git clone https://github.com/rbloxicraft/OpenTune
cd OpenTune
./gradlew assembleArm64Debug
```

Requirements: JDK 21, Android SDK (platform 36). The APK lands in `app/build/outputs/apk/arm64/debug/`.

The Subsonic client lives in its own Gradle module (`:navidrome`) with unit tests:

```bash
./gradlew :navidrome:test
```

## 🔄 Syncing with upstream

```bash
git fetch upstream
git merge upstream/master
```

## 🙏 Credits

- **[Arturo254/OpenTune](https://github.com/Arturo254/OpenTune)** — the upstream project this fork is based on
- **[InnerTune](https://github.com/z-huang/InnerTune)** and its contributors — the original lineage
- Navidrome integration, CI and APK publishing added in this fork

## 📄 License

GPL-3.0 — see [LICENSE](LICENSE).
