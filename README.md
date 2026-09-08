# 🚀 Nebula Mail — AI-Powered Mail Web Application

An intelligent, full-stack email web application built for the **Nebula KnowLab Hiring Assignment**. 
Powered by **Spring Boot**, **Java 17+**, **Real Gmail API**, **Google OAuth 2.0**, and **Google Gemini Native Function Calling** — with a responsive **Vanilla JavaScript, HTML5, and CSS3** 3-pane Single Page Application (SPA).

---

## ✨ Features

- **Real Gmail Integration**: Connects directly to a real Gmail account via Google OAuth 2.0 (no mock or fake data).
- **3-Pane Mail Interface**:
  - **Left Sidebar**: Folder navigation (`Inbox` with real-time unread badge, `Sent`), quick filters, and one-click compose.
  - **Middle List**: Fast email list with sender initial avatars, snippet previews, dates, unread indicators, and pagination.
  - **Right Detail Pane**: Full email inspection, sanitized HTML/plain text view, sender card, and quick reply action.
- **Search & Advanced Filters**:
  - Filter by sender (`from:`).
  - Date range filters (`after:`, `before:`).
  - Freeform keyword search.
  - Unread only toggle.
- **Compose & Reply**:
  - Send real emails with RFC 2822 MIME formatting.
  - Reply directly to threads preserving `In-Reply-To`, `References`, and `threadId` headers.
- **Real-Time Synchronization**:
  - Server-Sent Events (`/api/emails/sync/stream`) with Gmail `historyId` tracking.
  - Automatically updates the inbox when new emails arrive without requiring page refreshes.
- **Autonomous AI Assistant (Native Function Calling)**:
  - **Zero regex or if-else intent routing**: Powered by Gemini 1.5 Flash native LLM Tool Calling.
  - **Controls the actual UI**: The AI can switch folders, filter emails, open specific messages, pre-fill compose/reply windows, and summarize emails.
  - **Context-Aware**: Knows what email you are currently reading (e.g. *"Reply saying I will attend tomorrow"*, *"Summarize this email"*).

---

## 🛠️ Tech Stack

- **Backend**: Java 17+ (OpenJDK 25 LTS), Spring Boot 3.3.4, Maven 3.9.9 (Bundled wrapper).
- **Gmail SDK**: `google-api-services-gmail` v1, `google-api-client` 2.4.0, `jakarta.mail` 2.1.3.
- **AI Engine**: Google Gemini API via native Tool/Function Calling declarations.
- **Frontend**: Vanilla JavaScript (ES6+), Semantic HTML5, Modern CSS3 (CSS Variables, Flexbox, Grid).
- **Secrets Management**: `dotenv-java` (zero hardcoded secrets; loaded from `.env`).

---

## 📋 Prerequisites

1. **Java 17 or higher** installed (`java -version`).
2. **Google Cloud Console Project** with Gmail API enabled.
3. **Google Gemini API Key** from [Google AI Studio](https://aistudio.google.com/).

---

## ⚙️ Quick Start Setup

### Step 1: Clone and Configure Environment

Copy the example environment file:
```bash
cp .env.example .env
```

Edit `.env` and fill in your credentials:

```properties
# Google OAuth 2.0 Credentials
GOOGLE_CLIENT_ID=your_client_id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your_client_secret
GOOGLE_REDIRECT_URI=http://localhost:8080/api/auth/google/callback

# Google Gemini API Key
GEMINI_API_KEY=your_gemini_api_key

# Optional Server Port (default: 8080)
PORT=8080
```

> [!NOTE]
> ### How to get Google OAuth Credentials in 2 minutes:
> 1. Go to [Google Cloud Console](https://console.cloud.google.com/).
> 2. Create a project and navigate to **APIs & Services > Library**.
> 3. Search for **Gmail API** and click **Enable**.
> 4. Navigate to **APIs & Services > OAuth consent screen**:
>    - Select **External**, enter an App Name and your email.
>    - Under **Scopes**, add:
>      - `https://www.googleapis.com/auth/gmail.readonly`
>      - `https://www.googleapis.com/auth/gmail.send`
>      - `https://www.googleapis.com/auth/gmail.modify`
>    - Under **Test Users**, add your personal Gmail address.
> 5. Navigate to **APIs & Services > Credentials**:
>    - Click **Create Credentials > OAuth client ID**.
>    - Application type: **Web application**.
>    - Name: `Nebula Mail Web Client`.
>    - Authorized redirect URIs: `http://localhost:8080/api/auth/google/callback`.
>    - Copy the **Client ID** and **Client Secret** into your `.env` file.

---

### Step 2: Build & Run the Application

Run the bundled Maven wrapper:

```powershell
.\mvnw.cmd spring-boot:run
```

Once started, open your browser at:
👉 **[http://localhost:8080](http://localhost:8080)**

---

## 🧪 Testing the AI Assistant

Click the **✨ AI Assistant** button in the top right to open the conversational panel. Try commands like:

1. **Folder Navigation**:
   > *"Switch to my Sent folder"*
   
2. **Context-Aware Email Search & Filtering**:
   > *"Show me all unread emails from Google"*
   > *"Find emails sent this week"*

3. **Context-Aware Email Reply**:
   - Click to open any email in the list.
   - Say: *"Reply to this saying I've received the update and will review it by tomorrow."*
   - *Result*: The AI automatically generates an appropriate response, launches the compose window, sets the recipient and subject `Re:...`, and populates the drafted body for user review!

4. **Summarization**:
   - Open any email.
   - Say: *"Summarize this email for me"* (or click the **✨ Summarize with AI** button).
   - *Result*: The AI provides structured bullet points and action items.

5. **Email Composition**:
   > *"Compose an email to team@company.com about the project kickoff next Monday"*
   - *Result*: The compose modal immediately opens with recipient, subject, and professional message body prefilled.

---

## 🧪 Running Automated Tests

```powershell
.\mvnw.cmd test
```

Verifies:
- `SearchFilterTest`: Translates complex filters (sender, date ranges, unread status) into Gmail query syntax.
- `AiToolRegistryTest`: Validates the Gemini Function Calling schema declarations.
- `NebulaMailApplicationTests`: Verifies Spring Boot context integrity and dependency injection.
