/**
 * Mail View Component
 * Renders email lists, folder switching, filters, and full email reading pane.
 */
const MailView = {
    init() {
        this.listContainer = document.getElementById('emailListContainer');
        this.currentFolderTitle = document.getElementById('currentFolderTitle');
        this.mailCountText = document.getElementById('mailCountText');
        this.inboxUnreadBadge = document.getElementById('inboxUnreadBadge');
        this.activeFilterTag = document.getElementById('activeFilterTag');

        // Reading pane elements
        this.emptyDetailState = document.getElementById('emptyDetailState');
        this.activeEmailView = document.getElementById('activeEmailView');
        this.detailSubject = document.getElementById('detailSubject');
        this.detailFrom = document.getElementById('detailFrom');
        this.detailDate = document.getElementById('detailDate');
        this.detailTo = document.getElementById('detailTo');
        this.detailCc = document.getElementById('detailCc');
        this.detailCcRow = document.getElementById('detailCcRow');
        this.detailAvatar = document.getElementById('detailAvatar');
        this.emailHtmlFrame = document.getElementById('emailHtmlFrame');
        this.emailPlainText = document.getElementById('emailPlainText');
        this.detailReplyBtn = document.getElementById('detailReplyBtn');
        this.detailToggleReadBtn = document.getElementById('detailToggleReadBtn');
        this.detailAiSummarizeBtn = document.getElementById('detailAiSummarizeBtn');

        // Pagination
        this.prevPageBtn = document.getElementById('prevPageBtn');
        this.nextPageBtn = document.getElementById('nextPageBtn');

        this.bindEvents();
        this.bindState();
    },

    bindEvents() {
        // Folder navigation
        document.querySelectorAll('.sidebar-nav .nav-item').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('.sidebar-nav .nav-item').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                const folder = btn.getAttribute('data-folder');
                AppState.setFolder(folder);
                this.loadEmails();
            });
        });

        // Quick unread filter
        const quickUnreadBtn = document.getElementById('quickUnreadBtn');
        if (quickUnreadBtn) {
            quickUnreadBtn.addEventListener('click', () => {
                const current = AppState.filters.unreadOnly;
                AppState.setFilters({ unreadOnly: !current });
                quickUnreadBtn.classList.toggle('active', !current);
                this.loadEmails();
            });
        }

        // Pagination buttons
        if (this.nextPageBtn) {
            this.nextPageBtn.addEventListener('click', () => {
                if (AppState.nextPageToken) {
                    AppState.prevPageTokens.push(AppState.nextPageToken);
                    this.loadEmails(AppState.nextPageToken);
                }
            });
        }

        if (this.prevPageBtn) {
            this.prevPageBtn.addEventListener('click', () => {
                AppState.prevPageTokens.pop(); // current
                const prev = AppState.prevPageTokens.pop() || null;
                this.loadEmails(prev);
            });
        }

        // Detail action buttons
        if (this.detailReplyBtn) {
            this.detailReplyBtn.addEventListener('click', () => {
                if (AppState.selectedEmail && window.ComposeModal) {
                    ComposeModal.openReply({
                        emailId: AppState.selectedEmail.id,
                        threadId: AppState.selectedEmail.threadId,
                        to: AppState.selectedEmail.from,
                        subject: AppState.selectedEmail.subject,
                        body: ''
                    });
                }
            });
        }

        if (this.detailToggleReadBtn) {
            this.detailToggleReadBtn.addEventListener('click', () => {
                if (AppState.selectedEmail) {
                    const newReadState = !AppState.selectedEmail.isUnread;
                    this.toggleRead(AppState.selectedEmail.id, newReadState);
                }
            });
        }

        if (this.detailAiSummarizeBtn) {
            this.detailAiSummarizeBtn.addEventListener('click', () => {
                if (window.AiChat) {
                    AiChat.open();
                    AiChat.sendMessage('Please summarize this email into key points and action items.');
                }
            });
        }

        // Search & Filter controls
        const searchInput = document.getElementById('searchInput');
        const searchBtn = document.getElementById('searchBtn');
        const filterToggleBtn = document.getElementById('filterToggleBtn');
        const filterDropdown = document.getElementById('filterDropdown');
        const filterApplyBtn = document.getElementById('filterApplyBtn');
        const filterClearBtn = document.getElementById('filterClearBtn');

        if (searchBtn && searchInput) {
            searchBtn.addEventListener('click', () => {
                AppState.setFilters({ keyword: searchInput.value.trim() });
                this.loadEmails();
            });

            searchInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') {
                    AppState.setFilters({ keyword: searchInput.value.trim() });
                    this.loadEmails();
                }
            });
        }

        if (filterToggleBtn && filterDropdown) {
            filterToggleBtn.addEventListener('click', () => {
                filterDropdown.classList.toggle('hidden');
            });
        }

        if (filterApplyBtn) {
            filterApplyBtn.addEventListener('click', () => {
                const sender = document.getElementById('filterSender')?.value.trim() || '';
                const dateFrom = document.getElementById('filterDateFrom')?.value || '';
                const dateTo = document.getElementById('filterDateTo')?.value || '';
                const unreadOnly = !!document.getElementById('filterUnreadOnly')?.checked;

                AppState.setFilters({ sender, dateFrom, dateTo, unreadOnly });
                filterDropdown.classList.add('hidden');
                this.loadEmails();
            });
        }

        if (filterClearBtn) {
            filterClearBtn.addEventListener('click', () => {
                if (document.getElementById('filterSender')) document.getElementById('filterSender').value = '';
                if (document.getElementById('filterDateFrom')) document.getElementById('filterDateFrom').value = '';
                if (document.getElementById('filterDateTo')) document.getElementById('filterDateTo').value = '';
                if (document.getElementById('filterUnreadOnly')) document.getElementById('filterUnreadOnly').checked = false;
                if (searchInput) searchInput.value = '';

                AppState.clearFilters();
                filterDropdown.classList.add('hidden');
                this.loadEmails();
            });
        }
    },

    bindState() {
        AppState.on('folderChanged', (folder) => {
            if (this.currentFolderTitle) {
                this.currentFolderTitle.textContent = folder === 'SENT' ? 'Sent Messages' : 'Inbox';
            }
            // Update active nav button
            document.querySelectorAll('.sidebar-nav .nav-item').forEach(btn => {
                btn.classList.toggle('active', btn.getAttribute('data-folder') === folder);
            });
        });

        AppState.on('filtersChanged', (filters) => {
            const hasFilter = filters.keyword || filters.sender || filters.dateFrom || filters.dateTo || filters.unreadOnly;
            if (this.activeFilterTag) {
                this.activeFilterTag.classList.toggle('hidden', !hasFilter);
            }
            // Sync search input
            const searchInput = document.getElementById('searchInput');
            if (searchInput && filters.keyword !== undefined) {
                searchInput.value = filters.keyword;
            }
        });
    },

    async loadEmails(pageToken = null) {
        if (!AppState.isAuthenticated) return;

        this.listContainer.innerHTML = `
            <div class="loading-spinner-wrapper">
                <div class="spinner"></div>
                <p>Loading ${AppState.currentFolder.toLowerCase()} emails...</p>
            </div>
        `;

        try {
            const response = await Api.listEmails(AppState.currentFolder, AppState.filters, pageToken);
            AppState.setEmails(response);
            this.renderEmailList(response.emails);

            if (this.mailCountText) {
                this.mailCountText.textContent = `${response.emails.length} emails`;
            }

            if (this.inboxUnreadBadge && AppState.currentFolder === 'INBOX') {
                this.inboxUnreadBadge.textContent = response.unreadCount;
                this.inboxUnreadBadge.style.display = response.unreadCount > 0 ? 'inline-block' : 'none';
            }

            // Pagination state
            if (this.nextPageBtn) this.nextPageBtn.disabled = !response.nextPageToken;
            if (this.prevPageBtn) this.prevPageBtn.disabled = AppState.prevPageTokens.length === 0;

        } catch (err) {
            console.error('Failed to load emails:', err);
            this.listContainer.innerHTML = `
                <div class="empty-state">
                    <div class="empty-icon">⚠️</div>
                    <h3>Error loading emails</h3>
                    <p>${err.message}</p>
                </div>
            `;
        }
    },

    renderEmailList(emails) {
        if (!emails || emails.length === 0) {
            this.listContainer.innerHTML = `
                <div class="empty-state">
                    <div class="empty-icon">📭</div>
                    <h3>No emails found</h3>
                    <p>No messages match your current folder and filter criteria.</p>
                </div>
            `;
            return;
        }

        this.listContainer.innerHTML = '';
        emails.forEach(email => {
            const card = document.createElement('div');
            card.className = `email-card ${email.isUnread ? 'unread' : ''} ${AppState.selectedEmailId === email.id ? 'selected' : ''}`;
            card.dataset.id = email.id;

            const senderDisplay = this.formatSender(email.from);
            const formattedDate = this.formatDate(email.date);

            card.innerHTML = `
                <div class="email-card-top">
                    <div class="sender-group">
                        ${email.isUnread ? '<span class="card-unread-dot" title="Unread"></span>' : ''}
                        <span class="card-sender" title="${email.from}">${senderDisplay}</span>
                    </div>
                    <span class="card-date">${formattedDate}</span>
                </div>
                <div class="card-subject">${email.subject || '(No Subject)'}</div>
                <div class="card-snippet">${email.snippet || ''}</div>
            `;

            card.addEventListener('click', () => {
                document.querySelectorAll('.email-card').forEach(c => c.classList.remove('selected'));
                card.classList.add('selected');
                this.loadEmailDetail(email.id);
            });

            this.listContainer.appendChild(card);
        });
    },

    async loadEmailDetail(emailId) {
        if (!emailId) return;

        try {
            const detail = await Api.getEmailDetail(emailId);
            AppState.selectEmail(detail);
            this.renderEmailDetail(detail);

            // If it was unread, mark it as read automatically in Gmail
            if (detail.isUnread) {
                Api.markAsRead(emailId, true).catch(err => console.warn('Could not auto mark as read', err));
                // Update card in UI
                const card = document.querySelector(`.email-card[data-id="${emailId}"]`);
                if (card) {
                    card.classList.remove('unread');
                    const dot = card.querySelector('.card-unread-dot');
                    if (dot) dot.remove();
                }
            }

        } catch (err) {
            console.error('Failed to load email detail:', err);
            this.showToast(`Error loading email: ${err.message}`);
        }
    },

    renderEmailDetail(email) {
        if (!email) {
            this.emptyDetailState.classList.remove('hidden');
            this.activeEmailView.classList.add('hidden');
            return;
        }

        this.emptyDetailState.classList.add('hidden');
        this.activeEmailView.classList.remove('hidden');

        this.detailSubject.textContent = email.subject || '(No Subject)';
        this.detailFrom.textContent = email.from;
        this.detailDate.textContent = email.date;
        this.detailTo.textContent = email.to || 'me';

        if (email.cc && email.cc.trim()) {
            this.detailCc.textContent = email.cc;
            this.detailCcRow.classList.remove('hidden');
        } else {
            this.detailCcRow.classList.add('hidden');
        }

        // Sender initial
        const initial = this.getSenderInitial(email.from);
        this.detailAvatar.textContent = initial;

        // Toggle Read button text
        if (this.detailToggleReadBtn) {
            this.detailToggleReadBtn.textContent = email.isUnread ? 'Mark Read' : 'Mark Unread';
        }

        // Render body safely in iframe or fallback text
        if (email.bodyHtml && email.bodyHtml.trim()) {
            this.emailHtmlFrame.classList.remove('hidden');
            this.emailPlainText.classList.add('hidden');
            const sanitizedHtml = this.sanitizeHtml(email.bodyHtml);
            // Write sanitized HTML into iframe
            const doc = this.emailHtmlFrame.contentDocument || this.emailHtmlFrame.contentWindow.document;
            doc.open();
            doc.write(`
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <style>
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                            font-size: 14px;
                            line-height: 1.6;
                            color: #f8fafc;
                            background-color: transparent;
                            margin: 16px;
                            word-break: break-word;
                        }
                        a { color: #38bdf8; }
                        img { max-width: 100%; height: auto; }
                    </style>
                </head>
                <body>${sanitizedHtml}</body>
                </html>
            `);
            doc.close();
        } else {
            this.emailHtmlFrame.classList.add('hidden');
            this.emailPlainText.classList.remove('hidden');
            this.emailPlainText.textContent = email.bodyText || email.snippet || '(No message body)';
        }
    },

    sanitizeHtml(rawHtml) {
        if (!rawHtml) return '';
        // 1. Strip script, object, embed, and nested iframe tags
        let clean = rawHtml
            .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '')
            .replace(/<iframe\b[^<]*(?:(?!<\/iframe>)<[^<]*)*<\/iframe>/gi, '')
            .replace(/<object\b[^<]*(?:(?!<\/object>)<[^<]*)*<\/object>/gi, '')
            .replace(/<embed\b[^>]*>/gi, '');

        // 2. Remove inline event handlers (e.g. onload=, onclick=, onerror=)
        clean = clean.replace(/\son\w+\s*=\s*(?:'[^']*'|"[^"]*"|[^\s>]+)/gi, '');

        // 3. Remove dangerous javascript: and vbscript: URIs
        clean = clean.replace(/href\s*=\s*(?:'javascript:[^']*'|"javascript:[^"]*"|javascript:[^\s>]+)/gi, 'href="#"');
        clean = clean.replace(/src\s*=\s*(?:'javascript:[^']*'|"javascript:[^"]*"|javascript:[^\s>]+)/gi, 'src=""');

        return clean;
    },

    async toggleRead(emailId, markAsRead) {
        try {
            await Api.markAsRead(emailId, markAsRead);
            if (AppState.selectedEmail && AppState.selectedEmail.id === emailId) {
                AppState.selectedEmail = { ...AppState.selectedEmail, isUnread: !markAsRead };
                if (this.detailToggleReadBtn) {
                    this.detailToggleReadBtn.textContent = markAsRead ? 'Mark Unread' : 'Mark Read';
                }
            }
            // Update list item
            const card = document.querySelector(`.email-card[data-id="${emailId}"]`);
            if (card) {
                card.classList.toggle('unread', !markAsRead);
                const dot = card.querySelector('.card-unread-dot');
                if (markAsRead && dot) dot.remove();
                if (!markAsRead && !dot) {
                    const group = card.querySelector('.sender-group');
                    if (group) group.insertAdjacentHTML('afterbegin', '<span class="card-unread-dot"></span>');
                }
            }
        } catch (e) {
            console.error('Failed to update read state', e);
        }
    },

    formatSender(fromString) {
        if (!fromString) return 'Unknown';
        // Extract display name if formatted as "Name <email@example.com>"
        const match = fromString.match(/^(.*?)(?:<.*?>)?$/);
        if (match && match[1].trim()) {
            return match[1].replace(/["']/g, '').trim();
        }
        return fromString;
    },

    getSenderInitial(fromString) {
        const name = this.formatSender(fromString);
        return name ? name.charAt(0).toUpperCase() : '?';
    },

    formatDate(dateString) {
        if (!dateString) return '';
        try {
            const date = new Date(dateString);
            const now = new Date();
            if (date.toDateString() === now.toDateString()) {
                return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
            }
            return date.toLocaleDateString([], { month: 'short', day: 'numeric' });
        } catch {
            return dateString;
        }
    },

    showToast(message) {
        const toast = document.getElementById('toast');
        if (!toast) return;
        toast.textContent = message;
        toast.classList.remove('hidden');
        setTimeout(() => toast.classList.add('hidden'), 3500);
    }
};

window.MailView = MailView;
