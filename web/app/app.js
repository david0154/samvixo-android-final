const firebaseConfig = {
  apiKey: "AIzaSyCkNjzAoExKqBax_OEWP_wqCHGXt8O-kbg",
  authDomain: "samvixo.firebaseapp.com",
  databaseURL: "https://samvixo-default-rtdb.asia-southeast1.firebasedatabase.app",
  projectId: "samvixo",
  storageBucket: "samvixo.firebasestorage.app",
  messagingSenderId: "466606868297",
  appId: "1:466606868297:web:4558f7cb72de26dda9fc98",
  measurementId: "G-WDXMD643YN"
};
firebase.initializeApp(firebaseConfig);
const auth    = firebase.auth();
const db      = firebase.firestore();
const storage = firebase.storage();

// ─── State ─────────────────────────────────────────────────────
let currentSessionId    = null;
let currentChatId       = null;
let currentChatName     = '';
let currentChatOtherUid = null;
let allChats            = [];
let allChannels         = [];
let allStatuses         = [];
let selectedCategory    = 'All';
let qrExpireTimer       = null;
let sessionUnsub        = null;
let messagesListener    = null;
let chatsListener       = null;
let typingListener      = null;
let typingTimer         = null;
let statusProgressTimer = null;

const CATEGORIES = ['All','News','Tech','Sports','Music','Education','Gaming','Business'];

// ─── QR LOGIN ──────────────────────────────────────────────
function generateQR() {
  // Tear down any previous session
  if (sessionUnsub) { sessionUnsub(); sessionUnsub = null; }
  clearTimeout(qrExpireTimer);

  const frame  = document.getElementById('qrFrame');
  const status = document.getElementById('qrStatus');
  const errEl  = document.getElementById('qrError');
  document.getElementById('qrRefresh').style.display = 'none';
  frame.innerHTML    = '<div class="spinner"></div>';
  status.textContent = 'Generating QR…';
  errEl.textContent  = '';

  // Reset 5-minute countdown bar
  const fill = document.getElementById('qrCountdownFill');
  if (fill) {
    fill.style.transition = 'none';
    fill.style.width      = '100%';
    setTimeout(() => {
      fill.style.transition = 'width 300s linear';
      fill.style.width      = '0%';
    }, 50);
  }

  // New session token — same format Android expects: UUID string
  currentSessionId = 'web_' + Date.now() + '_' + Math.random().toString(36).slice(2, 9);

  // Write Firestore session doc
  db.collection('web_sessions').doc(currentSessionId).set({
    status:    'pending',
    createdAt: firebase.firestore.FieldValue.serverTimestamp(),
    userAgent: navigator.userAgent
  }).then(() => {
    frame.innerHTML = '';

    // ─ QR payload: plain deep-link URL so Android ZXing reads it directly
    // Android WebQrPanel matches: samvixo://web-login?token=<sessionId>
    const payload = 'samvixo://web-login?token=' + currentSessionId;

    // Render QR using qrcode-generator (already loaded in index.html)
    const qr = qrcode(0, 'M');
    qr.addData(payload);
    qr.make();

    const moduleCount = qr.getModuleCount();
    const QR_SIZE     = 256;
    const QUIET       = 4;
    const totalMods   = moduleCount + QUIET * 2;
    const cellSize    = Math.floor(QR_SIZE / totalMods);
    const offset      = Math.floor((QR_SIZE - cellSize * totalMods) / 2);

    const canvas  = document.createElement('canvas');
    canvas.width  = QR_SIZE;
    canvas.height = QR_SIZE;
    const ctx     = canvas.getContext('2d');
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, QR_SIZE, QR_SIZE);
    ctx.fillStyle = '#000000';
    for (let row = 0; row < moduleCount; row++) {
      for (let col = 0; col < moduleCount; col++) {
        if (qr.isDark(row, col)) {
          ctx.fillRect(
            offset + (col + QUIET) * cellSize,
            offset + (row + QUIET) * cellSize,
            cellSize, cellSize
          );
        }
      }
    }
    canvas.style.width   = '100%';
    canvas.style.height  = '100%';
    canvas.style.display = 'block';
    frame.appendChild(canvas);

    status.textContent = 'Scan with Samvixo Android app';

    // ─ Listen for Firestore status changes (set by Android or Cloud Function)
    sessionUnsub = db.collection('web_sessions').doc(currentSessionId)
      .onSnapshot(doc => {
        if (!doc.exists) return;
        const d = doc.data();

        if (d.status === 'scanned') {
          // Android has scanned; show confirmation UI
          status.innerHTML = '✅ QR Scanned — waiting for phone confirmation…';
          frame.innerHTML =
            '<div style="display:flex;flex-direction:column;align-items:center;gap:12px;padding:20px">' +
            '<div class="spinner" style="border-top-color:#22c55e"></div>' +
            '<span style="color:#22c55e;font-size:13px">Phone is confirming…</span>' +
            '</div>';
        }

        if (d.status === 'approved') {
          status.textContent = '\u2705 Approved! Signing in…';
          // Path A: Cloud Function provided a customToken — use it
          if (d.customToken) {
            auth.signInWithCustomToken(d.customToken)
              .then(() => doc.ref.update({ status: 'consumed' }))
              .catch(e => {
                status.textContent = 'Sign-in failed. Try again.';
                errEl.textContent  = e.message;
                document.getElementById('qrRefresh').style.display = 'inline-block';
              });
          } else if (d.uid) {
            // Path B: Android wrote uid directly (no Cloud Function)
            // We cannot sign in with uid alone — show a message to re-scan
            status.textContent = '\u2705 Session approved. Please ensure Cloud Function is deployed for token sign-in.';
            errEl.textContent  = 'Custom token missing. Deploy approveWebSession Cloud Function.';
            document.getElementById('qrRefresh').style.display = 'inline-block';
          }
          if (sessionUnsub) { sessionUnsub(); sessionUnsub = null; }
        }

        if (d.status === 'rejected') {
          status.textContent = '\u274C Rejected. Refresh to try again.';
          document.getElementById('qrRefresh').style.display = 'inline-block';
          if (sessionUnsub) { sessionUnsub(); sessionUnsub = null; }
        }

        if (d.status === 'expired') {
          status.textContent = 'QR expired. Click Refresh.';
          frame.innerHTML = '<span style="color:#64748b;font-size:12px">Expired</span>';
          document.getElementById('qrRefresh').style.display = 'inline-block';
          if (sessionUnsub) { sessionUnsub(); sessionUnsub = null; }
        }
      });

    // ─ Auto-expire after 5 minutes (matches Android countdown)
    qrExpireTimer = setTimeout(() => {
      if (sessionUnsub) { sessionUnsub(); sessionUnsub = null; }
      status.textContent = 'QR expired. Click Refresh.';
      document.getElementById('qrRefresh').style.display = 'inline-block';
      frame.innerHTML = '<span style="color:#64748b;font-size:12px">Expired</span>';
      db.collection('web_sessions').doc(currentSessionId)
        .update({ status: 'expired' }).catch(() => {});
    }, 300000); // 5 minutes

  }).catch(err => {
    status.textContent = 'Failed to generate QR.';
    errEl.textContent  = '\u26A0 ' + (err.code || '') + ': ' + (err.message || err);
    document.getElementById('qrRefresh').style.display = 'inline-block';
    console.error('QR Firestore error:', err);
  });
}

// ─── AUTH STATE ───────────────────────────────────────────
auth.onAuthStateChanged(user => {
  if (user) {
    showApp(user);
    syncUserToFirestore(user);
  } else {
    showLogin();
  }
});

function syncUserToFirestore(user) {
  const data = {
    uid:      user.uid,
    phone:    user.phoneNumber || '',
    lastSeen: firebase.firestore.FieldValue.serverTimestamp()
  };
  if (user.displayName) data.displayName = user.displayName;
  if (user.photoURL)    data.photoURL    = user.photoURL;
  db.collection('users').doc(user.uid).set(data, { merge: true });
}

function showLogin() {
  document.getElementById('loginScreen').style.display = 'flex';
  document.getElementById('appShell').style.display    = 'none';
  generateQR();
}

function showApp(user) {
  clearTimeout(qrExpireTimer);
  if (sessionUnsub) { sessionUnsub(); sessionUnsub = null; }
  document.getElementById('loginScreen').style.display = 'none';
  document.getElementById('appShell').style.display    = 'flex';
  updateAvatarDisplay(user);
  loadChats(user.uid);
  loadStatuses(user.uid);
  loadChannels(user.uid);
  initCategoryChips();
  setInterval(() => {
    db.collection('users').doc(user.uid)
      .update({ lastSeen: firebase.firestore.FieldValue.serverTimestamp() });
  }, 60000);
}

function updateAvatarDisplay(user) {
  const initial  = (user.displayName || user.phoneNumber || 'U').charAt(0).toUpperCase();
  const avatarEl = document.getElementById('myAvatar');
  if (user.photoURL) {
    avatarEl.innerHTML = `<img src="${user.photoURL}" alt="avatar" />`;
  } else {
    avatarEl.textContent = initial;
  }
  const statusAvatar = document.getElementById('myStatusAvatar');
  if (statusAvatar) statusAvatar.textContent = initial;
}

function logout() {
  if (!confirm('Log out?')) return;
  if (chatsListener)    chatsListener();
  if (messagesListener) messagesListener();
  if (typingListener)   typingListener();
  auth.signOut();
}

// ─── TABS ─────────────────────────────────────────────────
function switchTab(tab) {
  ['chats','status','explore'].forEach(t => {
    const panel = document.getElementById(t + 'Tab');
    const btn   = document.getElementById('tab' + t.charAt(0).toUpperCase() + t.slice(1));
    if (panel) panel.classList.toggle('active', t === tab);
    if (btn)   btn.classList.toggle('active', t === tab);
  });
  if (tab !== 'chats') {
    const cp = document.getElementById('chatPane');
    const wp = document.getElementById('welcomePane');
    if (cp) cp.style.display = 'none';
    if (wp) wp.style.display = 'flex';
  }
}

// ─── CHATS ───────────────────────────────────────────────
function loadChats(uid) {
  if (chatsListener) chatsListener();
  chatsListener = db.collection('chats')
    .where('participants', 'array-contains', uid)
    .orderBy('lastMessageTime', 'desc')
    .onSnapshot(snap => {
      allChats = snap.docs.map(doc => {
        const d = doc.data();
        const names    = d.memberNames || {};
        const otherUid = (d.participants || []).find(m => m !== uid) || '';
        const name     = d.groupName || d.channelName || names[otherUid] || 'Chat';
        return {
          id: doc.id, name, otherUid,
          lastMessage: d.lastMessage || '',
          time:   d.lastMessageTime?.toDate ? d.lastMessageTime.toDate() : null,
          unread: d['unread_' + uid] || 0,
          type:   d.type || 'direct'
        };
      });
      renderChatList(allChats);
    }, err => console.error('Chats error:', err));
}

function renderChatList(chats) {
  const el = document.getElementById('chatList');
  if (!chats.length) {
    el.innerHTML = '<div class="empty-state"><span>\uD83D\uDCAC</span><span>No chats yet</span></div>';
    return;
  }
  el.innerHTML = chats.map(c => {
    const icon    = c.type==='group'?'\uD83D\uDC65':c.type==='channel'?'\uD83D\uDCE1':c.type==='broadcast'?'\uD83D\uDCE2':'';
    const init    = icon || c.name.charAt(0).toUpperCase();
    const timeStr = c.time ? formatTime(c.time) : '';
    const badge   = c.unread > 0 ? `<div class="unread-badge">${c.unread}</div>` : '';
    const active  = c.id === currentChatId ? ' active' : '';
    return `
      <div class="chat-item${active}" onclick="openChat('${c.id}','${esc(c.name)}','${c.type}','${c.otherUid}')">
        <div class="chat-avatar">${init}<div class="online-dot" id="dot_${c.otherUid}" style="display:none"></div></div>
        <div class="chat-info">
          <div class="chat-name">${esc(c.name)}</div>
          <div class="chat-last">${esc(c.lastMessage)}</div>
        </div>
        <div class="chat-meta"><div class="chat-time">${timeStr}</div>${badge}</div>
      </div>`;
  }).join('');
  chats.filter(c => c.type === 'direct' && c.otherUid).forEach(c => checkOnline(c.otherUid));
}

function checkOnline(uid) {
  db.collection('users').doc(uid).get().then(doc => {
    if (!doc.exists) return;
    const lastSeen = doc.data().lastSeen?.toDate?.();
    const online   = lastSeen && (Date.now() - lastSeen.getTime()) < 2 * 60 * 1000;
    const dot = document.getElementById('dot_' + uid);
    if (dot) dot.style.display = online ? 'block' : 'none';
  });
}

function filterChats() {
  const q = document.getElementById('searchInput').value.toLowerCase();
  renderChatList(allChats.filter(c => c.name.toLowerCase().includes(q)));
}

// ─── OPEN CHAT ─────────────────────────────────────────────
function openChat(chatId, name, type, otherUid) {
  currentChatId       = chatId;
  currentChatName     = name;
  currentChatOtherUid = otherUid;

  document.querySelectorAll('.chat-item').forEach(el => el.classList.remove('active'));
  document.querySelector(`[onclick*="'${chatId}'"]`)?.classList.add('active');

  document.getElementById('welcomePane').style.display    = 'none';
  document.getElementById('statusViewPane').style.display = 'none';
  document.getElementById('chatPane').style.display       = 'flex';
  document.getElementById('appShell').classList.add('chat-open');
  document.getElementById('backBtn').style.display = window.innerWidth < 700 ? 'inline-flex' : 'none';

  const icon = type==='group'?'\uD83D\uDC65':type==='channel'?'\uD83D\uDCE1':type==='broadcast'?'\uD83D\uDCE2':'\uD83D\uDC64';
  document.getElementById('chatHeaderAvatar').textContent = icon;
  document.getElementById('chatHeaderName').textContent   = name;
  document.getElementById('chatHeaderSub').textContent    = type === 'direct' ? 'Direct message' : type.charAt(0).toUpperCase() + type.slice(1);

  const uid = auth.currentUser?.uid;
  if (uid) db.collection('chats').doc(chatId).update({ ['unread_' + uid]: 0 }).catch(() => {});
  loadMessages(chatId);
  listenTyping(chatId);
}

function closeChat() {
  document.getElementById('chatPane').style.display    = 'none';
  document.getElementById('welcomePane').style.display = 'flex';
  document.getElementById('appShell').classList.remove('chat-open');
}

// ─── MESSAGES ───────────────────────────────────────────
function loadMessages(chatId) {
  if (messagesListener) messagesListener();
  const area = document.getElementById('messagesArea');
  area.innerHTML = '<div style="display:flex;justify-content:center;padding:30px"><div class="spinner"></div></div>';
  const uid = auth.currentUser?.uid;

  messagesListener = db.collection('chats').doc(chatId)
    .collection('messages')
    .orderBy('timestamp', 'asc')
    .limitToLast(100)
    .onSnapshot(snap => {
      area.innerHTML = '';
      snap.docs.forEach(doc => {
        const m    = doc.data();
        const isMe = m.senderId === uid;
        const div  = document.createElement('div');
        div.className = 'msg ' + (isMe ? 'msg-me' : 'msg-other');
        let inner = '';
        if (!isMe && m.senderName) inner += `<div class="msg-sender">${esc(m.senderName)}</div>`;
        if (m.imageUrl) inner += `<img class="msg-img" src="${m.imageUrl}" onclick="window.open('${m.imageUrl}')" />`;
        if (m.text || m.content) inner += `<div>${esc(m.text || m.content || '')}</div>`;
        const status     = m.status || 'sent';
        const tickTxt    = (status === 'seen'      || status === 'READ')      ? '\u2713\u2713' :
                           (status === 'delivered' || status === 'DELIVERED') ? '\u2713\u2713' : '\u2713';
        const tickColor  = (status === 'seen' || status === 'READ') ? '#34C759' : 'rgba(255,255,255,0.6)';
        const tick = isMe ? `<span class="tick" style="color:${tickColor}">${tickTxt}</span>` : '';
        if (m.timestamp?.toDate) {
          const d = m.timestamp.toDate();
          inner += `<div class="msg-time">${formatTime(d)} ${tick}</div>`;
        }
        div.innerHTML = inner;
        area.appendChild(div);
        if (!isMe && (m.status || 'sent').toLowerCase() === 'sent') {
          doc.ref.update({ status: 'delivered' });
        }
      });
      area.scrollTop = area.scrollHeight;
      if (uid) db.collection('chats').doc(chatId).update({ ['unread_' + uid]: 0 }).catch(() => {});
    }, err => {
      area.innerHTML = '<div style="color:var(--red);padding:20px">\u26A0\uFE0F Could not load messages</div>';
      console.error(err);
    });
}

function markChatSeen(chatId) {
  const uid = auth.currentUser?.uid;
  if (!uid || !chatId) return;
  db.collection('chats').doc(chatId).collection('messages')
    .where('status', '==', 'delivered').limit(50).get()
    .then(snap => {
      if (snap.empty) return;
      const batch = db.batch();
      snap.docs.forEach(doc => { if (doc.data().senderId !== uid) batch.update(doc.ref, { status: 'seen' }); });
      batch.commit();
    });
}

// ─── SEND MESSAGE ─────────────────────────────────────────
function sendMessage() {
  const input = document.getElementById('msgInput');
  const text  = input.value.trim();
  if (!text || !currentChatId) return;
  const uid  = auth.currentUser?.uid;
  const name = auth.currentUser?.displayName || auth.currentUser?.phoneNumber || 'Web User';
  if (!uid) return;
  input.value = ''; input.style.height = 'auto';
  const chatRef = db.collection('chats').doc(currentChatId);
  const msgRef  = chatRef.collection('messages').doc();
  const ts = firebase.firestore.FieldValue.serverTimestamp();
  chatRef.set({
    type: 'direct',
    participants: [uid, currentChatOtherUid].filter(Boolean),
    memberNames: { [uid]: name },
    lastMessage: text, lastMessageTime: ts,
    ['unread_' + currentChatOtherUid]: firebase.firestore.FieldValue.increment(1)
  }, { merge: true });
  msgRef.set({ senderId: uid, senderName: name, text, imageUrl: '', timestamp: ts, status: 'sent' });
  clearTyping();
}

function handleKey(e) {
  if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); }
}

function autoResize(el) {
  el.style.height = 'auto';
  el.style.height = Math.min(el.scrollHeight, 120) + 'px';
}

// ─── SEND IMAGE ─────────────────────────────────────────
function sendImageMessage(input) {
  const file = input.files[0];
  if (!file || !currentChatId) return;
  const uid  = auth.currentUser?.uid;
  const name = auth.currentUser?.displayName || auth.currentUser?.phoneNumber || 'Web User';
  showUploadToast('Uploading image…');
  const ref = storage.ref().child(`chat_media/${currentChatId}/${Date.now()}_${file.name}`);
  ref.put(file).then(() => ref.getDownloadURL()).then(url => {
    const chatRef = db.collection('chats').doc(currentChatId);
    const msgRef  = chatRef.collection('messages').doc();
    const ts = firebase.firestore.FieldValue.serverTimestamp();
    chatRef.set({
      type: 'direct',
      participants: [uid, currentChatOtherUid].filter(Boolean),
      lastMessage: '\uD83D\uDCF7 Photo', lastMessageTime: ts,
      ['unread_' + currentChatOtherUid]: firebase.firestore.FieldValue.increment(1)
    }, { merge: true });
    msgRef.set({ senderId: uid, senderName: name, text: '', imageUrl: url, storageRef: ref.fullPath, timestamp: ts, status: 'sent' });
    hideUploadToast();
  }).catch(() => hideUploadToast());
  input.value = '';
}

// ─── TYPING ──────────────────────────────────────────────
function sendTyping() {
  const uid = auth.currentUser?.uid;
  if (!uid || !currentChatId) return;
  db.collection('typing').doc(currentChatId)
    .set({ [uid]: firebase.firestore.FieldValue.serverTimestamp() }, { merge: true });
  clearTimeout(typingTimer);
  typingTimer = setTimeout(clearTyping, 3000);
}

function clearTyping() {
  const uid = auth.currentUser?.uid;
  if (!uid || !currentChatId) return;
  db.collection('typing').doc(currentChatId)
    .update({ [uid]: firebase.firestore.FieldValue.delete() }).catch(() => {});
}

function listenTyping(chatId) {
  if (typingListener) typingListener();
  const uid = auth.currentUser?.uid;
  const el  = document.getElementById('typingIndicator');
  typingListener = db.collection('typing').doc(chatId).onSnapshot(doc => {
    if (!doc.exists) { el.textContent = ''; return; }
    const data   = doc.data();
    const typers = Object.keys(data).filter(k => k !== uid);
    el.textContent = typers.length ? (currentChatName + ' is typing…') : '';
    if (typers.length === 0) markChatSeen(chatId);
  });
}

// ─── STATUS ──────────────────────────────────────────────
function loadStatuses(uid) {
  const cutoff = new Date(Date.now() - 24 * 60 * 60 * 1000);
  db.collection('statuses')
    .where('timestamp', '>', firebase.firestore.Timestamp.fromDate(cutoff))
    .orderBy('timestamp', 'asc')
    .onSnapshot(snap => {
      allStatuses = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      renderStatuses(uid);
    });
}

function renderStatuses(uid) {
  const list = document.getElementById('statusList');
  const grouped = {};
  allStatuses.forEach(s => {
    if (!grouped[s.userId]) grouped[s.userId] = [];
    grouped[s.userId].push(s);
  });
  const mine = grouped[uid] || [];
  document.getElementById('myStatusSub').textContent =
    mine.length ? `${mine.length} update(s) today` : 'Tap to add status update';
  const contactKeys = Object.keys(grouped).filter(k => k !== uid);
  if (!contactKeys.length) {
    list.innerHTML = '<div class="empty-state" style="height:100px"><span>\uD83C\uDF00</span><span>No updates yet</span></div>';
    return;
  }
  list.innerHTML = contactKeys.map(uId => {
    const statuses = grouped[uId];
    const latest   = statuses[statuses.length - 1];
    const unseen   = statuses.some(s => !(s.seenBy || []).includes(uid));
    const initials = (latest.userName || '?').charAt(0).toUpperCase();
    return `
      <div class="chat-item" onclick="viewStatus('${uId}')">
        <div class="status-ring ${unseen ? '' : 'seen'}" style="flex-shrink:0">
          <div class="status-avatar">${initials}</div>
        </div>
        <div class="chat-info">
          <div class="chat-name">${esc(latest.userName || 'User')}</div>
          <div class="chat-last">${statuses.length} update(s)</div>
        </div>
      </div>`;
  }).join('');
}

function addStatus() { document.getElementById('statusFilePicker').click(); }

function uploadStatus(input) {
  const file = input.files[0];
  if (!file) return;
  const uid  = auth.currentUser?.uid;
  const name = auth.currentUser?.displayName || auth.currentUser?.phoneNumber || 'Me';
  showUploadToast('Uploading status…');
  const ref = storage.ref().child(`statuses/${uid}/${Date.now()}_${file.name}`);
  ref.put(file).then(() => ref.getDownloadURL()).then(url => {
    db.collection('statuses').add({
      userId: uid, userName: name, imageUrl: url, caption: '',
      timestamp: firebase.firestore.FieldValue.serverTimestamp(), seenBy: []
    });
    hideUploadToast();
  }).catch(() => hideUploadToast());
  input.value = '';
}

function viewStatus(userId) {
  const uid      = auth.currentUser?.uid;
  const statuses = allStatuses.filter(s => s.userId === userId);
  if (!statuses.length) return;
  let idx = 0;
  function showAt(i) {
    if (i >= statuses.length) { closeStatusView(); return; }
    const s = statuses[i];
    document.getElementById('welcomePane').style.display    = 'none';
    document.getElementById('chatPane').style.display       = 'none';
    document.getElementById('statusViewPane').style.display = 'flex';
    document.getElementById('statusViewImg').src            = s.imageUrl || '';
    document.getElementById('statusViewUser').textContent   = s.userName || 'User';
    document.getElementById('statusViewCaption').textContent = s.caption || '';
    const ts = s.timestamp?.toDate?.();
    document.getElementById('statusViewTime').textContent = ts ? formatTime(ts) : '';
    const fill = document.getElementById('statusProgressFill');
    fill.style.width = '0%'; fill.style.transition = 'none';
    setTimeout(() => { fill.style.transition = 'width 5s linear'; fill.style.width = '100%'; }, 50);
    clearTimeout(statusProgressTimer);
    statusProgressTimer = setTimeout(() => showAt(i + 1), 5000);
    if (!(s.seenBy || []).includes(uid)) {
      db.collection('statuses').doc(s.id)
        .update({ seenBy: firebase.firestore.FieldValue.arrayUnion(uid) });
    }
  }
  showAt(0);
}

function closeStatusView() {
  clearTimeout(statusProgressTimer);
  document.getElementById('statusViewPane').style.display = 'none';
  document.getElementById('welcomePane').style.display    = 'flex';
}

// ─── EXPLORE / CHANNELS ───────────────────────────────────────
function initCategoryChips() {
  const wrap = document.getElementById('catChips');
  wrap.innerHTML = CATEGORIES.map(c =>
    `<button class="chip ${c==='All'?'active':''}" onclick="selectCategory('${c}')">${c}</button>`
  ).join('');
}

function selectCategory(cat) {
  selectedCategory = cat;
  document.querySelectorAll('.chip').forEach(el =>
    el.classList.toggle('active', el.textContent === cat));
  renderChannels();
}

function loadChannels(uid) {
  db.collection('chats')
    .where('type', '==', 'channel')
    .where('isPublic', '==', true)
    .orderBy('subscriberCount', 'desc')
    .limit(50)
    .onSnapshot(snap => {
      allChannels = snap.docs.map(doc => {
        const d = doc.data();
        return {
          id: doc.id,
          name: d.channelName || d.name || '',
          description: d.description || '',
          imageUrl: d.imageUrl || '',
          subscribers: d.subscriberCount || (d.participants||d.members||[]).length || 0,
          category: d.category || 'General',
          isSubscribed: (d.participants || d.members || []).includes(uid)
        };
      }).filter(c => c.name);
      renderChannels();
    });
}

function renderChannels() {
  const uid = auth.currentUser?.uid;
  const q   = document.getElementById('exploreSearch').value.toLowerCase();
  const filtered = allChannels.filter(c =>
    (selectedCategory === 'All' ||
      c.category.toLowerCase() === selectedCategory.toLowerCase()) &&
    (!q || c.name.toLowerCase().includes(q) ||
      c.description.toLowerCase().includes(q))
  );
  const list = document.getElementById('exploreList');
  if (!filtered.length) {
    list.innerHTML = `<div class="empty-state"><span>\uD83D\uDCE1</span><span>${allChannels.length === 0 ? 'Channels appear here once created' : 'No channels found'}</span></div>`;
    return;
  }
  list.innerHTML = filtered.map(c => {
    const init = c.imageUrl
      ? `<img src="${c.imageUrl}" style="width:46px;height:46px;border-radius:50%;object-fit:cover" />`
      : `<div class="chat-avatar">${c.name.charAt(0).toUpperCase()}</div>`;
    return `
      <div class="channel-card">
        ${init}
        <div style="flex:1;min-width:0">
          <div class="channel-name">${esc(c.name)}</div>
          <div class="channel-desc">${esc(c.description)}</div>
          <div class="channel-meta">${c.subscribers} subscribers \u00B7 ${c.category}</div>
        </div>
        <button class="join-btn ${c.isSubscribed?'joined':''}" onclick="toggleSubscribe('${c.id}','${c.isSubscribed}')">
          ${c.isSubscribed ? 'Joined' : 'Join'}
        </button>
      </div>`;
  }).join('');
}

function filterChannels() { renderChannels(); }

function toggleSubscribe(channelId, isSubscribed) {
  const uid = auth.currentUser?.uid;
  const ref = db.collection('chats').doc(channelId);
  if (isSubscribed === 'true' || isSubscribed === true) {
    ref.update({
      participants: firebase.firestore.FieldValue.arrayRemove(uid),
      subscriberCount: firebase.firestore.FieldValue.increment(-1)
    });
  } else {
    ref.update({
      participants: firebase.firestore.FieldValue.arrayUnion(uid),
      subscriberCount: firebase.firestore.FieldValue.increment(1)
    });
  }
}

// ─── PROFILE ─────────────────────────────────────────────
function openProfileModal() {
  const user = auth.currentUser;
  if (!user) return;
  document.getElementById('profilePhone').textContent = user.phoneNumber || 'Not set';
  const imgEl   = document.getElementById('profilePhotoImg');
  const initEl  = document.getElementById('profilePhotoInitial');
  const initial = (user.displayName || user.phoneNumber || 'U').charAt(0).toUpperCase();
  if (user.photoURL) {
    imgEl.src = user.photoURL; imgEl.style.display = 'block'; initEl.style.display = 'none';
  } else {
    imgEl.style.display = 'none'; initEl.textContent = initial; initEl.style.display = 'block';
  }
  document.getElementById('photoUploadProgress').textContent = '';
  db.collection('users').doc(user.uid).get().then(doc => {
    document.getElementById('profileName').value  = doc.data()?.displayName || user.displayName || '';
    document.getElementById('profileAbout').value = doc.data()?.about || '';
    const fsPhoto = doc.data()?.photoURL;
    if (fsPhoto && !user.photoURL) {
      imgEl.src = fsPhoto; imgEl.style.display = 'block'; initEl.style.display = 'none';
    }
  });
  document.getElementById('profileModal').classList.add('open');
}

function closeProfileModal() { document.getElementById('profileModal').classList.remove('open'); }

function uploadProfilePhoto(input) {
  const file = input.files[0];
  if (!file) return;
  const user = auth.currentUser;
  if (!user) return;
  const progressEl = document.getElementById('photoUploadProgress');
  const imgEl      = document.getElementById('profilePhotoImg');
  const initEl     = document.getElementById('profilePhotoInitial');
  progressEl.textContent = 'Uploading…';
  const ref = storage.ref().child(`profile_photos/${user.uid}/${Date.now()}_${file.name}`);
  ref.put(file)
    .then(() => ref.getDownloadURL())
    .then(url => user.updateProfile({ photoURL: url })
      .then(() => db.collection('users').doc(user.uid).update({ photoURL: url }))
      .then(() => url)
    )
    .then(url => {
      imgEl.src = url; imgEl.style.display = 'block'; initEl.style.display = 'none';
      const avatarEl = document.getElementById('myAvatar');
      avatarEl.innerHTML = `<img src="${url}" alt="avatar" />`;
      progressEl.textContent = '\u2705 Photo updated!';
      setTimeout(() => { progressEl.textContent = ''; }, 2500);
    })
    .catch(err => {
      progressEl.textContent = '\u274C Upload failed: ' + (err.message || err);
    });
  input.value = '';
}

function saveProfile() {
  const user  = auth.currentUser;
  const name  = document.getElementById('profileName').value.trim();
  const about = document.getElementById('profileAbout').value.trim();
  if (!user) return;
  user.updateProfile({ displayName: name });
  db.collection('users').doc(user.uid)
    .set({ displayName: name, about, phone: user.phoneNumber || '', uid: user.uid }, { merge: true })
    .then(() => {
      closeProfileModal();
      const avatarEl = document.getElementById('myAvatar');
      if (!user.photoURL) avatarEl.textContent = name.charAt(0).toUpperCase() || 'U';
      const statusAvatar = document.getElementById('myStatusAvatar');
      if (statusAvatar) statusAvatar.textContent = name.charAt(0).toUpperCase() || 'U';
    });
}

// ─── NEW CHAT ────────────────────────────────────────────
function openNewChatModal() {
  document.getElementById('newChatModal').classList.add('open');
  document.getElementById('newChatSearch').value = '';
  document.getElementById('userSearchResults').innerHTML = '';
}

function searchUsers() {
  const q   = document.getElementById('newChatSearch').value.trim();
  const uid = auth.currentUser?.uid;
  const res = document.getElementById('userSearchResults');
  if (q.length < 1) { res.innerHTML = ''; return; }
  const lower = q.toLowerCase();
  db.collection('users').where('uid', '!=', uid).limit(30).get().then(snap => {
    const filtered = snap.docs.map(d => d.data())
      .filter(u => (u.displayName || '').toLowerCase().includes(lower) ||
                   (u.phone || '').includes(q));
    renderUserSearchResults(filtered, res);
  });
}

function renderUserSearchResults(users, res) {
  res.innerHTML = users.length
    ? users.map(u =>
        `<div class="chat-item" onclick="startChat('${u.uid}','${esc(u.displayName || u.phone || 'User')}')">
          <div class="chat-avatar">${(u.displayName || u.phone || '?').charAt(0).toUpperCase()}</div>
          <div class="chat-info">
            <div class="chat-name">${esc(u.displayName || 'Unknown')}</div>
            <div class="chat-last">${esc(u.phone || '')}</div>
          </div>
        </div>`
      ).join('')
    : '<div style="padding:16px;color:var(--sub);font-size:13px">No users found</div>';
}

function startChat(otherUid, otherName) {
  const uid = auth.currentUser?.uid;
  if (!uid) return;
  const chatId = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0;
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
  });
  db.collection('chats').doc(chatId).set({
    chatId, type: 'direct',
    participants: [uid, otherUid],
    memberNames: { [uid]: auth.currentUser?.displayName || auth.currentUser?.phoneNumber || 'Web User' },
    lastMessage: '', lastMessageTime: firebase.firestore.FieldValue.serverTimestamp(),
    createdAt: firebase.firestore.FieldValue.serverTimestamp()
  });
  document.getElementById('newChatModal').classList.remove('open');
  switchTab('chats');
  openChat(chatId, otherName, 'direct', otherUid);
}

// ─── HELPERS ─────────────────────────────────────────────
function formatTime(date) {
  const now   = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const d     = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  if (d.getTime() === today.getTime())
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  const yesterday = new Date(today); yesterday.setDate(today.getDate() - 1);
  if (d.getTime() === yesterday.getTime()) return 'Yesterday';
  return date.toLocaleDateString([], { day: '2-digit', month: '2-digit' });
}

function esc(str) {
  return String(str || '')
    .replace(/&/g,'&amp;')
    .replace(/</g,'&lt;')
    .replace(/>/g,'&gt;')
    .replace(/"/g,'&quot;');
}

function showUploadToast(msg) {
  const el = document.getElementById('uploadStatusModal');
  el.textContent = msg; el.classList.add('open');
}
function hideUploadToast() {
  document.getElementById('uploadStatusModal').classList.remove('open');
}

document.addEventListener('click', e => {
  if (e.target.id === 'profileModal') closeProfileModal();
  if (e.target.id === 'newChatModal')
    document.getElementById('newChatModal').classList.remove('open');
});

document.addEventListener('DOMContentLoaded', () => {
  const circle = document.getElementById('profilePhotoCircle');
  if (circle) circle.addEventListener('click', () =>
    document.getElementById('profilePhotoPicker').click());
});

document.addEventListener('visibilitychange', () => {
  if (!document.hidden && currentChatId) markChatSeen(currentChatId);
});
