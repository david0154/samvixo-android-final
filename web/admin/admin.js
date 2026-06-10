// ── Firebase Config ─────────────────────────────────────────────────────────
const firebaseConfig = {
  apiKey: "AIzaSyCkNjzAoExKqBax_OEWP_wqCHGXt8O-kbg",
  authDomain: "samvixo.firebaseapp.com",
  databaseURL: "https://samvixo-default-rtdb.asia-southeast1.firebasedatabase.app",
  projectId: "samvixo",
  storageBucket: "samvixo.firebasestorage.app",
  messagingSenderId: "466606868297",
  appId: "1:466606868297:web:8fbf75efa2a3190ba9fc98",
  measurementId: "G-ZXPQLMCXCD"
};
firebase.initializeApp(firebaseConfig);
const auth      = firebase.auth();
const db        = firebase.firestore();
const rtdb      = firebase.database();
const storage   = firebase.storage();
const functions = firebase.functions();

// ── State ───────────────────────────────────────────────────────────────────
let adminRole            = '';
let allUsersCache        = [];
let allReportCache       = [];
let maintOn              = false;
let suspendTargetUid     = null;
let currentReplyDocId    = null;
let currentReplyUid      = null;
let currentReplySubject  = '';
let currentReplyMsg      = '';
let aiSuggestedText      = '';
const featureState       = {};

const RC_KEYS = [
  'AGORA_APP_ID','AGORA_APP_CERTIFICATE','OLLAMA_ENDPOINT_URL',
  'OLLAMA_SECRET_KEY','DEVIL_AI_MODEL','SARVAM_API_KEY','GIPHY_API_KEY',
  'DRIVE_CLIENT_ID','DRIVE_CLIENT_SECRET'
];

const LANGS = [
  {code:'hi-IN',name:'Hindi',flag:'🇮🇳'},{code:'bn-IN',name:'Bengali',flag:'🇮🇳'},
  {code:'te-IN',name:'Telugu',flag:'🇮🇳'},{code:'ta-IN',name:'Tamil',flag:'🇮🇳'},
  {code:'mr-IN',name:'Marathi',flag:'🇮🇳'},{code:'gu-IN',name:'Gujarati',flag:'🇮🇳'},
  {code:'kn-IN',name:'Kannada',flag:'🇮🇳'},{code:'ml-IN',name:'Malayalam',flag:'🇮🇳'},
  {code:'pa-IN',name:'Punjabi',flag:'🇮🇳'},{code:'or-IN',name:'Odia',flag:'🇮🇳'},
  {code:'as-IN',name:'Assamese',flag:'🇮🇳'},{code:'ur-IN',name:'Urdu',flag:'🇮🇳'},
  {code:'en-IN',name:'English (India)',flag:'🇬🇧'}
];

// ── Helper: check if current role is super admin ────────────────────────────
function isSuperAdmin() {
  return adminRole === 'super_admin' || adminRole === 'superadmin';
}

// ── Clock ───────────────────────────────────────────────────────────────────
setInterval(() => {
  const el = document.getElementById('clockDisplay');
  if (el) el.textContent = new Date().toLocaleTimeString([], {hour:'2-digit',minute:'2-digit',second:'2-digit'});
}, 1000);

function togglePnUid(val) {
  document.getElementById('pnUidRow').classList.toggle('hidden', val !== 'uid');
}

// ════════════════════════════════════════════════════════════════════════════
// AUTH — Email + Password login (no custom claim needed)
// ════════════════════════════════════════════════════════════════════════════
function hideLoading() {
  const ls = document.getElementById('loadingScreen');
  if (ls) ls.style.display = 'none';
}
setTimeout(hideLoading, 5000);

auth.onAuthStateChanged(async user => {
  hideLoading();
  if (!user) { showLogin(); return; }
  try {
    const doc = await db.collection('admin_users').doc(user.uid).get();
    if (!doc.exists) { auth.signOut(); showLogin(); return; }
    const role = doc.data().role || '';
    const validRoles = ['admin','superadmin','super_admin','second_admin','tech_team','support_team'];
    if (!validRoles.includes(role)) { auth.signOut(); showLogin(); return; }
    adminRole = role;
    showDashboard(user, adminRole);
  } catch(e) {
    console.error('Admin check failed:', e);
    auth.signOut(); showLogin();
  }
});

function showLogin() {
  hideLoading();
  document.getElementById('loginWrap').style.display = 'flex';
  document.getElementById('dashboard').style.display = 'none';
}

function showDashboard(user, role) {
  hideLoading();
  document.getElementById('loginWrap').style.display = 'none';
  document.getElementById('dashboard').style.display = 'block';
  document.getElementById('adminName').textContent    = user.displayName || user.email || 'Admin';
  document.getElementById('adminInitial').textContent = (user.displayName || user.email || 'A').charAt(0).toUpperCase();
  const roleTag = document.getElementById('roleTag');
  roleTag.textContent = role.replace(/_/g,' ').replace(/\b\w/g, c => c.toUpperCase());
  roleTag.className   = 'role-tag role-' + role;

  // ── support_team: very limited access ────────────────────────────────────
  if (role === 'support_team') {
    hide('nav-team'); hide('nav-broadcast'); hide('nav-notifs');
    hide('nav-settings'); hide('nav-section-team'); hide('nav-config');
    hide('nav-admob'); hide('nav-ratelimit');
  }

  // ── Non-super-admin: hide privileged sections ─────────────────────────────
  if (!isSuperAdmin()) {
    // Hide Add Team Member card
    const addTeamCard = document.getElementById('addTeamCard');
    if (addTeamCard) addTeamCard.style.display = 'none';

    // Hide Remote Config nav & page
    hide('nav-config');

    // Hide Rate Limiting nav & page
    hide('nav-ratelimit');

    // Hide Maintenance Mode section
    const maintSection = document.getElementById('maintenanceSection');
    if (maintSection) maintSection.style.display = 'none';

    // Hide Version Config section
    const versionSection = document.getElementById('versionSection');
    if (versionSection) versionSection.style.display = 'none';

    // Hide Clear Old Logs button
    const clearLogsBtn = document.getElementById('clearLogsBtn');
    if (clearLogsBtn) clearLogsBtn.style.display = 'none';

    // Hide AdMob flags nav (sensitive config)
    hide('nav-admob');
  }

  loadAll();
}

function hide(id) {
  const el = document.getElementById(id);
  if (el) el.style.display = 'none';
}

function loadAll() {
  loadStats(); loadUsers(); loadBannedUsers(); loadReports();
  loadTeam(); loadBcHistory(); loadLogs(); loadOnlineUsers();
  loadAnalytics(); loadSettings(); loadLanguages();
}

// ── Email + Password Login ───────────────────────────────────────────────────
async function adminLogin() {
  const email  = document.getElementById('adminPhone').value.trim();
  const pass   = document.getElementById('adminPass').value;
  const errEl  = document.getElementById('err1');
  errEl.textContent = '';
  if (!email || !pass) { errEl.textContent = 'Enter email and password'; return; }
  try {
    await auth.signInWithEmailAndPassword(email, pass);
  } catch(e) {
    const msg = e.code === 'auth/wrong-password'   ? 'Wrong password.' :
                e.code === 'auth/user-not-found'   ? 'No account found.' :
                e.code === 'auth/invalid-email'    ? 'Invalid email.' :
                e.code === 'auth/too-many-requests'? 'Too many attempts. Try later.' :
                (e.message || 'Login failed');
    errEl.textContent = msg;
  }
}

document.addEventListener('keydown', e => {
  if (e.key === 'Enter' && document.getElementById('loginWrap')?.style.display !== 'none') adminLogin();
});

function adminLogout() {
  if (confirm('Log out of admin dashboard?')) auth.signOut();
}

function verify2FA() {}
function backToStep1() {}

// ════════════════════════════════════════════════════════════════════════════
// NAVIGATION
// ════════════════════════════════════════════════════════════════════════════
function showPage(name) {
  document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
  document.getElementById('page-' + name)?.classList.add('active');
  document.getElementById('nav-'  + name)?.classList.add('active');
  const titles = {
    overview:'Overview', users:'All Users', banned:'Banned Users',
    online:'Online Now', reports:'Reports', trust:'Trust Scores',
    screenshots:'Screenshot Alerts', broadcast:'Broadcast',
    notifs:'Push Notifications', officialstatus:'Official Status',
    scheduled:'Scheduled Items', contactrequests:'Contact Requests',
    terms:'Terms & Conditions', privacy:'Privacy Policy',
    team:'Admin Team', analytics:'Analytics', logs:'Audit Logs',
    settings:'App Settings', config:'Remote Config',
    admob:'AdMob Flags', languages:'Languages', ratelimit:'Rate Limiting'
  };
  document.getElementById('pageTitle').textContent = titles[name] || name;
  if (name === 'trust')           loadLowTrust();
  if (name === 'screenshots')     loadScreenshots();
  if (name === 'contactrequests') loadContactRequests();
  if (name === 'officialstatus')  loadOfficialStatuses();
  if (name === 'scheduled')       loadAllScheduled();
  if (name === 'notifs')          loadScheduledNotifs();
  if (name === 'terms')           loadAdminConfig('terms_conditions','termsContent');
  if (name === 'privacy')         loadAdminConfig('privacy_policy','privacyContent');
}

// ════════════════════════════════════════════════════════════════════════════
// STATS
// ════════════════════════════════════════════════════════════════════════════
function loadStats() {
  db.collection('users').onSnapshot(s => { document.getElementById('statUsers').textContent = s.size; });
  db.collection('admin_users').onSnapshot(s => { document.getElementById('statTeam').textContent = s.size; });
  db.collection('users').where('banned','==',true).onSnapshot(s => { document.getElementById('statBanned').textContent = s.size; });
  db.collection('chats').where('type','==','channel').where('isPublic','==',true).onSnapshot(s => { document.getElementById('statChannels').textContent = s.size; });
  db.collection('reports').where('status','==','open').onSnapshot(s => {
    const n = s.size;
    document.getElementById('statReports').textContent   = n;
    document.getElementById('badge-reports').textContent = n;
  });
  const fiveAgo = firebase.firestore.Timestamp.fromDate(new Date(Date.now() - 5*60*1000));
  db.collection('users').where('lastSeen','>',fiveAgo).onSnapshot(s => { document.getElementById('statOnline').textContent = s.size; });
  const since24h = new Date(Date.now() - 86400000);
  db.collection('screenshot_alerts').where('timestamp','>=',since24h).onSnapshot(s => { document.getElementById('statScreenshots').textContent = s.size; });
  db.collection('contact_requests').where('replied','==',false).onSnapshot(s => {
    const n = s.size;
    document.getElementById('statContactOpen').textContent = n;
    document.getElementById('badge-contacts').textContent  = n;
  });
  Promise.all([
    db.collection('official_status').where('isScheduled','==',true).where('published','==',false).get(),
    db.collection('scheduled_notifications').where('sent','==',false).get()
  ]).then(([a,b]) => { document.getElementById('statScheduled').textContent = (a.size + b.size); }).catch(() => {});
  rtdb.ref('onlineUsers').on('value', snap => {
    if (snap.exists()) document.getElementById('statOnline').textContent = Object.keys(snap.val()).length;
  });
}

// ════════════════════════════════════════════════════════════════════════════
// USERS
// ════════════════════════════════════════════════════════════════════════════
function loadUsers() {
  db.collection('users').orderBy('createdAt','desc').limit(200).onSnapshot(snap => {
    allUsersCache = snap.docs;
    renderUsers(snap.docs);
    document.getElementById('recentSignups').innerHTML =
      snap.docs.slice(0,5).map(doc => {
        const d = doc.data();
        return `<tr><td>${esc(d.displayName||'?')}</td><td>${esc(d.phone||'—')}</td><td>${fmtDate(d.createdAt)}</td></tr>`;
      }).join('') || '<tr><td colspan="3" class="empty">None</td></tr>';
  }, err => console.error('Users:', err));
}

function renderUsers(docs) {
  document.getElementById('usersBody').innerHTML = docs.map(doc => {
    const d = doc.data();
    const banned = !!d.banned, sus = !!d.suspended, flag = !!d.redFlagged;
    let statusB = banned ? '<span class="badge badge-banned">Banned</span>'
                : sus    ? '<span class="badge badge-warn">Suspended</span>'
                : flag   ? '<span class="badge badge-purple">🚩 Flagged</span>'
                :          '<span class="badge badge-active">Active</span>';
    return `<tr>
      <td>${esc(d.displayName||'Unknown')}</td>
      <td>${esc(d.phone||'—')}</td>
      <td style="font-size:11px;color:var(--muted)">${doc.id.slice(0,12)}…</td>
      <td>${fmtDate(d.createdAt)}</td>
      <td>${statusB}</td>
      <td style="display:flex;gap:4px;flex-wrap:wrap">
        ${!banned
          ? `<button class="btn-sm btn-danger" onclick="banUser('${doc.id}')">Ban</button>`
          : `<button class="btn-sm btn-success" onclick="unbanUser('${doc.id}')">Unban</button>`}
        <button class="btn-sm btn-warn" onclick="openSuspendModal('${doc.id}')">Suspend</button>
        <button class="btn-sm btn-purple" onclick="redFlagUser('${doc.id}')">🚩 Flag</button>
        <button class="btn-sm btn-info" onclick="viewUser('${doc.id}')">View</button>
      </td>
    </tr>`;
  }).join('') || '<tr><td colspan="6" class="empty">No users found</td></tr>';
}

function filterUsers() {
  const q = document.getElementById('userSearch').value.toLowerCase();
  renderUsers(allUsersCache.filter(doc => {
    const d = doc.data();
    return (d.displayName||'').toLowerCase().includes(q) || (d.phone||'').includes(q);
  }));
}

async function banUser(uid) {
  const reason = prompt('Reason for ban (optional):');
  if (!confirm('Ban this user?')) return;
  await db.collection('users').doc(uid).update({
    banned: true, bannedAt: firebase.firestore.FieldValue.serverTimestamp(),
    banReason: reason || '', restricted: true
  });
  functions.httpsCallable('revokeUserTokens')({ uid }).catch(() => {});
  logAction('ban_user', uid); toast('🚫 User banned');
}

async function unbanUser(uid) {
  if (!confirm('Unban this user?')) return;
  await db.collection('users').doc(uid).update({ banned: false, bannedAt: null, banReason: '', restricted: false });
  logAction('unban_user', uid); toast('✅ User unbanned');
}

async function redFlagUser(uid) {
  await db.collection('users').doc(uid).update({
    redFlagged: true, redFlaggedAt: firebase.firestore.FieldValue.serverTimestamp()
  });
  logAction('red_flag_user', uid); toast('🚩 User red-flagged');
}

function viewUser(uid) {
  db.collection('users').doc(uid).get().then(doc => {
    if (!doc.exists) return;
    const d = doc.data();
    document.getElementById('userModalContent').innerHTML = `
      <div class="detail-row"><span class="detail-label">Name</span><span>${esc(d.displayName||'—')}</span></div>
      <div class="detail-row"><span class="detail-label">Phone</span><span>${esc(d.phone||'—')}</span></div>
      <div class="detail-row"><span class="detail-label">UID</span><span style="font-size:11px">${uid}</span></div>
      <div class="detail-row"><span class="detail-label">About</span><span>${esc(d.about||'—')}</span></div>
      <div class="detail-row"><span class="detail-label">Trust Score</span><span>${d.trustScore??'—'}</span></div>
      <div class="detail-row"><span class="detail-label">Report Count</span><span>${d.reportCount||0}</span></div>
      <div class="detail-row"><span class="detail-label">Joined</span><span>${fmtDate(d.createdAt)}</span></div>
      <div class="detail-row"><span class="detail-label">Last Seen</span><span>${fmtDate(d.lastSeen)}</span></div>
      <div class="detail-row"><span class="detail-label">Status</span><span>${d.banned?'<span class="badge badge-banned">Banned</span>':d.suspended?'<span class="badge badge-warn">Suspended</span>':'<span class="badge badge-active">Active</span>'}</span></div>
      ${d.banReason?`<div class="detail-row"><span class="detail-label">Ban Reason</span><span>${esc(d.banReason)}</span></div>`:''}
      <div style="margin-top:14px;display:flex;gap:8px;flex-wrap:wrap">
        ${!d.banned
          ?`<button class="btn-sm btn-danger" onclick="banUser('${uid}');document.getElementById('userModal').classList.remove('open')">Ban</button>`
          :`<button class="btn-sm btn-success" onclick="unbanUser('${uid}');document.getElementById('userModal').classList.remove('open')">Unban</button>`}
        <button class="btn-sm btn-warn" onclick="openSuspendModal('${uid}');document.getElementById('userModal').classList.remove('open')">Suspend</button>
        <button class="btn-sm btn-purple" onclick="redFlagUser('${uid}')">🚩 Red Flag</button>
      </div>`;
    document.getElementById('userModal').classList.add('open');
  });
}
document.getElementById('userModal').addEventListener('click', e => {
  if (e.target === document.getElementById('userModal')) document.getElementById('userModal').classList.remove('open');
});

// ════════════════════════════════════════════════════════════════════════════
// BANNED USERS
// ════════════════════════════════════════════════════════════════════════════
function loadBannedUsers() {
  db.collection('users').where('banned','==',true).onSnapshot(snap => {
    document.getElementById('bannedBody').innerHTML = snap.docs.map(doc => {
      const d = doc.data();
      return `<tr>
        <td>${esc(d.displayName||'Unknown')}</td><td>${esc(d.phone||'—')}</td>
        <td>${fmtDate(d.bannedAt)}</td><td>${esc(d.banReason||'—')}</td>
        <td><button class="btn-sm btn-success" onclick="unbanUser('${doc.id}')">Unban</button></td>
      </tr>`;
    }).join('') || '<tr><td colspan="5" class="empty">No banned users</td></tr>';
  });
}

// ════════════════════════════════════════════════════════════════════════════
// ONLINE NOW
// ════════════════════════════════════════════════════════════════════════════
function loadOnlineUsers() {
  const fiveAgo = firebase.firestore.Timestamp.fromDate(new Date(Date.now() - 5*60*1000));
  db.collection('users').where('lastSeen','>',fiveAgo).onSnapshot(snap => {
    document.getElementById('onlineBody').innerHTML = snap.docs.map(doc => {
      const d = doc.data();
      return `<tr><td>${esc(d.displayName||'?')}</td><td>${esc(d.phone||'—')}</td><td>${fmtDate(d.lastSeen)}</td></tr>`;
    }).join('') || '<tr><td colspan="3" class="empty">No users online right now</td></tr>';
  });
}

// ════════════════════════════════════════════════════════════════════════════
// REPORTS
// ════════════════════════════════════════════════════════════════════════════
let reportsFilter = 'all';
function filterReports(val) { reportsFilter = val; renderReports(allReportCache); }

function loadReports() {
  db.collection('reports').orderBy('createdAt','desc').limit(200).onSnapshot(snap => {
    allReportCache = snap.docs;
    renderReports(snap.docs);
    const openDocs = snap.docs.filter(d => d.data().status === 'open');
    document.getElementById('latestReports').innerHTML =
      openDocs.slice(0,5).map(doc => {
        const d = doc.data();
        return `<tr><td>${esc(d.reporterName||'?')}</td><td>${esc(d.reason||'—')}</td><td>${fmtDate(d.createdAt)}</td></tr>`;
      }).join('') || '<tr><td colspan="3" class="empty">None</td></tr>';
  });
}

function renderReports(docs) {
  const filtered = reportsFilter === 'all' ? docs : docs.filter(d => d.data().status === reportsFilter);
  document.getElementById('reportsBody').innerHTML = filtered.map(doc => {
    const d = doc.data(), isOpen = d.status === 'open';
    return `<tr>
      <td>${esc(d.reporterName||'—')}</td><td>${esc(d.reason||'—')}</td>
      <td>${esc(d.reportedName||'—')}</td><td>${fmtDate(d.createdAt)}</td>
      <td><span class="badge ${isOpen?'badge-open':'badge-resolved'}">${d.status||'—'}</span></td>
      <td style="display:flex;gap:4px;flex-wrap:wrap">
        ${isOpen?`<button class="btn-sm btn-success" onclick="resolveReport('${doc.id}')">✅ Resolve</button>`:''}
        <button class="btn-sm btn-purple" onclick="redFlagUser('${d.reportedUid||''}')">🚩 Flag</button>
        <button class="btn-sm btn-warn" onclick="openSuspendModal('${d.reportedUid||''}')">⏸ Suspend</button>
        <button class="btn-sm btn-danger" onclick="banUser('${d.reportedUid||''}')">🚫 Ban</button>
        <button class="btn-sm btn-info" onclick="viewUser('${d.reportedUid||''}')" ${d.reportedUid?'':'disabled'}>View</button>
      </td>
    </tr>`;
  }).join('') || '<tr><td colspan="6" class="empty">No reports</td></tr>';
}

async function resolveReport(id) {
  await db.collection('reports').doc(id).update({
    status: 'resolved', resolvedAt: firebase.firestore.FieldValue.serverTimestamp(), resolvedBy: auth.currentUser?.uid
  });
  logAction('resolve_report', id); toast('✅ Report resolved');
}

// ════════════════════════════════════════════════════════════════════════════
// SUSPEND MODAL
// ════════════════════════════════════════════════════════════════════════════
function openSuspendModal(uid) {
  if (!uid) return;
  suspendTargetUid = uid;
  document.getElementById('suspendModalUid').textContent = 'User: ' + uid;
  document.getElementById('suspendReason').value = '';
  document.getElementById('suspendModal').classList.add('open');
}
function closeSuspendModal() {
  document.getElementById('suspendModal').classList.remove('open');
  suspendTargetUid = null;
}
async function confirmSuspend() {
  if (!suspendTargetUid) return;
  const duration = parseInt(document.getElementById('suspendDuration').value);
  const reason   = document.getElementById('suspendReason').value || 'Community guideline violation';
  const until    = Date.now() + duration;
  await db.collection('users').doc(suspendTargetUid).update({
    restricted: true, suspended: true, restrictedUntil: until,
    suspendReason: reason, suspendedAt: firebase.firestore.FieldValue.serverTimestamp()
  });
  toast(`⏸ User suspended until ${new Date(until).toLocaleString()}`);
  logAction('suspend_user', `${suspendTargetUid} (${reason})`);
  closeSuspendModal();
}
document.getElementById('suspendModal').addEventListener('click', e => {
  if (e.target === document.getElementById('suspendModal')) closeSuspendModal();
});

// ════════════════════════════════════════════════════════════════════════════
// TRUST SCORES
// ════════════════════════════════════════════════════════════════════════════
function loadLowTrust() {
  db.collection('users').where('trustScore','<',30).limit(50).get().then(snap => {
    const tbody = document.getElementById('lowTrustBody');
    if (snap.empty) { tbody.innerHTML='<tr><td colspan="5" class="empty">All healthy 🎉</td></tr>'; return; }
    tbody.innerHTML = snap.docs.map(doc => {
      const u = doc.data();
      return `<tr>
        <td style="font-family:monospace;font-size:11px">${doc.id}</td>
        <td><span class="badge ${u.trustScore<20?'badge-danger':'badge-warn'}">${u.trustScore}</span></td>
        <td>${u.reportCount||0}</td>
        <td>${u.restricted?'<span class="badge badge-danger">Yes</span>':'<span class="badge badge-success">No</span>'}</td>
        <td style="display:flex;gap:4px">
          <button class="btn-sm btn-purple" onclick="redFlagUser('${doc.id}')">🚩 Flag</button>
          <button class="btn-sm btn-warn" onclick="openSuspendModal('${doc.id}')">⏸ Suspend</button>
          <button class="btn-sm btn-danger" onclick="banUser('${doc.id}')">🚫 Ban</button>
        </td>
      </tr>`;
    }).join('');
  });
}

async function lookupTrust() {
  const uid = document.getElementById('trustSearchUid').value.trim();
  if (!uid) return;
  const snap = await db.collection('users').doc(uid).get();
  const el   = document.getElementById('trustResult');
  if (!snap.exists) { el.innerHTML='<p style="color:var(--red)">User not found.</p>'; return; }
  const u = snap.data();
  el.innerHTML = `<div class="card" style="max-width:480px"><div class="card-body">
    <div class="detail-row"><span class="detail-label">Trust Score</span><strong style="color:${u.trustScore>=50?'var(--green)':'var(--red)'}">${u.trustScore??'—'}</strong></div>
    <div class="detail-row"><span class="detail-label">Report Count</span><strong>${u.reportCount??0}</strong></div>
    <div class="detail-row"><span class="detail-label">Restricted</span>${u.restricted?'<span class="badge badge-danger">Yes</span>':'<span class="badge badge-success">No</span>'}</div>
    <div class="detail-row"><span class="detail-label">Banned</span>${u.banned?'<span class="badge badge-danger">Yes</span>':'<span class="badge badge-success">No</span>'}</div>
    <div style="display:flex;gap:8px;margin-top:12px">
      <button class="btn-sm btn-purple" onclick="redFlagUser('${uid}')">🚩 Red Flag</button>
      <button class="btn-sm btn-warn" onclick="openSuspendModal('${uid}')">⏸ Suspend</button>
      <button class="btn-sm btn-danger" onclick="banUser('${uid}')">🚫 Ban</button>
    </div>
  </div></div>`;
}

// ════════════════════════════════════════════════════════════════════════════
// SCREENSHOT ALERTS
// ════════════════════════════════════════════════════════════════════════════
function loadScreenshots() {
  db.collection('screenshot_alerts').orderBy('timestamp','desc').limit(50).onSnapshot(snap => {
    const tbody = document.getElementById('screenshotBody');
    if (snap.empty) { tbody.innerHTML='<tr><td colspan="5" class="empty">No alerts</td></tr>'; return; }
    tbody.innerHTML = snap.docs.map(doc => {
      const a = doc.data();
      return `<tr>
        <td style="font-size:11px;font-family:monospace">${a.chatId||'—'}</td>
        <td>${a.takerName||'—'}</td>
        <td style="font-size:11px;font-family:monospace">${a.targetUid||'—'}</td>
        <td style="font-size:12px">${fmtDate(a.timestamp)}</td>
        <td style="display:flex;gap:4px">
          <button class="btn-sm btn-purple" onclick="redFlagUser('${a.takerUid||''}')">🚩 Flag</button>
          <button class="btn-sm btn-warn" onclick="openSuspendModal('${a.takerUid||''}')">⏸ Suspend</button>
        </td>
      </tr>`;
    }).join('');
  });
}

// ════════════════════════════════════════════════════════════════════════════
// BROADCAST
// ════════════════════════════════════════════════════════════════════════════
async function sendBroadcast() {
  const title  = document.getElementById('bcTitle').value.trim();
  const body   = document.getElementById('bcBody').value.trim();
  const target = document.getElementById('bcTarget').value;
  const status = document.getElementById('bcStatus');
  if (!title || !body) { status.className='status-msg err-inline'; status.textContent='Enter title and body'; return; }
  status.innerHTML = '<span class="spinner"></span> Sending…';
  try {
    await functions.httpsCallable('sendBroadcastMessage')({ title, body, target });
    status.className = 'status-msg ok'; status.textContent = '✅ Broadcast sent!';
    document.getElementById('bcTitle').value = document.getElementById('bcBody').value = '';
    logAction('broadcast', `${target}: ${title}`); loadBcHistory();
  } catch(e) { status.className = 'status-msg err-inline'; status.textContent = e.message || 'Failed'; }
}

function loadBcHistory() {
  db.collection('broadcasts').orderBy('sentAt','desc').limit(30).onSnapshot(snap => {
    document.getElementById('bcHistoryBody').innerHTML = snap.docs.map(doc => {
      const d = doc.data();
      return `<tr><td>${esc(d.title||'—')}</td><td>${d.target||'all'}</td><td>${esc(d.sentByName||'Admin')}</td><td>${fmtDate(d.sentAt)}</td><td>${d.reach||'—'}</td></tr>`;
    }).join('') || '<tr><td colspan="5" class="empty">No broadcasts yet</td></tr>';
  });
}

// ════════════════════════════════════════════════════════════════════════════
// PUSH NOTIFICATIONS
// ════════════════════════════════════════════════════════════════════════════
async function sendPushNotif(schedule) {
  const title   = document.getElementById('pnTitle').value.trim();
  const body    = document.getElementById('pnBody').value.trim();
  const target  = document.getElementById('pnTarget').value;
  const uid     = document.getElementById('pnUid').value.trim();
  const schedAt = document.getElementById('pnScheduleAt').value;
  const status  = document.getElementById('pnStatus');
  if (!title || !body) { status.className='status-msg err-inline'; status.textContent='Enter title and body'; return; }
  if (target === 'uid' && !uid) { status.className='status-msg err-inline'; status.textContent='Enter user UID'; return; }
  if (schedule && schedAt) {
    const schedDate = new Date(schedAt);
    await db.collection('scheduled_notifications').add({
      title, message: body, topic: target,
      scheduleAt: firebase.firestore.Timestamp.fromDate(schedDate),
      sent: false, createdAt: firebase.firestore.FieldValue.serverTimestamp(), createdBy: auth.currentUser?.uid || 'admin'
    });
    status.className = 'status-msg ok'; status.textContent = `⏰ Scheduled for ${schedDate.toLocaleString()}`;
    document.getElementById('pnScheduleAt').value = ''; loadScheduledNotifs(); return;
  }
  status.innerHTML = '<span class="spinner"></span> Sending…';
  try {
    await functions.httpsCallable('sendPushNotification')({ title, body, target, uid: target === 'uid' ? uid : null });
    status.className = 'status-msg ok'; status.textContent = '✅ Notification sent!';
    logAction('push_notification', `${target}: ${title}`);
  } catch(e) { status.className = 'status-msg err-inline'; status.textContent = e.message || 'Failed'; }
}

function loadScheduledNotifs() {
  db.collection('scheduled_notifications').where('sent','==',false)
    .orderBy('scheduleAt','asc').limit(20).get().then(snap => {
    const tbody = document.getElementById('scheduledNotifBody');
    if (!tbody) return;
    if (snap.empty) { tbody.innerHTML='<tr><td colspan="5" class="empty">No scheduled notifications</td></tr>'; return; }
    tbody.innerHTML = snap.docs.map(doc => {
      const n = doc.data();
      return `<tr><td>${esc(n.title||'(no title)')}</td><td>${n.topic||'all_users'}</td><td><span class="sched-badge">${fmtDate(n.scheduleAt)}</span></td><td><span class="badge badge-warn">Pending</span></td><td><button class="btn-sm btn-danger" onclick="cancelScheduledNotif('${doc.id}')">🗑 Cancel</button></td></tr>`;
    }).join('');
  }).catch(() => {});
}

async function cancelScheduledNotif(id) {
  if (!confirm('Cancel this scheduled notification?')) return;
  await db.collection('scheduled_notifications').doc(id).delete();
  toast('🗑 Cancelled'); loadScheduledNotifs();
}

// ════════════════════════════════════════════════════════════════════════════
// OFFICIAL STATUS
// ════════════════════════════════════════════════════════════════════════════
async function postOfficialStatus(schedule) {
  const author  = document.getElementById('osAuthor').value.trim() || 'Samvixo Team';
  const text    = document.getElementById('osText').value.trim();
  const media   = document.getElementById('osMedia').value.trim();
  const schedAt = document.getElementById('osScheduleAt').value;
  const status  = document.getElementById('osStatus');
  if (!text) { status.className='status-msg err-inline'; status.textContent='Enter status text'; return; }
  if (schedule && schedAt) {
    const schedDate = new Date(schedAt);
    await db.collection('official_status').add({
      authorName: author, text, mediaUrl: media,
      isScheduled: true, published: false,
      scheduleAt: firebase.firestore.Timestamp.fromDate(schedDate),
      createdAt: firebase.firestore.FieldValue.serverTimestamp()
    });
    status.className = 'status-msg ok'; status.textContent = `⏰ Scheduled for ${schedDate.toLocaleString()}`;
  } else {
    await db.collection('official_status').add({
      authorName: author, text, mediaUrl: media,
      isScheduled: false, published: true,
      createdAt: firebase.firestore.FieldValue.serverTimestamp(),
      timestamp: Date.now(), expiryTimestamp: Date.now() + 86400000, type: 'OFFICIAL_UPDATE'
    });
    status.className = 'status-msg ok'; status.textContent = '📣 Official status posted!';
    toast('📣 Official Samvixo Update posted!');
  }
  document.getElementById('osText').value = document.getElementById('osMedia').value = '';
  document.getElementById('osScheduleAt').value = '';
  logAction('post_official_status', text.slice(0,60)); loadOfficialStatuses();
}

function loadOfficialStatuses() {
  db.collection('official_status').where('published','==',true)
    .orderBy('createdAt','desc').limit(20).get().then(snap => {
    const tbody = document.getElementById('osListBody');
    if (!tbody) return;
    if (snap.empty) { tbody.innerHTML='<tr><td colspan="4" class="empty">No official statuses yet</td></tr>'; return; }
    tbody.innerHTML = snap.docs.map(doc => {
      const s = doc.data();
      return `<tr><td>${s.authorName||'Samvixo'}</td><td style="max-width:260px">${esc(s.text||'')}</td><td style="font-size:12px">${fmtDate(s.createdAt)}</td><td><button class="btn-sm btn-danger" onclick="deleteOfficialStatus('${doc.id}')">🗑 Delete</button></td></tr>`;
    }).join('');
  });
}

async function deleteOfficialStatus(id) {
  if (!confirm('Delete?')) return;
  await db.collection('official_status').doc(id).delete();
  toast('🗑 Deleted'); logAction('delete_official_status', id); loadOfficialStatuses();
}

// ════════════════════════════════════════════════════════════════════════════
// SCHEDULED ITEMS
// ════════════════════════════════════════════════════════════════════════════
async function loadAllScheduled() {
  try {
    const ss = await db.collection('official_status')
      .where('isScheduled','==',true).where('published','==',false)
      .orderBy('scheduleAt','asc').limit(20).get();
    const stbody = document.getElementById('schedStatusBody');
    if (stbody) stbody.innerHTML = ss.empty
      ? '<tr><td colspan="4" class="empty">No scheduled statuses</td></tr>'
      : ss.docs.map(doc => { const s = doc.data(); return `<tr><td>${s.authorName||'Samvixo'}</td><td>${esc(s.text||'')}</td><td><span class="sched-badge">${fmtDate(s.scheduleAt)}</span></td><td><button class="btn-sm btn-danger" onclick="cancelScheduledStatus('${doc.id}')">🗑 Cancel</button></td></tr>`; }).join('');
  } catch(e) {}
  try {
    const sn = await db.collection('scheduled_notifications').where('sent','==',false).orderBy('scheduleAt','asc').limit(20).get();
    const ntbody = document.getElementById('schedNotifBody');
    if (ntbody) ntbody.innerHTML = sn.empty
      ? '<tr><td colspan="5" class="empty">No scheduled notifications</td></tr>'
      : sn.docs.map(doc => { const n = doc.data(); return `<tr><td>${esc(n.title||'(no title)')}</td><td>${esc((n.message||'').substring(0,60))}</td><td>${n.topic||'all_users'}</td><td><span class="sched-badge">${fmtDate(n.scheduleAt)}</span></td><td><button class="btn-sm btn-danger" onclick="cancelScheduledNotif('${doc.id}')">🗑 Cancel</button></td></tr>`; }).join('');
  } catch(e) {}
}

async function cancelScheduledStatus(id) {
  if (!confirm('Cancel?')) return;
  await db.collection('official_status').doc(id).delete();
  toast('🗑 Cancelled'); loadAllScheduled();
}

// ════════════════════════════════════════════════════════════════════════════
// CONTACT REQUESTS
// ════════════════════════════════════════════════════════════════════════════
async function loadContactRequests() {
  const filter = document.getElementById('crFilter')?.value || 'open';
  let q;
  if (filter === 'open')    q = db.collection('contact_requests').where('replied','==',false).orderBy('createdAt','desc').limit(50);
  else if (filter === 'replied') q = db.collection('contact_requests').where('replied','==',true).orderBy('createdAt','desc').limit(50);
  else                      q = db.collection('contact_requests').orderBy('createdAt','desc').limit(50);
  const snap = await q.get();
  const tbody = document.getElementById('crBody');
  if (snap.empty) { tbody.innerHTML='<tr><td colspan="7" class="empty">No requests found</td></tr>'; return; }
  tbody.innerHTML = snap.docs.map(doc => {
    const r = doc.data(), ts = fmtDate(r.createdAt), replied = r.replied === true;
    const msgShort = (r.message||'').length > 60 ? r.message.substring(0,60)+'…' : r.message;
    const escSub = (r.subject||'').replace(/"/g,'&quot;'), escEmail = (r.email||'').replace(/"/g,'&quot;'), escMsg = (r.message||'').replace(/"/g,'&quot;');
    return `<tr>
      <td style="font-size:12px;white-space:nowrap">${ts}</td>
      <td style="font-size:11px;font-family:monospace;max-width:90px;overflow:hidden">${r.uid||'—'}</td>
      <td>${r.email||'—'}</td><td><strong>${esc(r.subject||'—')}</strong></td>
      <td style="max-width:200px;font-size:12px">${esc(msgShort||'')}</td>
      <td>${replied?'<span class="badge badge-success">Replied</span>':'<span class="badge badge-warn">Open</span>'}</td>
      <td>${replied
        ?`<button class="btn-sm btn-info" onclick="viewReply('${doc.id}')">👁 View</button>`
        :`<button class="btn-sm btn-accent" onclick="openReplyModal('${doc.id}','${r.uid||''}','${escSub}','${escEmail}','${escMsg}')">💬 Reply</button>`
      }</td>
    </tr>`;
  }).join('');
}

function openReplyModal(docId, uid, subject, email, originalMsg) {
  currentReplyDocId = docId; currentReplyUid = uid;
  currentReplySubject = subject; currentReplyMsg = originalMsg;
  document.getElementById('replyModalMeta').textContent = `To: ${uid} (${email})`;
  document.getElementById('replyOriginalMsg').textContent = `📩 "${subject}" — ${originalMsg}`;
  document.getElementById('replyText').value = '';
  document.getElementById('aiSuggestionBox').style.display = 'none';
  document.getElementById('aiSuggestionActions').style.display = 'none';
  aiSuggestedText = '';
  document.getElementById('replyModal').classList.add('open');
}
function closeReplyModal() {
  document.getElementById('replyModal').classList.remove('open');
  currentReplyDocId = currentReplyUid = null;
}
document.getElementById('replyModal').addEventListener('click', e => {
  if (e.target === document.getElementById('replyModal')) closeReplyModal();
});

async function aiSuggestReply() {
  const btn = document.getElementById('aiSuggestBtn'), box = document.getElementById('aiSuggestionBox'), acts = document.getElementById('aiSuggestionActions');
  btn.textContent = '🤖 Thinking…'; btn.disabled = true;
  try {
    const rcSnap = await db.collection('admin_config').doc('remote_config').get();
    const ollamaUrl = rcSnap.exists ? rcSnap.data()['OLLAMA_ENDPOINT_URL'] : null;
    const model = rcSnap.exists ? (rcSnap.data()['DEVIL_AI_MODEL'] || 'mistral') : 'mistral';
    if (!ollamaUrl) {
      const result = await functions.httpsCallable('adminAiSuggestReply')({ subject: currentReplySubject, message: currentReplyMsg });
      aiSuggestedText = result.data?.reply || '';
    } else {
      const prompt = `You are a professional support agent for Samvixo. Reply to:\nSubject: ${currentReplySubject}\nMessage: ${currentReplyMsg}\n\nWrite a helpful, friendly, concise reply (3-4 sentences).`;
      const resp = await fetch(`${ollamaUrl}/api/generate`, { method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({model, prompt, stream:false}) });
      aiSuggestedText = (await resp.json()).response || '';
    }
    box.textContent = aiSuggestedText; box.style.display = 'block'; acts.style.display = 'flex';
  } catch(e) { box.textContent = '❌ AI unavailable: ' + e.message; box.style.display = 'block'; acts.style.display = 'none';
  } finally { btn.textContent = '🤖 AI Suggest Reply'; btn.disabled = false; }
}

function useAiSuggestion() {
  if (aiSuggestedText) {
    document.getElementById('replyText').value = aiSuggestedText;
    document.getElementById('aiSuggestionBox').style.display = 'none';
    document.getElementById('aiSuggestionActions').style.display = 'none';
  }
}

async function submitReply() {
  const replyText = document.getElementById('replyText').value.trim();
  if (!replyText) return toast('⚠️ Write a reply first');
  if (!currentReplyDocId || !currentReplyUid) return;
  try {
    await db.collection('contact_requests').doc(currentReplyDocId).update({
      replied: true, replyText, repliedAt: firebase.firestore.FieldValue.serverTimestamp()
    });
    await db.collection('inbox_messages').add({
      recipientUid: currentReplyUid, fromTeam: true, senderName: 'Samvixo Support',
      subject: 'Re: ' + currentReplySubject, message: replyText,
      createdAt: firebase.firestore.FieldValue.serverTimestamp(), read: false
    });
    functions.httpsCallable('notifyInboxMessage')({ uid: currentReplyUid, title: 'Samvixo Support replied', body: replyText.substring(0,80) }).catch(() => {});
    toast('📨 Reply sent!'); closeReplyModal(); loadContactRequests(); logAction('contact_reply', currentReplyDocId);
  } catch(e) { toast('❌ ' + e.message); }
}

async function viewReply(docId) {
  const snap = await db.collection('contact_requests').doc(docId).get();
  if (!snap.exists) return;
  const r = snap.data();
  alert(`Reply sent:\n\n${r.replyText||'(empty)'}\n\nAt: ${fmtDate(r.repliedAt)}`);
}

// ════════════════════════════════════════════════════════════════════════════
// TERMS & PRIVACY
// ════════════════════════════════════════════════════════════════════════════
async function loadAdminConfig(docId, textareaId) {
  try {
    const snap = await db.collection('admin_config').doc(docId).get();
    document.getElementById(textareaId).value = snap.exists ? (snap.data().content || '') : '';
  } catch(e) { toast('❌ ' + e.message); }
}

async function saveAdminConfig(docId, textareaId) {
  const content = document.getElementById(textareaId).value;
  const statusId = docId === 'terms_conditions' ? 'termsSaveStatus' : 'privacySaveStatus';
  try {
    await db.collection('admin_config').doc(docId).set({ content, updatedAt: firebase.firestore.FieldValue.serverTimestamp(), updatedBy: auth.currentUser?.uid || 'admin' }, { merge: true });
    const el = document.getElementById(statusId);
    el.textContent = '✅ Saved!'; setTimeout(() => el.textContent = '', 4000);
    toast('💾 Saved'); logAction('save_config', docId);
  } catch(e) { toast('❌ ' + e.message); }
}

// ════════════════════════════════════════════════════════════════════════════
// TEAM
// ════════════════════════════════════════════════════════════════════════════
function loadTeam() {
  db.collection('admin_users').onSnapshot(snap => {
    document.getElementById('teamBody').innerHTML = snap.docs.map(doc => {
      const d = doc.data();
      const roleBadges = {
        super_admin:'<span class="badge badge-super">Super Admin</span>',
        superadmin:'<span class="badge badge-super">Super Admin</span>',
        second_admin:'<span class="badge badge-second">2nd Admin</span>',
        tech_team:'<span class="badge badge-tech">Tech Team</span>',
        support_team:'<span class="badge badge-support">Support</span>'
      };
      return `<tr>
        <td>${esc(d.name||d.username||'—')}</td><td>${esc(d.phone||d.email||'—')}</td>
        <td>${roleBadges[d.role]||`<span class="badge">${d.role}</span>`}</td>
        <td>${esc(d.addedByName||'—')}</td><td>${fmtDate(d.addedAt)}</td>
        <td>${isSuperAdmin()
          ?`<button class="btn-sm btn-danger" onclick="removeTeamMember('${doc.id}')">Remove</button>`
          :'—'}</td>
      </tr>`;
    }).join('') || '<tr><td colspan="6" class="empty">No team members</td></tr>';
  });
}

async function addTeamMember() {
  if (!isSuperAdmin()) { toast('🚫 Only Super Admin can add team members'); return; }
  const name  = document.getElementById('tmName').value.trim();
  const phone = document.getElementById('tmPhone').value.trim();
  const pass  = document.getElementById('tmPass').value.trim();
  const role  = document.getElementById('tmRole').value;
  const errEl = document.getElementById('teamErr');
  const okEl  = document.getElementById('teamOk');
  errEl.textContent = ''; okEl.textContent = '';
  if (!name || !phone || !pass) { errEl.textContent = 'Name, phone and password are required'; return; }
  if (pass.length < 8) { errEl.textContent = 'Password must be at least 8 characters'; return; }
  try {
    await functions.httpsCallable('addTeamMember')({ name, phone, pass, role });
    okEl.textContent = '✅ Team member added!';
    document.getElementById('tmName').value = document.getElementById('tmPhone').value = document.getElementById('tmPass').value = '';
    logAction('add_team_member', `${name} (${role})`);
  } catch(e) { errEl.textContent = e.message || 'Failed to add'; }
}

async function removeTeamMember(docId) {
  if (!isSuperAdmin()) { toast('🚫 Only Super Admin can remove team members'); return; }
  if (!confirm('Remove this admin team member?')) return;
  await db.collection('admin_users').doc(docId).delete();
  logAction('remove_team_member', docId); toast('🗑 Team member removed');
}

// ════════════════════════════════════════════════════════════════════════════
// ANALYTICS
// ════════════════════════════════════════════════════════════════════════════
function loadAnalytics() {
  const days = [];
  for (let i = 6; i >= 0; i--) { const d = new Date(); d.setDate(d.getDate() - i); days.push(d); }
  db.collection('users').where('createdAt','>=',firebase.firestore.Timestamp.fromDate(days[0])).get().then(snap => {
    const counts = new Array(7).fill(0);
    snap.docs.forEach(doc => {
      const d = doc.data().createdAt?.toDate?.();
      if (!d) return;
      const idx = days.findIndex(day => day.toDateString() === d.toDateString());
      if (idx >= 0) counts[idx]++;
    });
    const max = Math.max(...counts, 1);
    document.getElementById('chartBars').innerHTML = counts.map((c,i) =>
      `<div class="bar" style="height:${Math.round((c/max)*90)}px" title="${days[i].getDate()}/${days[i].getMonth()+1}: ${c} users"></div>`).join('');
    document.getElementById('chartLabels').innerHTML = days.map(d =>
      `<div class="chart-label">${d.getDate()}/${d.getMonth()+1}</div>`).join('');
  });
  db.collection('chats').get().then(s => { document.getElementById('aChatsTotal').textContent = s.size; });
  db.collection('chats').where('type','==','channel').get().then(s => { document.getElementById('aChannelsTotal').textContent = s.size; });
  db.collection('chats').where('type','==','group').get().then(s => { document.getElementById('aGroupsTotal').textContent = s.size; });
  db.collection('broadcasts').get().then(s => { document.getElementById('aBroadcastTotal').textContent = s.size; });
  db.collection('web_sessions').where('status','==','approved').get().then(s => { document.getElementById('aWebSessions').textContent = s.size; });
  const today = new Date(); today.setHours(0,0,0,0);
  db.collection('statuses').where('timestamp','>=',firebase.firestore.Timestamp.fromDate(today)).get()
    .then(s => { document.getElementById('aMessagesTotal').textContent = s.size; });
}

// ════════════════════════════════════════════════════════════════════════════
// AUDIT LOGS
// ════════════════════════════════════════════════════════════════════════════
function loadLogs() {
  db.collection('audit_logs').orderBy('timestamp','desc').limit(200).onSnapshot(snap => {
    document.getElementById('logsBody').innerHTML = snap.docs.map(doc => {
      const d = doc.data();
      return `<tr><td>${esc(d.adminName||'Admin')}</td><td>${esc(d.action)}</td><td style="max-width:180px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(d.target||'—')}</td><td>${fmtDate(d.timestamp)}</td></tr>`;
    }).join('') || '<tr><td colspan="4" class="empty">No logs</td></tr>';
  });
}

async function clearOldLogs() {
  if (!isSuperAdmin()) { toast('🚫 Only Super Admin can clear audit logs'); return; }
  if (!confirm('Delete audit logs older than 30 days?')) return;
  const cutoff = firebase.firestore.Timestamp.fromDate(new Date(Date.now() - 30*24*60*60*1000));
  const snap = await db.collection('audit_logs').where('timestamp','<',cutoff).get();
  const batch = db.batch();
  snap.docs.forEach(doc => batch.delete(doc.ref));
  await batch.commit();
  toast(`🗑 Deleted ${snap.size} old log entries`);
  logAction('clear_old_logs', `${snap.size} entries deleted`);
}

function logAction(action, target) {
  const user = auth.currentUser;
  db.collection('audit_logs').add({
    adminName: user?.displayName || user?.email || 'Admin',
    adminUid: user?.uid || '', action, target,
    timestamp: firebase.firestore.FieldValue.serverTimestamp()
  });
}

// ════════════════════════════════════════════════════════════════════════════
// SETTINGS
// ════════════════════════════════════════════════════════════════════════════
function loadSettings() {
  db.collection('app_config').doc('settings').onSnapshot(doc => {
    if (!doc.exists) return;
    const d = doc.data();
    maintOn = !!d.maintenanceMode;
    document.getElementById('maintTrack').classList.toggle('on', maintOn);
    document.getElementById('maintenanceStatus').textContent = maintOn ? '🔴 Maintenance ON' : '🟢 App is Live';
    if (d.announcement) document.getElementById('announcementText').value = d.announcement;
    if (d.minVersion)    document.getElementById('minVersion').value    = d.minVersion;
    if (d.latestVersion) document.getElementById('latestVersion').value = d.latestVersion;
    if (d.features) {
      const flagMap = { ai:'ffAITrack', webLogin:'ffWebTrack', mesh:'ffMeshTrack', status:'ffStatusTrack', emergencyMesh:'ffMeshCheckTrack', secretChat:'ffSecretTrack', driveBackup:'ffBackupTrack' };
      Object.entries(flagMap).forEach(([key, trackId]) => {
        const on = !!d.features[key]; featureState[key] = on;
        document.getElementById(trackId)?.classList.toggle('on', on);
      });
    }
  });
}

async function toggleMaintenance() {
  if (!isSuperAdmin()) { toast('🚫 Only Super Admin can toggle maintenance mode'); return; }
  maintOn = !maintOn;
  document.getElementById('maintTrack').classList.toggle('on', maintOn);
  document.getElementById('maintenanceStatus').textContent = maintOn ? '🔴 Maintenance ON' : '🟢 App is Live';
  await db.collection('app_config').doc('settings').set({ maintenanceMode: maintOn }, { merge: true });
  logAction('maintenance_mode', maintOn ? 'enabled' : 'disabled');
  toast(maintOn ? '🔴 Maintenance mode ON' : '🟢 App is live');
}

async function toggleFeatureFlag(key, trackId) {
  featureState[key] = !featureState[key];
  document.getElementById(trackId)?.classList.toggle('on', featureState[key]);
  await db.collection('app_config').doc('settings').set({ features: { [key]: featureState[key] } }, { merge: true });
  logAction('feature_flag', `${key} = ${featureState[key]}`); toast(`✅ ${key} = ${featureState[key]}`);
}

async function saveAnnouncement() {
  const text = document.getElementById('announcementText').value.trim();
  await db.collection('app_config').doc('settings').set({ announcement: text }, { merge: true });
  const ok = document.getElementById('annOk');
  ok.textContent = '✅ Saved!'; setTimeout(() => ok.textContent = '', 3000);
  logAction('save_announcement', text.slice(0,60)); toast('📣 Announcement saved');
}

async function saveVersionConfig() {
  if (!isSuperAdmin()) { toast('🚫 Only Super Admin can change version config'); return; }
  const min = document.getElementById('minVersion').value.trim();
  const latest = document.getElementById('latestVersion').value.trim();
  await db.collection('app_config').doc('settings').set({ minVersion: min, latestVersion: latest }, { merge: true });
  const ok = document.getElementById('versionOk');
  ok.textContent = '✅ Saved!'; setTimeout(() => ok.textContent = '', 3000);
  logAction('version_config', `min=${min} latest=${latest}`); toast('📱 Version config saved');
}

// ════════════════════════════════════════════════════════════════════════════
// REMOTE CONFIG
// ════════════════════════════════════════════════════════════════════════════
async function saveRemoteConfig() {
  if (!isSuperAdmin()) { toast('🚫 Only Super Admin can edit Remote Config'); return; }
  const config = {};
  RC_KEYS.forEach(k => { const v = document.getElementById('rc-' + k)?.value; if (v) config[k] = v; });
  if (!Object.keys(config).length) { toast('⚠️ No values entered'); return; }
  try {
    await functions.httpsCallable('updateRemoteConfig')(config);
    const ok = document.getElementById('rcStatus');
    ok.textContent = '✅ Config saved!'; setTimeout(() => ok.textContent = '', 3000);
    logAction('save_remote_config', Object.keys(config).join(', ')); toast('✅ Remote config saved');
  } catch(e) { toast('❌ ' + e.message); }
}

async function saveRemoteFlag(key, val) {
  if (!isSuperAdmin()) { toast('🚫 Only Super Admin can change remote flags'); return; }
  try {
    await functions.httpsCallable('updateRemoteConfig')({ [key]: val.toString() });
    toast(`✅ ${key} = ${val}`); logAction('remote_flag', `${key}=${val}`);
  } catch(e) { toast('❌ ' + e.message); }
}

async function saveRateLimits() {
  if (!isSuperAdmin()) { toast('🚫 Only Super Admin can change rate limits'); return; }
  const config = {};
  const msg = document.getElementById('rl-msg').value;
  const api = document.getElementById('rl-api').value;
  const ai  = document.getElementById('rl-ai').value;
  if (msg) config['RATE_MSG_PER_MIN']  = msg;
  if (api) config['RATE_API_PER_HOUR'] = api;
  if (ai)  config['RATE_AI_PER_DAY']   = ai;
  try {
    await functions.httpsCallable('updateRemoteConfig')(config);
    const ok = document.getElementById('rlStatus');
    ok.textContent = '✅ Limits saved!'; setTimeout(() => ok.textContent = '', 3000);
    toast('✅ Rate limits saved'); logAction('save_rate_limits', JSON.stringify(config));
  } catch(e) { toast('❌ ' + e.message); }
}

// ════════════════════════════════════════════════════════════════════════════
// LANGUAGES
// ════════════════════════════════════════════════════════════════════════════
function loadLanguages() {
  const grid = document.getElementById('langGrid');
  if (!grid) return;
  grid.innerHTML = LANGS.map(l => `
    <div style="background:#f8f5ff;border-radius:10px;padding:12px 16px;display:flex;align-items:center;justify-content:space-between;gap:8px;border:1px solid var(--border)">
      <span>${l.flag} ${l.name}</span>
      <label class="toggle-cb"><input type="checkbox" checked onchange="saveRemoteFlag('LANG_${l.code.replace('-','_')}_ENABLED',this.checked)" /><span class="slider"></span></label>
    </div>`).join('');
}

// ════════════════════════════════════════════════════════════════════════════
// HELPERS
// ════════════════════════════════════════════════════════════════════════════
function fmtDate(ts) {
  if (!ts) return '—';
  const d = ts.toDate ? ts.toDate() : new Date(ts);
  return d.toLocaleDateString(undefined, { day:'2-digit', month:'short', year:'numeric', hour:'2-digit', minute:'2-digit' });
}

function esc(str) {
  return String(str||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function toast(msg) {
  const el = document.getElementById('toast');
  el.textContent = msg; el.classList.add('show');
  setTimeout(() => el.classList.remove('show'), 3500);
}
