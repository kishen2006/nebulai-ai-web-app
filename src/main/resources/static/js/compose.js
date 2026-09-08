/**
 * Compose & Reply Modal Component
 */
const ComposeModal = {
    init() {
        this.modal = document.getElementById('composeModal');
        this.title = document.getElementById('composeModalTitle');
        this.form = document.getElementById('composeForm');
        this.toInput = document.getElementById('composeTo');
        this.ccInput = document.getElementById('composeCc');
        this.subjectInput = document.getElementById('composeSubject');
        this.bodyInput = document.getElementById('composeBody');
        this.closeBtn = document.getElementById('composeCloseBtn');
        this.discardBtn = document.getElementById('composeDiscardBtn');
        this.sendBtn = document.getElementById('composeSendBtn');
        this.composeBtn = document.getElementById('composeBtn');
        this.aiAssistBtn = document.getElementById('composeAiAssistBtn');

        this.replyMode = false;
        this.replyTarget = null; // { emailId, threadId }

        this.bindEvents();
    },

    bindEvents() {
        if (this.composeBtn) {
            this.composeBtn.addEventListener('click', () => this.openCompose());
        }

        if (this.closeBtn) {
            this.closeBtn.addEventListener('click', () => this.close());
        }

        if (this.discardBtn) {
            this.discardBtn.addEventListener('click', () => this.close());
        }

        if (this.form) {
            this.form.addEventListener('submit', (e) => {
                e.preventDefault();
                this.handleSend();
            });
        }

        if (this.aiAssistBtn) {
            this.aiAssistBtn.addEventListener('click', () => {
                const currentText = this.bodyInput.value;
                if (!currentText.trim()) {
                    if (window.AiChat) {
                        AiChat.open();
                        AiChat.sendMessage(`Please help draft an email to ${this.toInput.value || 'the recipient'} regarding ${this.subjectInput.value || 'an update'}.`);
                    }
                    return;
                }
                if (window.AiChat) {
                    AiChat.open();
                    AiChat.sendMessage(`Please polish and improve this email draft: "${currentText}"`);
                }
            });
        }
    },

    openCompose(prefill = {}) {
        this.replyMode = false;
        this.replyTarget = null;
        this.title.textContent = 'New Message';

        this.toInput.value = prefill.to || '';
        this.ccInput.value = prefill.cc || '';
        this.subjectInput.value = prefill.subject || '';
        this.bodyInput.value = prefill.body || '';

        this.modal.classList.remove('hidden');
        AppState.isComposeOpen = true;

        if (!prefill.to) {
            this.toInput.focus();
        } else if (!prefill.body) {
            this.bodyInput.focus();
        }
    },

    openReply(replyInfo = {}) {
        this.replyMode = true;
        this.replyTarget = {
            emailId: replyInfo.emailId,
            threadId: replyInfo.threadId
        };

        this.title.textContent = `Reply: ${replyInfo.subject || ''}`;
        this.toInput.value = replyInfo.to || '';
        this.ccInput.value = replyInfo.cc || '';
        
        let subj = replyInfo.subject || '';
        if (subj && !subj.toLowerCase().startsWith('re:')) {
            subj = 'Re: ' + subj;
        }
        this.subjectInput.value = subj;
        this.bodyInput.value = replyInfo.body || '';

        this.modal.classList.remove('hidden');
        AppState.isComposeOpen = true;
        this.bodyInput.focus();
    },

    close() {
        this.modal.classList.add('hidden');
        AppState.isComposeOpen = false;
        this.form.reset();
        this.replyMode = false;
        this.replyTarget = null;
    },

    async handleSend() {
        const to = this.toInput.value.trim();
        const cc = this.ccInput.value.trim();
        const subject = this.subjectInput.value.trim();
        const body = this.bodyInput.value.trim();

        if (!to || !subject || !body) {
            alert('Please fill out all required fields (To, Subject, Body).');
            return;
        }

        this.sendBtn.disabled = true;
        this.sendBtn.textContent = 'Sending...';

        try {
            if (this.replyMode && this.replyTarget && this.replyTarget.emailId) {
                await Api.replyEmail({
                    emailId: this.replyTarget.emailId,
                    threadId: this.replyTarget.threadId,
                    to,
                    subject,
                    body,
                    isHtml: false
                });
                this.showToast('Reply sent successfully via Gmail!');
            } else {
                await Api.sendEmail({
                    to,
                    cc,
                    subject,
                    body,
                    isHtml: false
                });
                this.showToast('Email sent successfully via Gmail!');
            }

            this.close();

            // Refresh email list if in Sent folder
            if (AppState.currentFolder === 'SENT' && window.MailView) {
                MailView.loadEmails();
            }

        } catch (err) {
            console.error('Failed to send email:', err);
            alert(`Failed to send email: ${err.message}`);
        } finally {
            this.sendBtn.disabled = false;
            this.sendBtn.textContent = 'Send Email';
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

window.ComposeModal = ComposeModal;
