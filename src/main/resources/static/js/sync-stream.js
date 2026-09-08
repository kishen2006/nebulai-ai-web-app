/**
 * Real-Time Mailbox Synchronization Component
 * Subscribes to Server-Sent Events (SSE) from the backend to receive live inbox updates.
 */
const SyncStream = {
    eventSource: null,
    reconnectTimeout: null,

    init() {
        this.statusDot = document.querySelector('.sync-dot');
        this.syncText = document.querySelector('.sync-text');
        this.manualSyncBtn = document.getElementById('manualSyncBtn');

        if (this.manualSyncBtn) {
            this.manualSyncBtn.addEventListener('click', () => this.manualSync());
        }

        AppState.on('authChanged', (status) => {
            if (status.authenticated) {
                this.connect();
            } else {
                this.disconnect();
            }
        });
    },

    connect() {
        if (this.eventSource) {
            this.eventSource.close();
        }

        console.log('[SyncStream] Connecting to live mail sync SSE stream...');
        this.eventSource = new EventSource('/api/emails/sync/stream');

        this.eventSource.addEventListener('INIT', (event) => {
            console.log('[SyncStream] Connected to stream:', event.data);
            this.setLiveState(true);
        });

        this.eventSource.addEventListener('SYNC', (event) => {
            console.log('[SyncStream] Received SYNC event:', event.data);
            try {
                const data = JSON.parse(event.data);
                if (data.hasNewMessages) {
                    this.showSyncNotification('New email arrived in Gmail!');
                    if (window.MailView) {
                        MailView.loadEmails();
                    }
                }
            } catch (e) {
                console.warn('[SyncStream] Error parsing sync event:', e);
            }
        });

        this.eventSource.onerror = (err) => {
            console.warn('[SyncStream] SSE connection error, reconnecting in 10s...', err);
            this.setLiveState(false);
            this.eventSource.close();
            this.eventSource = null;

            clearTimeout(this.reconnectTimeout);
            this.reconnectTimeout = setTimeout(() => {
                if (AppState.isAuthenticated) {
                    this.connect();
                }
            }, 10000);
        };
    },

    disconnect() {
        if (this.eventSource) {
            this.eventSource.close();
            this.eventSource = null;
        }
        this.setLiveState(false);
    },

    async manualSync() {
        if (this.manualSyncBtn) {
            this.manualSyncBtn.classList.add('spinning');
        }

        try {
            const res = await Api.triggerSync();
            if (res.updated && window.MailView) {
                MailView.loadEmails();
                this.showSyncNotification('Mailbox refreshed with latest Gmail updates.');
            } else if (window.MailView) {
                MailView.loadEmails();
                this.showSyncNotification('Mailbox is up to date.');
            }
        } catch (e) {
            console.error('Manual sync failed', e);
        } finally {
            if (this.manualSyncBtn) {
                setTimeout(() => this.manualSyncBtn.classList.remove('spinning'), 600);
            }
        }
    },

    setLiveState(isLive) {
        if (this.statusDot) {
            this.statusDot.className = `sync-dot ${isLive ? 'live' : ''}`;
        }
        if (this.syncText) {
            this.syncText.textContent = isLive ? 'Live Sync' : 'Sync Paused';
        }
    },

    showSyncNotification(msg) {
        const toast = document.getElementById('toast');
        if (!toast) return;
        toast.textContent = `📬 ${msg}`;
        toast.classList.remove('hidden');
        setTimeout(() => toast.classList.add('hidden'), 3500);
    }
};

window.SyncStream = SyncStream;
