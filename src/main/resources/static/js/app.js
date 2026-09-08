/**
 * Application Bootstrap
 * Initializes state, verifies authentication, and activates components.
 */
document.addEventListener('DOMContentLoaded', async () => {
    console.log('[NebulaMail] Initializing application...');

    // Initialize UI Components
    MailView.init();
    ComposeModal.init();
    SyncStream.init();
    AiChat.init();

    // Handle URL parameters (e.g., ?login=success or ?error=...)
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('error')) {
        showGlobalToast(`⚠️ ${urlParams.get('error')}`);
        // Clean URL
        window.history.replaceState({}, document.title, window.location.pathname);
    } else if (urlParams.get('login') === 'success') {
        showGlobalToast('✨ Successfully connected to your Gmail account!');
        window.history.replaceState({}, document.title, window.location.pathname);
    }

    // Check Google OAuth authentication status
    await checkAuthentication();

    // Setup Logout Button
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', async () => {
            try {
                await Api.logout();
                window.location.reload();
            } catch (e) {
                console.error('Logout error', e);
            }
        });
    }
});

async function checkAuthentication() {
    const loginOverlay = document.getElementById('loginOverlay');
    const userProfileEl = document.getElementById('userProfile');
    const userEmailEl = document.getElementById('userEmail');
    const userAvatarEl = document.getElementById('userAvatar');
    const configWarning = document.getElementById('configWarning');
    const googleLoginBtn = document.getElementById('googleLoginBtn');

    try {
        const status = await Api.checkAuthStatus();
        AppState.setAuth(status);

        if (status.authenticated && status.user) {
            // User is authenticated
            loginOverlay.classList.add('hidden');
            userProfileEl.classList.remove('hidden');

            const email = status.user.email || '';
            userEmailEl.textContent = email;
            userAvatarEl.textContent = email.charAt(0).toUpperCase();

            // Load initial Inbox emails
            MailView.loadEmails();

        } else {
            // User is not authenticated
            loginOverlay.classList.remove('hidden');
            userProfileEl.classList.add('hidden');

            if (!status.configured) {
                configWarning.classList.remove('hidden');
                googleLoginBtn.classList.add('hidden');
            } else {
                configWarning.classList.add('hidden');
                googleLoginBtn.classList.remove('hidden');
            }
        }
    } catch (err) {
        console.error('Failed to verify authentication status:', err);
        loginOverlay.classList.remove('hidden');
    }
}

function showGlobalToast(msg) {
    const toast = document.getElementById('toast');
    if (!toast) return;
    toast.textContent = msg;
    toast.classList.remove('hidden');
    setTimeout(() => toast.classList.add('hidden'), 4000);
}

window.showGlobalToast = showGlobalToast;
