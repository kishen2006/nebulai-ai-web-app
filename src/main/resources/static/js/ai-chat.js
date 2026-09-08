/**
 * AI Assistant Chat Component
 * Manages the slide-out AI Assistant drawer, context-awareness, and tool execution rendering.
 */
const AiChat = {
    init() {
        this.drawer = document.getElementById('aiDrawer');
        this.toggleBtn = document.getElementById('aiToggleBtn');
        this.closeBtn = document.getElementById('aiCloseBtn');
        this.contextText = document.getElementById('aiContextText');
        this.messagesContainer = document.getElementById('aiChatMessages');
        this.form = document.getElementById('aiChatForm');
        this.input = document.getElementById('aiInput');
        this.sendBtn = document.getElementById('aiSendBtn');

        this.bindEvents();
        this.bindState();
    },

    bindEvents() {
        if (this.toggleBtn) {
            this.toggleBtn.addEventListener('click', () => this.toggle());
        }

        if (this.closeBtn) {
            this.closeBtn.addEventListener('click', () => this.close());
        }

        if (this.form) {
            this.form.addEventListener('submit', (e) => {
                e.preventDefault();
                const msg = this.input.value.trim();
                if (msg) {
                    this.sendMessage(msg);
                }
            });
        }

        // Prompt suggestion chips
        document.addEventListener('click', (e) => {
            const chip = e.target.closest('.prompt-chip');
            if (chip) {
                const prompt = chip.getAttribute('data-prompt');
                if (prompt) {
                    this.open();
                    this.sendMessage(prompt);
                }
            }
        });
    },

    bindState() {
        AppState.on('folderChanged', () => this.updateContext());
        AppState.on('emailSelected', () => this.updateContext());
    },

    updateContext() {
        if (!this.contextText) return;
        if (AppState.selectedEmail) {
            const subj = AppState.selectedEmail.subject || '(No Subject)';
            this.contextText.textContent = `Viewing: ${subj.length > 25 ? subj.substring(0, 25) + '...' : subj}`;
        } else {
            this.contextText.textContent = `In: ${AppState.currentFolder === 'SENT' ? 'Sent' : 'Inbox'}`;
        }
    },

    toggle() {
        if (this.drawer.classList.contains('open')) {
            this.close();
        } else {
            this.open();
        }
    },

    open() {
        this.drawer.classList.add('open');
        AppState.isAiDrawerOpen = true;
        this.updateContext();
        if (this.input) this.input.focus();
    },

    close() {
        this.drawer.classList.remove('open');
        AppState.isAiDrawerOpen = false;
    },

    async sendMessage(promptText) {
        if (!promptText.trim()) return;

        // Add user message to UI
        this.appendMessage('user', promptText);
        this.input.value = '';
        this.input.disabled = true;
        this.sendBtn.disabled = true;

        // Show typing indicator
        const typingId = this.showTypingIndicator();

        // Get live UI context
        const context = AppState.getUiContext();

        try {
            const response = await Api.sendAiChat(promptText, context, AppState.chatHistory);
            this.removeTypingIndicator(typingId);

            // Record history
            AppState.chatHistory.push({ role: 'user', content: promptText });
            AppState.chatHistory.push({ role: 'assistant', content: response.reply });

            // Render AI response bubble
            this.appendMessage('assistant', response.reply, response.actions);

            // Execute UI Actions returned by LLM function calling!
            if (response.actions && response.actions.length > 0) {
                response.actions.forEach(action => {
                    if (window.UiDispatcher) {
                        UiDispatcher.dispatch(action);
                    }
                });
            }

        } catch (err) {
            console.error('AI chat failed:', err);
            this.removeTypingIndicator(typingId);
            this.appendMessage('assistant', `⚠️ Error communicating with AI: ${err.message}`);
        } finally {
            this.input.disabled = false;
            this.sendBtn.disabled = false;
            this.input.focus();
        }
    },

    appendMessage(role, text, actions = []) {
        const msgDiv = document.createElement('div');
        msgDiv.className = `ai-message ${role}`;

        const avatar = document.createElement('div');
        avatar.className = 'ai-avatar';
        avatar.textContent = role === 'user' ? '👤' : '✨';

        const bubble = document.createElement('div');
        bubble.className = 'ai-bubble';

        // Format linebreaks and bullet points
        bubble.innerHTML = this.formatMarkdown(text);

        // If actions were executed, attach action cards
        if (actions && actions.length > 0) {
            actions.forEach(act => {
                const actionCard = document.createElement('div');
                actionCard.className = 'ai-action-card';
                actionCard.innerHTML = `
                    <span class="ai-action-icon">⚡</span>
                    <span>Action executed: <strong>${this.formatActionName(act.action)}</strong></span>
                `;
                bubble.appendChild(actionCard);
            });
        }

        msgDiv.appendChild(avatar);
        msgDiv.appendChild(bubble);

        this.messagesContainer.appendChild(msgDiv);
        this.scrollToBottom();
    },

    showTypingIndicator() {
        const id = 'typing_' + Date.now();
        const msgDiv = document.createElement('div');
        msgDiv.id = id;
        msgDiv.className = 'ai-message assistant';
        msgDiv.innerHTML = `
            <div class="ai-avatar">✨</div>
            <div class="ai-bubble">
                <div class="ai-typing-indicator">
                    <div class="typing-dot"></div>
                    <div class="typing-dot"></div>
                    <div class="typing-dot"></div>
                </div>
            </div>
        `;
        this.messagesContainer.appendChild(msgDiv);
        this.scrollToBottom();
        return id;
    },

    removeTypingIndicator(id) {
        const elem = document.getElementById(id);
        if (elem) elem.remove();
    },

    formatMarkdown(text) {
        if (!text) return '';
        let escaped = text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');

        // Bold **text**
        escaped = escaped.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
        // Bullets
        escaped = escaped.replace(/^[\*\-]\s+(.*)$/gm, '<li>$1</li>');
        if (escaped.includes('<li>')) {
            escaped = escaped.replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>');
        }
        // Newlines
        escaped = escaped.replace(/\n/g, '<br>');

        return escaped;
    },

    formatActionName(action) {
        switch (action) {
            case 'NAVIGATE_FOLDER': return 'Folder Switched';
            case 'FILTER_EMAILS': return 'Search Filters Applied';
            case 'OPEN_EMAIL': return 'Email Opened';
            case 'POPULATE_COMPOSE': return 'Drafted in Composer';
            case 'POPULATE_REPLY': return 'Drafted Reply';
            case 'MARK_READ': return 'Read Status Updated';
            default: return action;
        }
    },

    scrollToBottom() {
        this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
    }
};

window.AiChat = AiChat;
