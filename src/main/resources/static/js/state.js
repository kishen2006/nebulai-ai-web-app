/**
 * State Management for Nebula Mail
 * Holds central reactive state and notifies registered listeners on state changes.
 */
const AppState = {
    user: null,
    isAuthenticated: false,
    isConfigured: false,

    currentFolder: 'INBOX', // 'INBOX' | 'SENT'
    emails: [],
    selectedEmailId: null,
    selectedEmail: null,
    unreadCount: 0,
    nextPageToken: null,
    prevPageTokens: [],

    // Current Search & Filter criteria
    filters: {
        keyword: '',
        sender: '',
        dateFrom: '',
        dateTo: '',
        unreadOnly: false
    },

    isComposeOpen: false,
    isAiDrawerOpen: false,

    // AI Chat History
    chatHistory: [],

    // Listeners for state change events
    listeners: {},

    on(event, callback) {
        if (!this.listeners[event]) {
            this.listeners[event] = [];
        }
        this.listeners[event].push(callback);
    },

    emit(event, data) {
        if (this.listeners[event]) {
            this.listeners[event].forEach(cb => cb(data));
        }
    },

    setAuth(authStatus) {
        this.isAuthenticated = authStatus.authenticated;
        this.isConfigured = authStatus.configured;
        this.user = authStatus.user;
        this.emit('authChanged', authStatus);
    },

    setFolder(folder) {
        if (this.currentFolder !== folder) {
            this.currentFolder = folder;
            this.selectedEmailId = null;
            this.selectedEmail = null;
            this.nextPageToken = null;
            this.prevPageTokens = [];
            this.emit('folderChanged', folder);
            this.emit('emailSelected', null);
        }
    },

    setEmails(emailListResponse) {
        this.emails = emailListResponse.emails || [];
        this.nextPageToken = emailListResponse.nextPageToken || null;
        this.unreadCount = emailListResponse.unreadCount || 0;
        this.emit('emailsUpdated', this.emails);
    },

    selectEmail(emailDetail) {
        this.selectedEmail = emailDetail;
        this.selectedEmailId = emailDetail ? emailDetail.id : null;
        this.emit('emailSelected', emailDetail);
    },

    setFilters(newFilters) {
        this.filters = { ...this.filters, ...newFilters };
        this.nextPageToken = null;
        this.prevPageTokens = [];
        this.emit('filtersChanged', this.filters);
    },

    clearFilters() {
        this.filters = {
            keyword: '',
            sender: '',
            dateFrom: '',
            dateTo: '',
            unreadOnly: false
        };
        this.nextPageToken = null;
        this.prevPageTokens = [];
        this.emit('filtersChanged', this.filters);
    },

    getUiContext() {
        return {
            currentFolder: this.currentFolder,
            selectedEmailId: this.selectedEmailId,
            selectedEmailSubject: this.selectedEmail ? this.selectedEmail.subject : null,
            selectedEmailFrom: this.selectedEmail ? this.selectedEmail.from : null,
            selectedEmailSnippet: this.selectedEmail ? this.selectedEmail.snippet : null,
            selectedEmailBody: this.selectedEmail ? (this.selectedEmail.bodyText || this.selectedEmail.snippet) : null,
            isComposeOpen: this.isComposeOpen,
            visibleEmails: this.emails.slice(0, 10).map(e => ({
                id: e.id,
                from: e.from,
                subject: e.subject,
                isUnread: e.isUnread
            }))
        };
    }
};

window.AppState = AppState;
