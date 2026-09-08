/**
 * UI Action Dispatcher
 * Bridges AI Assistant tool decisions to actual interactive UI manipulations.
 */
const UiDispatcher = {
    dispatch(action) {
        if (!action || !action.action) return;
        const type = action.action;
        const params = action.parameters || {};

        console.log(`[UiDispatcher] Executing action: ${type}`, params);

        switch (type) {
            case 'NAVIGATE_FOLDER':
                this.navigateFolder(params.folder);
                break;

            case 'FILTER_EMAILS':
                this.filterEmails(params);
                break;

            case 'OPEN_EMAIL':
                this.openEmail(params.emailId);
                break;

            case 'POPULATE_COMPOSE':
                this.populateCompose(params);
                break;

            case 'POPULATE_REPLY':
                this.populateReply(params);
                break;

            case 'MARK_READ':
                this.markRead(params.emailId, params.isRead);
                break;

            default:
                console.warn(`[UiDispatcher] Unhandled action type: ${type}`);
        }
    },

    navigateFolder(folder) {
        const target = (folder || 'INBOX').toUpperCase();
        AppState.setFolder(target);
        if (window.MailView) {
            MailView.loadEmails();
        }
        this.showToast(`Switched folder to ${target}`);
    },

    filterEmails(params) {
        const newFilters = {};
        if (params.query) newFilters.keyword = params.query;
        if (params.sender) newFilters.sender = params.sender;
        if (params.dateFrom) newFilters.dateFrom = params.dateFrom;
        if (params.dateTo) newFilters.dateTo = params.dateTo;
        if (params.unreadOnly !== undefined) newFilters.unreadOnly = !!params.unreadOnly;

        // If folder specified in filter
        if (params.folder) {
            AppState.setFolder(params.folder.toUpperCase());
        }

        AppState.setFilters(newFilters);
        if (window.MailView) {
            MailView.loadEmails();
        }
        this.showToast('Applied AI email filters');
    },

    openEmail(emailId) {
        if (!emailId) return;
        if (window.MailView) {
            MailView.loadEmailDetail(emailId);
        }
        this.showToast('Opened email');
    },

    populateCompose(params) {
        if (window.ComposeModal) {
            ComposeModal.openCompose({
                to: params.to || '',
                subject: params.subject || '',
                body: params.body || ''
            });
        }
        this.showToast('Drafted email in composer');
    },

    populateReply(params) {
        if (window.ComposeModal) {
            ComposeModal.openReply({
                to: params.to || (AppState.selectedEmail ? AppState.selectedEmail.from : ''),
                subject: params.subject || (AppState.selectedEmail ? 'Re: ' + AppState.selectedEmail.subject : 'Re:'),
                body: params.replyBody || params.body || '',
                emailId: params.emailId || (AppState.selectedEmail ? AppState.selectedEmail.id : null),
                threadId: params.threadId || (AppState.selectedEmail ? AppState.selectedEmail.threadId : null)
            });
        }
        this.showToast('Drafted reply in composer');
    },

    markRead(emailId, isRead) {
        const id = emailId || (AppState.selectedEmail ? AppState.selectedEmail.id : null);
        if (!id) return;
        if (window.MailView) {
            MailView.toggleRead(id, isRead);
        }
    },

    showToast(message) {
        const toast = document.getElementById('toast');
        if (!toast) return;
        toast.textContent = `⚡ AI: ${message}`;
        toast.classList.remove('hidden');
        clearTimeout(this.toastTimeout);
        this.toastTimeout = setTimeout(() => {
            toast.classList.add('hidden');
        }, 3500);
    }
};

window.UiDispatcher = UiDispatcher;
