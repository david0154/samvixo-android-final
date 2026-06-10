/**
 * Samvixo Cloud Functions — functions/index.js
 *
 * Exports:
 *  1. approveWebSession   — HTTPS Callable: Android confirms QR web login
 *  2. cleanExpiredSessions — Scheduled: purge old web_sessions every 10 min
 *  3. sendMessageNotification — Firestore trigger: push FCM on new message
 *  4. cleanupExpiredStatuses  — Scheduled: delete statuses older than 24 h
 */

const functions = require('firebase-functions');
const admin     = require('firebase-admin');

if (!admin.apps.length) admin.initializeApp();

const db = admin.firestore();

// ─────────────────────────────────────────────────────────────────────────────
// 1. approveWebSession — called by Android after QR confirmation
// ─────────────────────────────────────────────────────────────────────────────
exports.approveWebSession = functions
  .region('asia-south1')
  .https.onCall(async (data, context) => {

    if (!context.auth) {
      throw new functions.https.HttpsError(
        'unauthenticated', 'You must be signed in to approve a web session.'
      );
    }

    const uid       = context.auth.uid;
    const sessionId = data.sessionId;

    if (!sessionId || typeof sessionId !== 'string') {
      throw new functions.https.HttpsError(
        'invalid-argument', 'sessionId is required.'
      );
    }

    const sessionRef = db.collection('web_sessions').doc(sessionId);
    const sessionDoc = await sessionRef.get();

    if (!sessionDoc.exists) {
      throw new functions.https.HttpsError(
        'not-found', 'Session not found. It may have expired.'
      );
    }

    const session = sessionDoc.data();

    if (!['pending', 'scanned'].includes(session.status)) {
      throw new functions.https.HttpsError(
        'failed-precondition',
        `Session is already in state: ${session.status}`
      );
    }

    if (session.expireAt) {
      const expireMs = session.expireAt.toMillis
        ? session.expireAt.toMillis()
        : session.expireAt._seconds * 1000;
      if (Date.now() > expireMs) {
        await sessionRef.update({ status: 'expired' });
        throw new functions.https.HttpsError(
          'deadline-exceeded', 'This QR code has expired. Please scan a new one.'
        );
      }
    }

    let customToken;
    try {
      customToken = await admin.auth().createCustomToken(uid);
    } catch (err) {
      console.error('createCustomToken error:', err);
      throw new functions.https.HttpsError(
        'internal', 'Failed to create authentication token.'
      );
    }

    await sessionRef.update({
      status:      'approved',
      uid:         uid,
      customToken: customToken,
      approvedAt:  admin.firestore.FieldValue.serverTimestamp()
    });

    console.log(`Web session approved: ${sessionId} for uid: ${uid}`);
    return { success: true };
  });

// ─────────────────────────────────────────────────────────────────────────────
// 2. cleanExpiredSessions — every 10 minutes
// ─────────────────────────────────────────────────────────────────────────────
exports.cleanExpiredSessions = functions
  .region('asia-south1')
  .pubsub.schedule('every 10 minutes')
  .onRun(async () => {
    const cutoff = admin.firestore.Timestamp.fromMillis(
      Date.now() - 10 * 60 * 1000
    );
    const snap = await db.collection('web_sessions')
      .where('createdAt', '<', cutoff)
      .limit(100)
      .get();
    if (snap.empty) return null;
    const batch = db.batch();
    snap.docs.forEach(doc => batch.delete(doc.ref));
    await batch.commit();
    console.log(`Cleaned ${snap.size} expired web sessions.`);
    return null;
  });

// ─────────────────────────────────────────────────────────────────────────────
// 3. sendMessageNotification — fires on every new chat message
//    Sends FCM push to the recipient if they have a stored token
// ─────────────────────────────────────────────────────────────────────────────
exports.sendMessageNotification = functions
  .region('asia-south1')
  .firestore
  .document('chats/{chatId}/messages/{messageId}')
  .onCreate(async (snap, context) => {
    const msg    = snap.data();
    const chatId = context.params.chatId;

    // sender uid
    const senderUid = msg.senderId || msg.uid || null;
    if (!senderUid) return null;

    // Get chat doc to find the other participant(s)
    const chatDoc = await db.collection('chats').doc(chatId).get();
    if (!chatDoc.exists) return null;
    const chatData = chatDoc.data();

    const members = chatData.members || chatData.participants || [];
    const recipients = members.filter(uid => uid !== senderUid);
    if (!recipients.length) return null;

    // Get sender display name
    let senderName = 'Samvixo';
    try {
      const senderDoc = await db.collection('users').doc(senderUid).get();
      senderName = senderDoc.data()?.displayName || senderDoc.data()?.name || 'Samvixo';
    } catch (_) {}

    // Message body preview
    const bodyText = msg.text || msg.content ||
      (msg.type === 'image'  ? '📷 Photo'  : null) ||
      (msg.type === 'voice'  ? '🎤 Voice note' : null) ||
      (msg.type === 'video'  ? '🎬 Video'  : null) ||
      (msg.type === 'file'   ? '📎 File'   : null) ||
      'New message';

    // Send to each recipient
    const sends = recipients.map(async (recipientUid) => {
      try {
        const tokenDoc = await db.collection('fcm_tokens').doc(recipientUid).get();
        if (!tokenDoc.exists) return;
        const token = tokenDoc.data()?.token;
        if (!token) return;

        await admin.messaging().send({
          token,
          notification: {
            title: senderName,
            body:  bodyText.substring(0, 100)
          },
          data: {
            chatId,
            type:     chatData.type || 'direct',
            senderId: senderUid
          },
          android: {
            priority: 'high',
            notification: {
              channelId: 'samvixo_messages',
              sound:     'default'
            }
          }
        });
      } catch (err) {
        // Invalid/stale token — remove it
        if (err.code === 'messaging/registration-token-not-registered') {
          await db.collection('fcm_tokens').doc(recipientUid).delete();
        }
        console.error(`FCM send failed for ${recipientUid}:`, err.message);
      }
    });

    await Promise.all(sends);
    return null;
  });

// ─────────────────────────────────────────────────────────────────────────────
// 4. cleanupExpiredStatuses — every hour, delete statuses older than 24h
// ─────────────────────────────────────────────────────────────────────────────
exports.cleanupExpiredStatuses = functions
  .region('asia-south1')
  .pubsub.schedule('every 60 minutes')
  .onRun(async () => {
    const cutoff = admin.firestore.Timestamp.fromMillis(
      Date.now() - 24 * 60 * 60 * 1000
    );
    const snap = await db.collection('statuses')
      .where('createdAt', '<', cutoff)
      .limit(200)
      .get();
    if (snap.empty) return null;
    const batch = db.batch();
    snap.docs.forEach(doc => batch.delete(doc.ref));
    await batch.commit();
    console.log(`Deleted ${snap.size} expired statuses.`);
    return null;
  });
