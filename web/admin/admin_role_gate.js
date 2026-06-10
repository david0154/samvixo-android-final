/**
 * admin_role_gate.js
 * Loaded AFTER the main admin index.html script.
 * Applies role-based access control:
 *   - super_admin  → full access, gold badge
 *   - second_admin → purple badge, Remote Config nav locked
 *   - others       → Remote Config nav locked
 *
 * Usage: add <script src="admin_role_gate.js"></script> at bottom of admin index.html
 */
(function () {
  // Wait for the dashboard to appear (auth + role loaded)
  // We hook into the existing window.showPage and role tag rendering.

  const SUPER_ADMIN_ROLES = ['super_admin', 'superadmin'];

  function applyRoleGate(role) {
    const configNav = document.getElementById('nav-config');
    if (!configNav) return;

    const isSuperAdmin = SUPER_ADMIN_ROLES.includes((role || '').toLowerCase());

    if (isSuperAdmin) {
      // Full access – make sure it's clickable and styled normally
      configNav.style.pointerEvents = '';
      configNav.style.opacity       = '';
      configNav.title               = '';
      configNav.onclick             = function () { window.showPage('config'); };
      // Remove lock icon if previously added
      const lock = configNav.querySelector('.rc-lock');
      if (lock) lock.remove();
    } else {
      // Lock Remote Config nav for non-super_admin
      configNav.style.pointerEvents = 'none';
      configNav.style.opacity       = '0.45';
      configNav.title               = 'Super Admin access only';
      configNav.onclick             = null;
      if (!configNav.querySelector('.rc-lock')) {
        const lockSpan = document.createElement('span');
        lockSpan.className   = 'rc-lock';
        lockSpan.textContent = '\uD83D\uDD12';
        lockSpan.style.marginLeft = 'auto';
        lockSpan.style.fontSize   = '13px';
        configNav.appendChild(lockSpan);
      }
      // If the user is currently on the config page, redirect to overview
      const activePage = document.querySelector('.page.active');
      if (activePage && activePage.id === 'page-config') {
        window.showPage('overview');
      }
    }
  }

  // Observe the roleTag element for changes (it gets set after Firestore loads)
  function watchRoleTag() {
    const roleTag = document.getElementById('roleTag');
    if (!roleTag) {
      setTimeout(watchRoleTag, 300);
      return;
    }

    // Read current value immediately
    const currentRole = (roleTag.textContent || '').trim();
    if (currentRole && currentRole !== 'Role') {
      applyRoleGate(currentRole);
    }

    // Watch for DOM mutations in case role loads async
    const obs = new MutationObserver(() => {
      const role = (roleTag.textContent || '').trim();
      if (role && role !== 'Role') applyRoleGate(role);
    });
    obs.observe(roleTag, { childList: true, characterData: true, subtree: true });

    // Also watch the dashboard visibility change
    const dashboard = document.getElementById('dashboard');
    if (dashboard) {
      const dashObs = new MutationObserver(() => {
        const role2 = (roleTag.textContent || '').trim();
        if (role2 && role2 !== 'Role') applyRoleGate(role2);
      });
      dashObs.observe(dashboard, { attributes: true, attributeFilter: ['style'] });
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', watchRoleTag);
  } else {
    watchRoleTag();
  }
})();
