/**
 * API Service for communicating with Spring Boot REST endpoints.
 */
const Api = {
    async checkAuthStatus() {
        const res = await fetch('/api/auth/status');
        if (!res.ok) throw new Error('Failed to check auth status');
        return await res.json();
    },

    async logout() {
        const res = await fetch('/api/auth/logout', { method: 'POST' });
        if (!res.ok) throw new Error('Failed to log out');
        return await res.json();
    },

    async listEmails(folder = 'INBOX', filters = {}, pageToken = null) {
        const params = new URLSearchParams();
        params.append('folder', folder);
        if (filters.keyword) params.append('q', filters.keyword);
        if (filters.sender) params.append('sender', filters.sender);
        if (filters.dateFrom) params.append('dateFrom', filters.dateFrom);
        if (filters.dateTo) params.append('dateTo', filters.dateTo);
        if (filters.unreadOnly) params.append('unreadOnly', 'true');
        if (pageToken) params.append('pageToken', pageToken);
        params.append('maxResults', '25');

        const res = await fetch(`/api/emails?${params.toString()}`);
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || 'Failed to fetch emails');
        }
        return await res.json();
    },

    async getEmailDetail(id) {
        const res = await fetch(`/api/emails/${encodeURIComponent(id)}`);
        if (!res.ok) throw new Error('Failed to load email detail');
        return await res.json();
    },

    async sendEmail(payload) {
        const res = await fetch('/api/emails/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || 'Failed to send email');
        }
        return await res.json();
    },

    async replyEmail(payload) {
        const res = await fetch('/api/emails/reply', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || 'Failed to reply to email');
        }
        return await res.json();
    },

    async markAsRead(id, isRead = true) {
        const res = await fetch(`/api/emails/${encodeURIComponent(id)}/read?read=${isRead}`, {
            method: 'PATCH'
        });
        if (!res.ok) throw new Error('Failed to update email read state');
        return await res.json();
    },

    async triggerSync() {
        const res = await fetch('/api/emails/sync/refresh', { method: 'POST' });
        if (!res.ok) throw new Error('Failed to trigger manual sync');
        return await res.json();
    },

    async sendAiChat(message, context, history) {
        const res = await fetch('/api/ai/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message, context, history })
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || 'Failed to chat with AI assistant');
        }
        return await res.json();
    }
};

window.Api = Api;
