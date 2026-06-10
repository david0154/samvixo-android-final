# Samvixo

> **Secure Messaging Super-App for India**  
> Built by **Nexuzy Lab** · Package: `samvixo.nexuzy.com`  
> Platform: Android Native (Kotlin) + Web + Desktop  
> Status: Pre-Development / Planning · Version: 1.1

---

## What is Samvixo?

Samvixo is a WhatsApp-style, Signal-grade end-to-end encrypted messaging super-app built specifically for India. It combines:

- 🔐 **Signal Double Ratchet encryption** on every message
- 🤖 **Three-tier AI** — Ollama (self-hosted) + ML Kit (on-device) + Sarvam AI (cloud)
- 📡 **Offline Emergency Mesh** via Bluetooth + Wi-Fi Direct
- 🗣️ **Sarvam AI** for live captions, translation & summaries in all 22 Indian languages
- 📞 **Agora SDK** for encrypted voice and video calls
- ☁️ **Firebase** for auth, storage, and real-time data
- 💸 **Free forever** for users — revenue via B2B APIs and non-intrusive ads

---

## Repository Structure

```
samvixo-android-final/
├── app/                        # Android app (Kotlin + Jetpack Compose)
│   ├── src/main/
│   │   ├── java/samvixo/nexuzy/com/
│   │   │   ├── auth/           # OTP login, device binding, key generation
│   │   │   ├── chat/           # Messaging, E2E encryption, media sharing
│   │   │   ├── calls/          # Agora voice & video calls
│   │   │   ├── status/         # Stories/status with AI captions
│   │   │   ├── channels/       # Channel discovery, news feed
│   │   │   ├── ai/             # Ollama, ML Kit, Sarvam AI integration
│   │   │   ├── maps/           # OSMDroid maps, live location
│   │   │   ├── weather/        # Open-Meteo weather
│   │   │   ├── emergency/      # Bluetooth mesh, Wi-Fi Direct
│   │   │   ├── backup/         # Drive backup + decentralised backup
│   │   │   ├── admin/          # Admin panel API calls
│   │   │   └── ui/             # Jetpack Compose screens + Material 3
│   │   └── res/
│   ├── build.gradle
│   └── google-services.json    # Replace with your Firebase config
├── functions/                  # Firebase Cloud Functions (Node.js)
│   ├── index.js                # Agora token server, push triggers, B2B APIs
│   └── package.json
├── web/                        # Web app + Admin Panel (Firebase Hosting)
│   ├── index.html              # QR login web client
│   └── admin/                  # Admin dashboard
├── SETUP.md                    # Complete setup guide
├── PID.md                      # Product Information Document v1.1
└── DOCS.md                     # Developer documentation
```

---

## Tech Stack

| Layer | Technology | Version/Notes |
|---|---|---|
| Language | Kotlin | 1.9.24 |
| UI | Jetpack Compose + Material 3 | — |
| Local DB | Room | 2.6 |
| Background | WorkManager | Scheduled messages, sync |
| Camera | CameraX | — |
| Media | Media3 ExoPlayer | — |
| On-Device AI | ML Kit | Translation + OCR |
| Paging | Paging 3 | Chat history |
| Preferences | DataStore | User settings |
| Maps | OSMDroid (OpenStreetMap) | Free, no API key |
| Weather | Open-Meteo API | Free, no API key |
| Ads | Google AdMob | Channels, AI, Status only |
| Calls | Agora SDK | App ID + Token required |
| Backend | Firebase (11 services) | See SETUP.md |
| Self-Hosted AI | Ollama | Admin-configured URL |
| Cloud AI | Sarvam AI API | Key in Remote Config |

---

## Firebase Services Used

| Service | Purpose |
|---|---|
| Firebase Authentication | Phone OTP + device binding |
| Cloud Firestore | Messages, chats, profiles, channels |
| Realtime Database | Online presence, typing indicators |
| Cloud Storage | Encrypted media files |
| Cloud Functions | Agora tokens, push triggers, B2B APIs |
| Firebase Cloud Messaging | Push notifications |
| Firebase Hosting | Web app + Admin panel |
| Firebase App Check | API abuse prevention |
| Firebase Analytics | Usage stats |
| Firebase Remote Config | API keys, feature flags |
| Firebase Crashlytics | Crash reporting |

---

## AI Architecture

```
┌─────────────────────────────────────────────────┐
│              SAMVIXO AI STACK                   │
├───────────────┬───────────────┬─────────────────┤
│   TIER 1      │   TIER 2      │   TIER 3        │
│   Ollama      │   ML Kit      │   Sarvam AI     │
│ (Self-Hosted) │  (On-Device)  │  (Cloud API)    │
├───────────────┼───────────────┼─────────────────┤
│ Chat AI       │ Translation   │ Indian langs    │
│ Reply Suggest │ OCR           │ Live captions   │
│ Smart Folders │ Spam detect   │ Status captions │
│ Vault Search  │ Offline AI    │ Msg summaries   │
└───────────────┴───────────────┴─────────────────┘
```

---

## Security Model

- **Key Exchange:** X25519 Elliptic Curve Diffie-Hellman
- **Encryption:** AES-256-GCM
- **Protocol:** Signal Double Ratchet
- **Forward Secrecy:** Perfect Forward Secrecy (PFS)
- **Private Keys:** Stored in Android Keystore — never uploaded
- **Backup:** Zero-knowledge — Nexuzy Lab cannot read backups

---

## Advertisement Policy

✅ **Ads ARE shown in:** Channels feed · AI Chat · Status/Stories viewer · AI section · Discover  
❌ **Ads NEVER in:** Personal chats · Group chats · Secret chats · Voice/Video calls · Profile · Settings

---

## Revenue Model

| Stream | Detail |
|---|---|
| AdMob Ads | Channels, AI chat, status, AI sections |
| B2B API Access | OTP, SMS, Notification, Chat, Voice APIs |
| Sponsored Stories | Brand-promoted status |
| Sponsored Channels | Promoted channel placement |

---

## Quick Start

See **[SETUP.md](./SETUP.md)** for the complete step-by-step setup guide.

---

## License

Proprietary — © 2025 Nexuzy Lab. All rights reserved.
