# 🚀 Samvixo — Complete Zero-to-Live Setup Guide

> **Beginner-friendly.** Every step explains what you're doing, why, and exactly how.  
> This guide covers: Android App · Firebase · Cloud Functions · Admin Panel · Web App · Devil AI · Agora · Sarvam AI · Giphy · Google Drive Backup · AdMob

---

## 📝 Table of Contents

1. [Before You Start](#1-before-you-start)
2. [Create Firebase Project](#2-create-firebase-project)
3. [Enable All Firebase Services](#3-enable-all-firebase-services)
4. [Understanding the Config Files](#4-understanding-the-config-files)  ⭐ **New — explains all 3 files**
5. [Firebase Security Rules](#5-firebase-security-rules)
6. [Deploy Rules, Indexes & Hosting](#6-deploy-rules-indexes--hosting)
7. [Download App Config](#7-download-app-config)
8. [Remote Config Keys](#8-remote-config-keys)
9. [Cloud Functions — All 20](#9-cloud-functions)
10. [Devil AI (Ollama)](#10-devil-ai-ollama-integration)
11. [Agora Video Calls](#11-agora-video-calls)
12. [Sarvam AI](#12-sarvam-ai)
13. [AdMob Ads](#13-admob-ads)
14. [Giphy API](#14-giphy-api)
15. [Google Drive Backup](#15-google-drive-backup)
16. [Admin Panel Setup](#16-admin-panel-setup)
17. [Firestore Collections Schema](#17-firestore-collections-schema)
18. [Android App — Dependencies](#18-android-app--dependencies)
19. [Go-Live Checklist](#19-go-live-checklist)
20. [Troubleshooting](#20-troubleshooting)

---

## 1. Before You Start

### Install Node.js v18+

1. Go to [nodejs.org](https://nodejs.org) → download the **LTS** version
2. Run installer → click Next through everything
3. Verify: open terminal → `node --version` → should show `v18.x.x` or higher

### Install Firebase CLI

```bash
npm install -g firebase-tools
firebase --version    # should show 13.x.x or higher
firebase login        # opens browser → sign in with your Google account
```

### Install Android Studio

1. Download from [developer.android.com/studio](https://developer.android.com/studio)
2. Run installer, accept all defaults
3. On first launch: let it download the Android SDK (∼10 min)
4. Also install **JDK 17** from [adoptium.net](https://adoptium.net) → Temurin 17

### Clone This Repo

```bash
git clone https://github.com/david0154/samvixo-android-final.git
cd samvixo-android-final
```

---

## 2. Create Firebase Project

1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. Click **Add project** → name it `samvixo-nexuzy`
3. Enable Google Analytics → Continue → Create project
4. Wait ~30 seconds → click **Continue**

---

## 3. Enable All Firebase Services

### Authentication
- Left menu → **Build → Authentication → Get started**
- **Sign-in method** tab → enable **Phone**
- Also enable **Email/Password** (needed for admin login)

### Firestore Database
- **Build → Firestore Database → Create database**
- Choose **Production mode**
- Location: **asia-south1 (Mumbai)** → Enable

### Realtime Database
- **Build → Realtime Database → Create database**
- Location: us-central1 → **Start in locked mode** → Enable

### Storage
- **Build → Storage → Get started** → Next → Done

### Hosting — Create 2 sites
- **Build → Hosting → Get started** (click through wizard)
- Hosting dashboard → **Add another site**
  - Site 1: `samvixo-app` → used for the public web app
  - Site 2: `samvixo-admin` → used for the admin panel

### Cloud Functions
- **Build → Functions → Get started**
- ⚠️ **Upgrade to Blaze plan** (pay-as-you-go) — required for outbound HTTP calls

### Remote Config
- **Engage → Remote Config → Create configuration**

### App Check, FCM, Crashlytics, Analytics
- **Build → App Check** → register Android app → select **Play Integrity**
- FCM is on by default
- **Release & Monitor → Crashlytics → Enable**
- Analytics enabled during project creation

---

## 4. Understanding the Config Files

> ⭐ **This section explains exactly what the 3 config files in the repo root do, where they're used, and when you need to touch them.**

---

### 📄 `firebase.json` — The Master Deployment Config

**What it is:** The main config file the Firebase CLI reads every time you run `firebase deploy`. It tells the CLI:
- Where your Firestore rules file is
- Where your Firestore indexes file is
- Where your Storage rules file is
- Where your Realtime Database rules file is
- Which folder to upload for each hosting site
- What security headers to add to your web pages

**Where it lives:** Repo root → `firebase.json`

**When you use it:** You never manually edit it in normal development. The file is already configured correctly. It runs automatically when you type any `firebase deploy` command.

**What's inside (explained line by line):**

```json
{
  "firestore": {
    "rules": "firestore.rules",        // ← tells CLI: deploy THIS file as Firestore rules
    "indexes": "firestore.indexes.json" // ← tells CLI: deploy THIS file as Firestore indexes
  },
  "storage": {
    "rules": "storage.rules"           // ← tells CLI: deploy THIS file as Storage rules
  },
  "database": {
    "rules": "database.rules.json"     // ← tells CLI: deploy THIS file as RTDB rules
  },
  "functions": {
    "source": "functions"              // ← your Cloud Functions code is in the /functions folder
  },
  "hosting": [
    {
      "target": "app",                 // ← the site named "samvixo-app" in Firebase Hosting
      "public": "web/app"             // ← upload everything in this local folder to that site
    },
    {
      "target": "admin",              // ← the site named "samvixo-admin" in Firebase Hosting
      "public": "web/admin"           // ← upload everything in this local folder to that site
    }
  ]
}
```

**Do you need to edit it?**  
Only if you rename your Firebase project or hosting sites. If you followed Step 2-3 exactly, leave it as-is.

---

### 📄 `database.rules.json` — Realtime Database Security Rules

**What it is:** The security rules for Firebase **Realtime Database** (RTDB). RTDB is the fast key-value store used for:
- `onlineUsers/` — who is currently online (green dot in chat list)
- `typing/{chatId}/{uid}` — typing indicators ("David is typing…")
- `calls/{callId}` — Agora call signalling

**Where it lives:** Repo root → `database.rules.json`

**When you use it:** Never manually — it's deployed automatically by `firebase deploy --only database`.

**What the rules mean:**

```json
"onlineUsers": {
  ".read": "auth != null",            // Any logged-in user can READ who is online
  "$uid": {
    ".write": "auth != null && auth.uid === $uid", // You can ONLY write YOUR OWN presence
    ".validate": "newData.hasChildren(['status', 'lastSeen'])" // Must send both fields
  }
}
```
> 💡 **Example:** When David opens the app, his phone writes to `onlineUsers/DAVID_UID = { status: "online", lastSeen: 1234567890 }`. Nobody else can write to his entry. Everyone can read that he's online.

```json
"typing": {
  "$chatId": {
    "$uid": {
      ".read": "auth != null",          // Any logged-in user can see typing status
      ".write": "auth != null && auth.uid === $uid" // Only YOU can write YOUR typing status
    }
  }
}
```
> 💡 **Example:** David types in chat `abc123`. His phone writes `typing/abc123/DAVID_UID = true`. Priya's app reads that and shows "David is typing…".

```json
"calls": {
  "$callId": {
    ".read": "auth != null",           // Any logged-in user can read call signalling data
    ".write": "auth != null"           // Any logged-in user can write to calls (for Agora signalling)
  }
}
```
> 💡 **Example:** When David calls Priya, both sides write/read Agora channel name and token info here before the call connects.

```json
"$other": {
  ".read": false, ".write": false    // Everything else in RTDB is LOCKED
}
```

**Do you need to edit it?**  
No. These rules are production-ready. Only change them if you add a new RTDB path.

---

### 📄 `firestore.indexes.json` — Firestore Composite Index Definitions

**What it is:** Definitions of **composite indexes** for Firestore. A composite index is needed when your Firestore query filters/sorts on **more than one field at the same time**.

**Where it lives:** Repo root → `firestore.indexes.json`

**When you use it:** Never manually — deployed by `firebase deploy --only firestore:indexes`.

**Why indexes are needed:**  
Firestore can filter on a single field (e.g., `where("resolved", "==", false)`) without any index. But the moment you combine two fields (e.g., `where("resolved", "==", false)` + `orderBy("queuedAt", "desc")`), Firestore **requires a pre-built index** or the query will throw an error: *"The query requires an index"*.

**What each index does:**

```json
// Index 1 — Reports collection
// Used by: Cloud Function onReportCreated to check "how many unresolved reports does targetUid have?"
{
  "collectionGroup": "reports",
  "fields": [
    { "fieldPath": "targetUid", "order": "ASCENDING" },   // filter: which user was reported?
    { "fieldPath": "resolved",  "order": "ASCENDING" }    // filter: not yet resolved?
  ]
}
```
> Without this: The auto-restrict logic (ban user after 6+ reports) would fail with a Firestore error.

```json
// Index 2 — Admin Review Queue collection
// Used by: Admin panel Report Queue page — "show unresolved items, newest first"
{
  "collectionGroup": "admin_review_queue",
  "fields": [
    { "fieldPath": "resolved",  "order": "ASCENDING" },  // filter: not resolved yet
    { "fieldPath": "queuedAt", "order": "DESCENDING" }  // sort: newest at the top
  ]
}
```
> Without this: The admin report queue page would throw an error instead of showing reports.

```json
// Index 3 — Users collection
// Used by: Admin panel to list "all restricted users"
{
  "collectionGroup": "users",
  "fields": [
    { "fieldPath": "restricted",   "order": "ASCENDING" },  // filter: restricted == true
    { "fieldPath": "restrictedAt", "order": "DESCENDING" }  // sort: most recently restricted first
  ]
}
```

```json
// Index 4 — Chats collection
// Used by: Cloud Function cleanupDisappearingMessages
// finds all chats that have a disappearing timer set
{
  "collectionGroup": "chats",
  "fields": [
    { "fieldPath": "disappearingTimer", "order": "ASCENDING" }
  ]
}
```

**New indexes needed for Phase 2 admin features (already added in repo):**

| Collection | Fields | Used By |
|---|---|---|
| `official_status` | `isScheduled ASC` + `published ASC` + `scheduleAt ASC` | Scheduled Items page + Cloud Function |
| `scheduled_notifications` | `sent ASC` + `scheduleAt ASC` | Scheduled Notifications page + Cloud Function |
| `contact_requests` | `replied ASC` + `createdAt DESC` | Contact Requests filter |
| `users` | `trustScore ASC` | Trust Score low-score list |
| `screenshot_alerts` | `timestamp DESC` | Screenshot Alerts page |

**Do you need to edit it?**  
Only if you add a new Firestore query that combines 2+ fields. Firestore will also auto-generate a "create index" link in the error message if you forget.

---

### 📊 How All 3 Files Connect

```
firebase.json          ←─ CLI reads this first. It's the "map" of all deployments.
    │
    ├── points to → firestore.rules        (Firestore read/write security)
    ├── points to → firestore.indexes.json  (Firestore query speed & composite queries)
    ├── points to → storage.rules           (Cloud Storage file security)
    ├── points to → database.rules.json     (Realtime Database security)
    ├── points to → functions/              (your Cloud Functions code)
    ├── points to → web/app/               (public web app files to upload)
    └── points to → web/admin/             (admin panel files to upload)
```

**When you run `firebase deploy`, it reads `firebase.json` and deploys ALL of the above at once.**  
When you run `firebase deploy --only firestore:rules`, it reads `firebase.json` to find the rules file path, then deploys only that.

---

## 5. Firebase Security Rules

> All rules are already in the repo. You just deploy them (Section 6).

### Firestore Rules Summary (`firestore.rules`)

| Collection | Who can READ | Who can WRITE |
|---|---|---|
| `users/{uid}` | Any logged-in user | Only the owner (their own doc) |
| `admins/{uid}` | Only themselves | Nobody from client (server-only) |
| `chats/{chatId}` | Only participants | Only participants |
| `status/` | Any logged-in user | Only the owner |
| `official_status/` | Any logged-in user | Server-only (admin Cloud Function) |
| `reports/` | Admin only | Any logged-in user (to create) |
| `channels/` | Any logged-in user | Channel owner only |
| `user_drive_tokens/` | Nobody | Nobody (server-only) |
| `admin_review_queue/` | Admin only | Server-only |
| `2fa_secrets/` | Nobody | Nobody (server-only) |
| `contact_requests/` | Owner or admin | Owner (create) or admin (update) |
| `scheduled_notifications/` | Admin only | Admin only |
| `inbox_messages/{uid}/` | Owner only | Server-only |

### Realtime Database Rules (`database.rules.json`) — Already explained in Section 4

### Storage Rules (`storage.rules`) Summary

| Path | Who can read | Who can write | Max size |
|---|---|---|
| `avatars/{uid}/` | Anyone | Owner only | 5 MB |
| `chat_media/{chatId}/` | Any logged-in user | Any logged-in user | 100 MB |
| `status_media/{uid}/` | Any logged-in user | Owner only | 50 MB |
| `transfers/{id}/` | Any logged-in user | Any logged-in user | 500 MB |

---

## 6. Deploy Rules, Indexes & Hosting

```bash
# From repo root:
firebase use samvixo-nexuzy

# Deploy everything at once:
firebase deploy

# OR deploy specific parts:
firebase deploy --only firestore:rules        # Firestore security rules
firebase deploy --only firestore:indexes      # Firestore composite indexes
firebase deploy --only storage                # Cloud Storage rules
firebase deploy --only database               # Realtime Database rules
firebase deploy --only hosting:app            # Public web app
firebase deploy --only hosting:admin          # Admin panel
firebase deploy --only functions              # Cloud Functions
```

> ⭐ **Tip:** The first time, run `firebase deploy` (no flags) to deploy everything at once. After that, use `--only` to re-deploy just what changed.

---

## 7. Download App Config

### Android — google-services.json

1. Firebase Console → ⚙️ **Project Settings**
2. **Your apps** → click Android icon
3. Package name: `com.nexuzy.samvixo` → **Register app**
4. Download `google-services.json`
5. Place it at: **`app/google-services.json`**

> ⚠️ This file is in `.gitignore`. Never commit it to GitHub.

### Web Admin Config

1. Project Settings → **Your apps** → click **`</>`** (Web)
2. App nickname: `samvixo-admin` → Register
3. Copy the `firebaseConfig` object
4. Open `web/admin/index.html` → find the placeholder values (e.g., `YOUR_API_KEY`) → replace all

### .firebaserc

Create this file in the **repo root** (if not already there):

```json
{
  "projects": { "default": "samvixo-nexuzy" },
  "targets": {
    "samvixo-nexuzy": {
      "hosting": {
        "app":   ["samvixo-app"],
        "admin": ["samvixo-admin"]
      }
    }
  }
}
```

> 💡 **What is `.firebaserc`?** It maps the short name `app`/`admin` (used in `firebase.json`) to the actual Firebase Hosting site IDs (`samvixo-app`, `samvixo-admin`). Without this file, `firebase deploy --only hosting:admin` will fail with "hosting target not found".

---

## 8. Remote Config Keys

> Firebase Console → **Engage → Remote Config → Add parameter** → add each key below → **Publish changes**

| Key | Value | Notes |
|---|---|---|
| `AGORA_APP_ID` | Your Agora App ID | From [console.agora.io](https://console.agora.io) |
| `AGORA_APP_CERTIFICATE` | Your Agora certificate | Same place |
| `DEVIL_AI_ENDPOINT` | `https://aiapi.devilpvt.in` | Devil AI base URL |
| `DEVIL_AI_MODEL` | `devil-ai` | Or `gemma3:4b` |
| `OLLAMA_ENDPOINT_URL` | `https://aiapi.devilpvt.in` | Same as DEVIL_AI_ENDPOINT |
| `OLLAMA_SECRET_KEY` | *(leave blank if public)* | Bearer key if your Ollama is protected |
| `SARVAM_API_KEY` | Your Sarvam key | From [dashboard.sarvam.ai](https://dashboard.sarvam.ai) |
| `GIPHY_API_KEY` | Your Giphy key | From [developers.giphy.com](https://developers.giphy.com) |
| `ADMOB_CHANNELS_ENABLED` | `true` | Toggle ads in Channels |
| `ADMOB_AI_CHAT_ENABLED` | `false` | Toggle ads in AI Chat |
| `ADMOB_STATUS_ENABLED` | `true` | Toggle ads in Status |
| `GDRIVE_CLIENT_ID` | Your Google OAuth Client ID | From Google Cloud Console |
| `GDRIVE_CLIENT_SECRET` | Your Google OAuth Secret | Same place |
| `GDRIVE_REDIRECT_URI` | `https://us-central1-samvixo-nexuzy.cloudfunctions.net/driveBackupCallback` | Your CF callback |

> Click **Publish changes** after adding all keys.

---

## 9. Cloud Functions

### Install & Deploy

```bash
cd functions
npm install
npm install otplib          # for admin 2FA
npm install node-cron       # optional if using cron inside CF
cd ..
firebase deploy --only functions
```

### All 20 Functions

| # | Function | Trigger | What it does |
|---|---|---|---|
| 1 | `generateAgoraToken` | Callable | Generates Agora RTC token from Remote Config |
| 2 | `onMessageStatusUpdate` | Firestore write | Deletes disappearing messages after READ |
| 3 | `broadcastAdminNotification` | Callable | Admin-only FCM push to all users |
| 4 | `onOfficialStatusPost` | Firestore write | Sends FCM when admin posts official status |
| 5 | `cleanupExpiredStatus` | Scheduled (1hr) | Deletes expired status + official_status docs |
| 6 | `onReportCreated` | Firestore write | Auto-restricts user at 6+ reports; queues for admin |
| 7 | `updateTrustScore` | Callable | +5 on verify, -10 on report |
| 8 | `cleanupDisappearingMessages` | Scheduled (30min) | Deletes messages older than per-chat timer |
| 9 | `onMediaDeliveryConfirmed` | Firestore write | Deletes Cloud Storage file after DELIVERED |
| 10 | `sarvamAiProxy` | Callable | Server-side Sarvam AI proxy (key never hits client) |
| 11 | `generateQrLoginToken` | Callable | Creates 5-min QR session + custom token |
| 12 | `validateQrSession` | Callable | Validates QR, returns custom token, deletes session |
| 13 | `setSponsoredContent` | Callable | Admin marks status/channel as sponsored |
| 14 | `driveBackupGetAuthUrl` | Callable | Returns Google OAuth URL for Drive connection |
| 15 | `driveBackupCallback` | HTTP | OAuth redirect handler; saves tokens to Firestore |
| 16 | `triggerDriveBackup` | Callable | Exports chats (AES-256-GCM encrypted) to Google Drive |
| 17 | `disconnectDriveBackup` | Callable | Removes Drive tokens from Firestore |
| 18 | `publishScheduledStatuses` | **Scheduled (every 5 min)** | Publishes official_status where `isScheduled=true && scheduleAt <= now` |
| 19 | `sendScheduledNotifications` | **Scheduled (every 5 min)** | Sends FCM for scheduled_notifications where `sent=false && scheduleAt <= now` |
| 20 | `adminAiSuggestReply` | **Callable** | AI-generates a draft reply to a contact request using Ollama |

### New Functions Code (add to `functions/index.js`)

#### Function 18 — Publish Scheduled Official Statuses

```javascript
exports.publishScheduledStatuses = functions.pubsub
  .schedule('every 5 minutes').onRun(async () => {
    const now = admin.firestore.Timestamp.now();
    const snap = await admin.firestore().collection('official_status')
      .where('isScheduled', '==', true)
      .where('published',   '==', false)
      .where('scheduleAt',  '<=', now)
      .get();
    const batch = admin.firestore().batch();
    snap.docs.forEach(d => batch.update(d.ref, { published: true, isScheduled: false }));
    await batch.commit();
    // Also send FCM for each newly published status
    for (const d of snap.docs) {
      const s = d.data();
      await admin.messaging().send({
        notification: { title: 'Samvixo 📣', body: (s.text || 'New official update!').substring(0, 100) },
        topic: 'all_users'
      });
    }
    return null;
  });
```

#### Function 19 — Send Scheduled Notifications

```javascript
exports.sendScheduledNotifications = functions.pubsub
  .schedule('every 5 minutes').onRun(async () => {
    const now = admin.firestore.Timestamp.now();
    const snap = await admin.firestore().collection('scheduled_notifications')
      .where('sent',       '==', false)
      .where('scheduleAt', '<=', now)
      .get();
    const batch = admin.firestore().batch();
    for (const d of snap.docs) {
      const n = d.data();
      try {
        await admin.messaging().send({
          notification: { title: n.title || 'Samvixo', body: n.message || '' },
          topic: n.topic || 'all_users'
        });
        batch.update(d.ref, { sent: true, sentAt: admin.firestore.FieldValue.serverTimestamp() });
      } catch (e) {
        batch.update(d.ref, { error: e.message });
      }
    }
    await batch.commit();
    return null;
  });
```

#### Function 20 — AI Suggest Reply (for Contact Requests)

```javascript
const fetch = require('node-fetch'); // npm install node-fetch@2

exports.adminAiSuggestReply = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Login required');
  // Verify admin
  const adminDoc = await admin.firestore().collection('admins').doc(context.auth.uid).get();
  if (!adminDoc.exists) throw new functions.https.HttpsError('permission-denied', 'Admins only');

  const { subject, message } = data;

  // Get Ollama URL from Remote Config (or use Devil AI default)
  const rc = admin.remoteConfig();
  const template = await rc.getTemplate();
  const ollamaUrl = template.parameters?.OLLAMA_ENDPOINT_URL?.defaultValue?.value
    || 'https://aiapi.devilpvt.in';
  const model = template.parameters?.DEVIL_AI_MODEL?.defaultValue?.value || 'devil-ai';

  const prompt = `You are a professional customer support agent for Samvixo, a secure messaging app by Nexuzy Lab. A user submitted this contact request:\n\nSubject: ${subject}\nMessage: ${message}\n\nWrite a helpful, friendly, concise reply (3-4 sentences). No subject line. Just the reply body.`;

  const resp = await fetch(`${ollamaUrl}/api/generate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ model, prompt, stream: false }),
    timeout: 30000
  });
  const json = await resp.json();
  return { reply: json.response || '' };
});
```

> **After adding new functions:** Run `cd functions && npm install node-fetch@2` then `firebase deploy --only functions`.

### Existing Functions (must also be in functions/index.js)

#### `postOfficialStatus`

```javascript
exports.postOfficialStatus = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Must be authenticated.');
  const adminDoc = await admin.firestore().collection('admins').doc(context.auth.uid).get();
  if (!adminDoc.exists) throw new functions.https.HttpsError('permission-denied', 'Admins only.');
  const { text, mediaUrl, mediaType, expiryHours, sponsored } = data;
  const expiryMs = (expiryHours || 24) * 60 * 60 * 1000;
  const statusRef = await admin.firestore().collection('official_status').add({
    text: text || '',
    mediaUrl: mediaUrl || null,
    mediaType: mediaType || 'text',
    sponsored: sponsored === true,
    postedBy: context.auth.uid,
    published: true,
    isScheduled: false,
    postedAt: admin.firestore.FieldValue.serverTimestamp(),
    expiryTimestamp: Date.now() + expiryMs
  });
  await admin.messaging().send({
    notification: { title: 'Samvixo 📣', body: text ? text.substring(0, 100) : 'New official update!' },
    topic: 'all_users'
  });
  return { success: true, statusId: statusRef.id };
});
```

#### Admin 2FA Functions

```javascript
const { authenticator } = require('otplib');

exports.setupAdmin2FA = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Must be authenticated.');
  const adminDoc = await admin.firestore().collection('admins').doc(context.auth.uid).get();
  if (!adminDoc.exists) throw new functions.https.HttpsError('permission-denied', 'Admins only.');
  const secret = authenticator.generateSecret();
  await admin.firestore().collection('2fa_secrets').doc(context.auth.uid).set({ secret });
  const otpauth = authenticator.keyuri(context.auth.email || 'admin', 'Samvixo', secret);
  return { secret, otpauth };
});

exports.verifyAdmin2FA = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Must be authenticated.');
  const { code } = data;
  const secretDoc = await admin.firestore().collection('2fa_secrets').doc(context.auth.uid).get();
  if (!secretDoc.exists) throw new functions.https.HttpsError('not-found', '2FA not set up.');
  const valid = authenticator.verify({ token: code, secret: secretDoc.data().secret });
  if (!valid) throw new functions.https.HttpsError('permission-denied', 'Invalid code.');
  return { verified: true };
});

exports.confirmAdmin2FA = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Must be authenticated.');
  const { code } = data;
  const secretDoc = await admin.firestore().collection('2fa_secrets').doc(context.auth.uid).get();
  if (!secretDoc.exists) throw new functions.https.HttpsError('not-found', '2FA secret not found.');
  const valid = authenticator.verify({ token: code, secret: secretDoc.data().secret });
  if (!valid) throw new functions.https.HttpsError('permission-denied', 'Wrong code.');
  await admin.firestore().collection('admins').doc(context.auth.uid).update({ twoFactorEnabled: true });
  return { enabled: true };
});

exports.notifyInboxMessage = functions.https.onCall(async (data, context) => {
  const { uid, title, body } = data;
  if (!uid) return { skipped: true };
  const userDoc = await admin.firestore().collection('users').doc(uid).get();
  const fcmToken = userDoc.exists ? userDoc.data().fcmToken : null;
  if (!fcmToken) return { skipped: true };
  await admin.messaging().send({ token: fcmToken, notification: { title, body } });
  return { sent: true };
});
```

---

## 10. Devil AI (Ollama) Integration

> Devil AI is a self-hosted Ollama instance operated by David (Devil One Pvt Ltd). No API key required. Free to use.

### Endpoint

```
Base URL:  https://aiapi.devilpvt.in
Generate:  POST https://aiapi.devilpvt.in/api/generate
Models:    GET  https://aiapi.devilpvt.in/api/tags
```

### Available Models

| Model | Best For |
|---|---|
| `devil-ai` | General chat, Samvixo default |
| `gemma3:4b` | Faster responses, lighter tasks |

### Config.kt (already in repo)

```kotlin
const val DEVIL_AI_BASE_URL = "https://aiapi.devilpvt.in/"
const val DEVIL_AI_MODEL    = "devil-ai"
```

### No API Key Required

Devil AI at `https://aiapi.devilpvt.in` requires **no authentication header**. Just POST to the endpoint.

---

## 11. Agora Video Calls

1. Go to [console.agora.io](https://console.agora.io) → Sign up → **Create a project**
2. Authentication: **Secure mode (App ID + Token)**
3. Copy **App ID** and **Primary Certificate**
4. Add to Remote Config: `AGORA_APP_ID` and `AGORA_APP_CERTIFICATE` → Publish

```kotlin
// Get token from Cloud Function:
Firebase.functions.getHttpsCallable("generateAgoraToken")
    .call(hashMapOf("channelName" to channelId))
    .addOnSuccessListener { result ->
        val data  = result.data as Map<*, *>
        val token = data["token"] as String
        val appId = data["appId"]  as String
        agoraEngine.joinChannel(token, channelId, "", 0)
    }
```

---

## 12. Sarvam AI

1. Go to [dashboard.sarvam.ai](https://dashboard.sarvam.ai) → Sign up → **API Keys → Create new key**
2. Copy key → add to Remote Config as `SARVAM_API_KEY` → Publish

```kotlin
// The API key never leaves the server. Call via Cloud Function:
Firebase.functions.getHttpsCallable("sarvamAiProxy")
    .call(hashMapOf(
        "endpoint" to "translate",
        "payload"  to hashMapOf(
            "input" to userText,
            "source_language_code" to "en-IN",
            "target_language_code" to "hi-IN"
        )
    ))
```

---

## 13. AdMob Ads

1. Go to [admob.google.com](https://admob.google.com) → **Add app** → Android → `Samvixo`
2. Copy **App ID** (format: `ca-app-pub-XXXXXXXX~XXXXXXXXXX`)
3. Create ad units: Banner, Interstitial, Rewarded
4. Open `Config.kt` → replace `ADMOB_APP_ID` and `ADMOB_BANNER_UNIT_ID`

**Add to `AndroidManifest.xml` inside `<application>` tag:**

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-XXXXXXXX~XXXXXXXXXX" />
```

**Toggle ad placements live via Admin Panel → AdMob Flags tab** (no app update needed).

---

## 14. Giphy API

1. Go to [developers.giphy.com](https://developers.giphy.com) → **Create an App** → API
2. Copy **API Key**
3. Add to `Config.kt`: `const val GIPHY_API_KEY = "your_key"`
4. Also add to Remote Config `GIPHY_API_KEY` for live updates

---

## 15. Google Drive Backup

### Step 1 — Google Cloud Console
1. [console.cloud.google.com](https://console.cloud.google.com) → New Project → `Samvixo OAuth`
2. **APIs & Services → Library** → search **Google Drive API** → Enable

### Step 2 — OAuth Consent Screen
1. **APIs & Services → OAuth consent screen** → External → Create
2. Add scopes: `drive.file` + `drive.appdata`
3. Add your email as test user → Save

### Step 3 — Create Credentials
1. **Credentials → Create Credentials → OAuth client ID** → Web application
2. Redirect URI: `https://us-central1-samvixo-nexuzy.cloudfunctions.net/driveBackupCallback`
3. Copy **Client ID** and **Client Secret** → add to Remote Config + `Config.kt`

---

## 16. Admin Panel Setup

### Deploy

```bash
firebase deploy --only hosting:admin
```

Access at: `https://samvixo-admin.web.app`

### First-Time Admin Account

1. Firebase Console → **Authentication → Users → Add user**
   - Email: your admin email
   - Password: strong password
2. Copy the **UID** shown in the Users table
3. Firestore → **Create collection** → `admins` → Document ID: your UID
   - Field: `role` = `superadmin` (string)
   - Field: `username` = `admin` (string)
   - Field: `twoFactorEnabled` = `false` (boolean)

### Admin Panel Features (Phase 1 + Phase 2)

| Tab | Features |
|---|---|
| **Dashboard** | Live online users, total users, pending reports, restricted users, open contact requests, scheduled items count |
| **Report Queue** | Review flagged users; actions: ✅ Resolve / 🚩 Red Flag / ⏸ Suspend (24h/48h/7d/30d with reason) / 🚫 Ban |
| **Trust Scores** | Search user by UID, view trust/ban/suspend/redflag status, apply actions |
| **Screenshot Alerts** | View who took screenshots; apply Red Flag or Suspend |
| **Notifications** | Send FCM now OR schedule for a future date/time |
| **Official Status** | Post now OR schedule for a future date/time; posts visible to all users |
| **Scheduled Items** | Unified view of all pending scheduled statuses + notifications; cancel any |
| **Contact Requests** | View all contact messages; filter by open/replied; **🤖 AI Suggest Reply** button drafts a reply using Devil AI; admin edits and sends to user inbox |
| **Sponsored** | Tag any status/channel as sponsored |
| **Remote Config** | Set Agora, Ollama, Sarvam, Giphy, feature flags |
| **AdMob Flags** | Toggle ad placements per section |
| **Languages** | Enable/disable each Indian language via Sarvam AI |
| **Rate Limiting** | Set per-user message/AI/API rate limits |

### How Scheduled Status Works (end-to-end)

```
Admin Panel                    Firestore                     Cloud Function
    │                              │                              │
    ├─ Schedule for 9:00 PM ────► official_status doc           │
    │  (click ⏰ Schedule btn)      │  isScheduled: true           │
    │                              │  published:   false          │
    │                              │  scheduleAt:  9:00 PM        │
    │                              │                              │
    │                         At 9:00 PM ─────────────────────► publishScheduledStatuses runs
    │                              │  Updates doc:               │
    │                              │  published: true             │
    │                              │  isScheduled: false          │
    │                              │                 Sends FCM ──► All users notified
```

### How AI Reply Works (end-to-end)

```
Admin sees contact request → clicks "🤖 AI Suggest Reply"
  │
  ├─ (if OLLAMA_ENDPOINT_URL set in Remote Config)
  │   → calls Ollama /api/generate directly with subject + message
  │
  └─ (if not set or CORS blocked)
      → calls adminAiSuggestReply Cloud Function
          → Cloud Function reads OLLAMA_ENDPOINT_URL from Remote Config (server-side, no CORS issue)
          → calls Ollama
          → returns AI-drafted reply text to admin panel

Admin edits text (optional) → clicks "📨 Send Reply"
  → Writes to contact_requests/{id} (replied: true, replyText: "...")
  → Writes to inbox_messages/{uid} so user sees reply in-app
  → calls notifyInboxMessage CF → sends FCM push to user
```

### Red Flag vs Suspend vs Ban (difference)

| Action | Firestore fields set | Effect on user | Reversible? |
|---|---|---|---|
| 🚩 **Red Flag** | `redFlagged: true` | Nothing visible to user yet; marks for closer review | Yes — unset manually |
| ⏸ **Suspend** | `restricted: true`, `suspended: true`, `restrictedUntil: timestamp` | User cannot send messages/login until time expires | Auto-reverses after timer |
| 🚫 **Ban** | `banned: true`, `restricted: true` | User is permanently blocked | Manual unban required |

---

## 17. Firestore Collections Schema

```
Firestore
├─ users/{uid}
│   displayName, photoUrl, phone, bio
│   trustScore (int, default 100)
│   restricted, banned, suspended (bool)
│   redFlagged (bool)
│   restrictedUntil (ms timestamp, for suspensions)
│   suspendReason (string)
│   reportCount (int)
│
├─ admins/{uid}
│   role: "superadmin" | "moderator"
│   username, twoFactorEnabled
│
├─ 2fa_secrets/{uid}              ← server-only
│
├─ chats/{chatId}
│   participants: [uid1, uid2]
│   disappearingTimer: 0|86400000|604800000
│   └─ messages/{msgId}
│       text, senderId, sentAt, status, disappearing
│
├─ status/{docId}               ← user stories
├─ official_status/{docId}      ← admin posts
│   text, mediaUrl, authorName
│   published (bool)
│   isScheduled (bool)           ← ⭐ NEW: true until CF publishes it
│   scheduleAt (Timestamp)       ← ⭐ NEW: when to auto-publish
│
├─ channels/{channelId}
├─ reports/{reportId}
├─ admin_review_queue/{uid}
│   uid, reportCount, queuedAt, resolved
│   redFlagged, suspended, banned (bool)
│
├─ contact_requests/{docId}     ← from users
│   uid, email, subject, message
│   replied (bool), replyText, repliedAt
│   createdAt
│
├─ inbox_messages/{docId}       ← ⭐ NEW: admin reply delivered to user
│   recipientUid, fromTeam: true
│   senderName, subject, message
│   read (bool), createdAt
│
├─ scheduled_notifications/{docId}  ← ⭐ NEW
│   title, message, topic
│   scheduleAt (Timestamp)
│   sent (bool), sentAt
│
├─ media_transfers/{transferId}
├─ qr_sessions/{sessionId}
└─ user_drive_tokens/{uid}      ← server-only
```

---

## 18. Android App — Dependencies

```gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'com.google.gms.google-services'
    id 'com.google.firebase.crashlytics'
}

dependencies {
    implementation platform('com.google.firebase:firebase-bom:33.0.0')
    implementation 'com.google.firebase:firebase-auth-ktx'
    implementation 'com.google.firebase:firebase-firestore-ktx'
    implementation 'com.google.firebase:firebase-database-ktx'
    implementation 'com.google.firebase:firebase-messaging-ktx'
    implementation 'com.google.firebase:firebase-storage-ktx'
    implementation 'com.google.firebase:firebase-config-ktx'
    implementation 'com.google.firebase:firebase-crashlytics-ktx'
    implementation 'com.google.firebase:firebase-analytics-ktx'
    implementation 'com.google.firebase:firebase-functions-ktx'
    implementation 'com.google.firebase:firebase-appcheck-playintegrity'
    implementation 'io.agora.rtc:full-sdk:4.3.0'
    implementation 'org.whispersystems:signal-protocol-android:2.8.1'
    implementation 'com.google.mlkit:translate:17.0.2'
    implementation 'com.google.mlkit:text-recognition:16.0.0'
    implementation 'org.osmdroid:osmdroid-android:6.1.18'
    implementation 'com.squareup.retrofit2:retrofit:2.11.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.11.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'
    implementation 'com.google.android.gms:play-services-ads:23.0.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3'
    implementation 'com.github.bumptech.glide:glide:4.16.0'
    implementation 'com.journeyapps:zxing-android-embedded:4.3.0'
}
```

---

## 19. Go-Live Checklist

- [ ] `google-services.json` placed in `app/` (NOT committed to git)
- [ ] `.firebaserc` created with correct project ID and hosting targets
- [ ] `web/admin/index.html` updated with real Firebase config values
- [ ] `Config.kt`: replace all `YOUR_*` placeholders
- [ ] `AdMob App ID` added to `AndroidManifest.xml`
- [ ] All 14 Remote Config keys added and published
- [ ] Firestore rules deployed: `firebase deploy --only firestore:rules`
- [ ] Firestore indexes deployed: `firebase deploy --only firestore:indexes`
- [ ] Storage rules deployed: `firebase deploy --only storage`
- [ ] Realtime Database rules deployed: `firebase deploy --only database`
- [ ] `otplib` and `node-fetch@2` installed in `functions/`
- [ ] All 20 Cloud Functions deployed and visible in Firebase Console
- [ ] Both hosting sites deployed: `firebase deploy --only hosting`
- [ ] Admin UID added to `admins` Firestore collection
- [ ] App Check enabled
- [ ] Test: Devil AI chat → responds from `https://aiapi.devilpvt.in`
- [ ] Test: Agora call → token generated and call connects
- [ ] Test: Schedule an Official Status → verify it publishes at the set time
- [ ] Test: Schedule a Notification → verify it sends at the set time
- [ ] Test: Contact Request AI reply → AI generates draft; send reaches user inbox
- [ ] Test: Red Flag a user → `redFlagged: true` in Firestore
- [ ] Test: Suspend a user → `restricted: true`, `restrictedUntil` set
- [ ] Test: Google Drive backup → connect → back up → file appears in user's Drive
- [ ] Test: Giphy search → results appear in GIF picker
- [ ] Google Drive OAuth: publish app in Google Cloud Console for non-test users

---

## 20. Troubleshooting

| Problem | Fix |
|---|---|
| `firebase deploy` fails: "hosting target not found" | Create `.firebaserc` with the hosting target mappings (Section 7) |
| Firestore: "The query requires an index" | Run `firebase deploy --only firestore:indexes` to deploy `firestore.indexes.json` |
| RTDB rules: reads/writes blocked | Run `firebase deploy --only database` to apply `database.rules.json` |
| Agora token error: "credentials not set" | Add `AGORA_APP_ID` + `AGORA_APP_CERTIFICATE` to Remote Config → Publish |
| Devil AI timeout | Check `https://aiapi.devilpvt.in/api/tags` in browser — must be reachable |
| Admin panel blank after login | Update `firebaseConfig` in `web/admin/index.html` with real values |
| 2FA QR not working | `cd functions && npm install otplib` then `firebase deploy --only functions` |
| AI Suggest Reply fails | Configure `OLLAMA_ENDPOINT_URL` in Remote Config; OR deploy `adminAiSuggestReply` CF |
| Scheduled status not publishing | Deploy `publishScheduledStatuses` CF; ensure `isScheduled=true` and `scheduleAt` is a Firestore Timestamp |
| Scheduled notification not sending | Deploy `sendScheduledNotifications` CF; check `sent=false` in the doc |
| Sarvam proxy error | Add `SARVAM_API_KEY` to Remote Config → Publish |
| Giphy 401 | Replace `YOUR_GIPHY_API_KEY` in `Config.kt` |
| Functions deploy fails: billing | Upgrade to Blaze plan in Firebase Console |
| Firestore permission denied | Check UID is in `admins` collection; run `firebase deploy --only firestore:rules` |
| Drive backup OAuth not working | Add `GDRIVE_CLIENT_ID`, `GDRIVE_CLIENT_SECRET`, `GDRIVE_REDIRECT_URI` to Remote Config |
| Drive works for you, not others | Google Cloud Console → OAuth consent → Publish App (remove from Testing mode) |
| Android build fails | File → Invalidate Caches; ensure JDK 17 in Project Structure |
| App Check blocking dev | Temporarily disable enforcement in Firebase Console → App Check |
| FCM not received | Verify `google-services.json` is at `app/google-services.json` (not root) |

---

*Last updated: June 2026 · Samvixo v1.2 · Devil AI: https://aiapi.devilpvt.in · Nexuzy Lab*
