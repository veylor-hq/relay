package com.veylor.relay.controller;

import com.veylor.relay.repository.ApplicationRepository;
import com.veylor.relay.repository.EmailSenderRepository;
import com.veylor.relay.repository.NotificationJobRepository;
import com.veylor.relay.repository.NotificationLogRepository;
import com.veylor.relay.repository.RecipientRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminViewController {

    private final ApplicationRepository applicationRepository;
    private final EmailSenderRepository emailSenderRepository;
    private final RecipientRepository recipientRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final NotificationJobRepository notificationJobRepository;

    @Getter
    @Builder
    public static class AdminOverviewSummary {
        private long totalApplications;
        private long totalSenders;
        private long totalRecipients;
        private long queuedOutboxJobs;
        private long sentNotifications;
        private long failedNotifications;
        private long pendingNotifications;
    }

    @GetMapping("/api/v1/admin/summary")
    public ResponseEntity<AdminOverviewSummary> getSummary() {
        AdminOverviewSummary summary = AdminOverviewSummary.builder()
                .totalApplications(applicationRepository.count())
                .totalSenders(emailSenderRepository.count())
                .totalRecipients(recipientRepository.count())
                .queuedOutboxJobs(notificationJobRepository.count())
                .sentNotifications(notificationLogRepository.findByStatus("SENT", PageRequest.of(0, 1)).getTotalElements())
                .failedNotifications(notificationLogRepository.findByStatus("FAILED", PageRequest.of(0, 1)).getTotalElements())
                .pendingNotifications(notificationLogRepository.findByStatus("PENDING", PageRequest.of(0, 1)).getTotalElements())
                .build();

        return ResponseEntity.ok(summary);
    }

    @GetMapping(value = "/admin", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String getAdminDashboardHtml() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Veylor Relay | Admin Console</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;600&family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-base: #0c0f14;
            --bg-card: #151921;
            --bg-card-hover: #1c222c;
            --border-subtle: #252c38;
            --border-strong: #333c4d;
            --text-main: #f0f3f8;
            --text-muted: #8b9bb4;
            --accent-primary: #3b82f6;
            --accent-primary-hover: #2563eb;
            --accent-success: #10b981;
            --accent-warning: #f59e0b;
            --accent-danger: #ef4444;
            --code-bg: #1e2430;
        }

        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: 'Plus Jakarta Sans', -apple-system, sans-serif;
            background: var(--bg-base);
            color: var(--text-main);
            line-height: 1.5;
            min-height: 100vh;
        }

        /* Top Bar */
        header {
            background: var(--bg-card);
            border-bottom: 1px solid var(--border-subtle);
            padding: 1rem 2rem;
            display: flex;
            align-items: center;
            justify-content: space-between;
            position: sticky;
            top: 0;
            z-index: 100;
        }
        .brand {
            display: flex;
            align-items: center;
            gap: 0.75rem;
            font-weight: 700;
            font-size: 1.15rem;
            letter-spacing: -0.02em;
        }
        .badge {
            background: rgba(59, 130, 246, 0.15);
            color: var(--accent-primary);
            font-size: 0.72rem;
            padding: 0.2rem 0.6rem;
            border-radius: 9999px;
            font-family: 'JetBrains Mono', monospace;
            border: 1px solid rgba(59, 130, 246, 0.3);
        }
        .auth-bar {
            display: flex;
            align-items: center;
            gap: 0.6rem;
        }
        .auth-bar input {
            background: var(--bg-base);
            border: 1px solid var(--border-subtle);
            color: var(--text-main);
            padding: 0.45rem 0.85rem;
            border-radius: 6px;
            font-size: 0.85rem;
            width: 260px;
            font-family: 'JetBrains Mono', monospace;
        }
        .auth-bar input:focus {
            outline: none;
            border-color: var(--accent-primary);
        }

        /* Layout */
        .container {
            max-width: 1380px;
            margin: 0 auto;
            padding: 2rem;
        }

        /* Tabs */
        .nav-tabs {
            display: flex;
            gap: 0.5rem;
            margin-bottom: 1.5rem;
            border-bottom: 1px solid var(--border-subtle);
            padding-bottom: 0.75rem;
        }
        .nav-tab {
            background: transparent;
            border: none;
            color: var(--text-muted);
            font-weight: 600;
            font-size: 0.95rem;
            padding: 0.5rem 1rem;
            cursor: pointer;
            border-radius: 6px;
            transition: all 0.15s ease;
        }
        .nav-tab:hover {
            color: var(--text-main);
            background: var(--bg-card);
        }
        .nav-tab.active {
            color: #ffffff;
            background: var(--accent-primary);
        }

        /* Metric Grid */
        .metrics-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 1.25rem;
            margin-bottom: 2rem;
        }
        .metric-card {
            background: var(--bg-card);
            border: 1px solid var(--border-subtle);
            padding: 1.25rem;
            border-radius: 10px;
        }
        .metric-title {
            font-size: 0.8rem;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: var(--text-muted);
            margin-bottom: 0.4rem;
        }
        .metric-value {
            font-size: 1.85rem;
            font-weight: 700;
            font-family: 'JetBrains Mono', monospace;
        }

        /* Content Sections */
        .section-panel {
            display: none;
        }
        .section-panel.active {
            display: block;
        }

        .action-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 1.25rem;
        }
        .btn {
            background: var(--accent-primary);
            color: #ffffff;
            border: none;
            padding: 0.5rem 1rem;
            font-weight: 600;
            font-size: 0.85rem;
            border-radius: 6px;
            cursor: pointer;
            transition: background 0.15s ease;
            display: inline-flex;
            align-items: center;
            gap: 0.4rem;
        }
        .btn:hover { background: var(--accent-primary-hover); }
        .btn-secondary {
            background: var(--bg-card);
            border: 1px solid var(--border-subtle);
            color: var(--text-main);
        }
        .btn-secondary:hover { background: var(--bg-card-hover); }
        .btn-danger { background: var(--accent-danger); }
        .btn-danger:hover { background: #dc2626; }
        .btn-sm { padding: 0.3rem 0.6rem; font-size: 0.78rem; }

        /* Tables */
        .table-wrap {
            background: var(--bg-card);
            border: 1px solid var(--border-subtle);
            border-radius: 10px;
            overflow-x: auto;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            text-align: left;
            font-size: 0.88rem;
        }
        th {
            background: rgba(255, 255, 255, 0.02);
            padding: 0.85rem 1.15rem;
            color: var(--text-muted);
            font-weight: 600;
            border-bottom: 1px solid var(--border-subtle);
            font-size: 0.78rem;
            text-transform: uppercase;
            letter-spacing: 0.05em;
        }
        td {
            padding: 0.85rem 1.15rem;
            border-bottom: 1px solid var(--border-subtle);
            color: var(--text-main);
        }
        tr:last-child td { border-bottom: none; }
        tr:hover td { background: rgba(255, 255, 255, 0.015); }

        .tag {
            font-family: 'JetBrains Mono', monospace;
            font-size: 0.75rem;
            padding: 0.15rem 0.5rem;
            border-radius: 4px;
            display: inline-block;
        }
        .tag-active { background: rgba(16, 185, 129, 0.15); color: var(--accent-success); border: 1px solid rgba(16, 185, 129, 0.3); }
        .tag-inactive { background: rgba(239, 68, 68, 0.15); color: var(--accent-danger); border: 1px solid rgba(239, 68, 68, 0.3); }
        .tag-pending { background: rgba(245, 158, 11, 0.15); color: var(--accent-warning); border: 1px solid rgba(245, 158, 11, 0.3); }

        .code-box {
            font-family: 'JetBrains Mono', monospace;
            background: var(--code-bg);
            padding: 0.2rem 0.4rem;
            border-radius: 4px;
            font-size: 0.8rem;
            color: #cbd5e1;
        }

        /* Modal */
        .modal-overlay {
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(0, 0, 0, 0.75);
            display: none;
            align-items: center;
            justify-content: center;
            z-index: 1000;
            backdrop-filter: blur(3px);
        }
        .modal-overlay.active { display: flex; }
        .modal {
            background: var(--bg-card);
            border: 1px solid var(--border-strong);
            border-radius: 12px;
            width: 100%;
            max-width: 520px;
            padding: 1.75rem;
            box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);
        }
        .modal-title {
            font-size: 1.15rem;
            font-weight: 700;
            margin-bottom: 1.25rem;
        }
        .form-group {
            margin-bottom: 1rem;
        }
        .form-group label {
            display: block;
            font-size: 0.82rem;
            font-weight: 600;
            color: var(--text-muted);
            margin-bottom: 0.35rem;
        }
        .form-control {
            width: 100%;
            background: var(--bg-base);
            border: 1px solid var(--border-subtle);
            color: var(--text-main);
            padding: 0.5rem 0.75rem;
            border-radius: 6px;
            font-size: 0.88rem;
        }
        .form-control:focus { outline: none; border-color: var(--accent-primary); }
        .modal-actions {
            display: flex;
            justify-content: flex-end;
            gap: 0.75rem;
            margin-top: 1.5rem;
        }

        .alert-box {
            padding: 0.85rem;
            border-radius: 6px;
            margin-bottom: 1rem;
            font-size: 0.85rem;
        }
        .alert-success { background: rgba(16, 185, 129, 0.15); border: 1px solid rgba(16, 185, 129, 0.3); color: #6ee7b7; }
        .alert-danger { background: rgba(239, 68, 68, 0.15); border: 1px solid rgba(239, 68, 68, 0.3); color: #fca5a5; }
    </style>
</head>
<body>

<div id="loginGate" style="min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 2rem;">
    <div style="background: var(--bg-card); border: 1px solid var(--border-subtle); border-radius: 12px; padding: 2.5rem; width: 100%; max-width: 440px; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);">
        <div style="display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.5rem;">
            <span style="font-size: 1.6rem;">⚡</span>
            <h2 style="font-size: 1.35rem; font-weight: 700; letter-spacing: -0.02em;">Veylor Relay</h2>
        </div>
        <p style="color: var(--text-muted); font-size: 0.88rem; margin-bottom: 1.5rem;">
            Enter your Admin Secret Token to access the management console.
        </p>
        <div id="loginError" class="alert-box alert-danger" style="display: none; margin-bottom: 1.25rem;"></div>
        <form onsubmit="handleLogin(event)">
            <div class="form-group" style="margin-bottom: 1.25rem;">
                <label for="adminTokenInput">Admin Secret Token</label>
                <input type="password" id="adminTokenInput" class="form-control" placeholder="Enter X-Admin-Token" autocomplete="off" required>
            </div>
            <button type="submit" id="loginBtn" class="btn" style="width: 100%; justify-content: center; padding: 0.65rem;">
                Access Console
            </button>
        </form>
    </div>
</div>

<div id="appDashboard" style="display: none;">

<header>
    <div class="brand">
        <span>⚡ Veylor Relay</span>
        <span class="badge">V1 Microservice Console</span>
    </div>
    <div class="auth-bar">
        <span class="tag tag-active" style="display: flex; align-items: center; gap: 0.4rem;">
            <span style="width: 6px; height: 6px; background: var(--accent-success); border-radius: 50%;"></span>
            Authenticated
        </span>
        <button class="btn btn-secondary btn-sm" onclick="logoutAdmin()">Lock / Sign Out</button>
    </div>
</header>

<div class="container">

    <!-- Metrics -->
    <div class="metrics-grid">
        <div class="metric-card">
            <div class="metric-title">Registered Apps</div>
            <div class="metric-value" id="m-apps">-</div>
        </div>
        <div class="metric-card">
            <div class="metric-title">Active Senders</div>
            <div class="metric-value" id="m-senders">-</div>
        </div>
        <div class="metric-card">
            <div class="metric-title">Outbox In-Flight</div>
            <div class="metric-value" id="m-outbox" style="color: var(--accent-warning);">-</div>
        </div>
        <div class="metric-card">
            <div class="metric-title">Delivered (Sent)</div>
            <div class="metric-value" id="m-sent" style="color: var(--accent-success);">-</div>
        </div>
        <div class="metric-card">
            <div class="metric-title">Failed Dispatches</div>
            <div class="metric-value" id="m-failed" style="color: var(--accent-danger);">-</div>
        </div>
    </div>

    <!-- Navigation Tabs -->
    <div class="nav-tabs">
        <button class="nav-tab active" onclick="switchTab('apps')">Applications</button>
        <button class="nav-tab" onclick="switchTab('senders')">Email Senders</button>
        <button class="nav-tab" onclick="switchTab('recipients')">Recipients</button>
        <button class="nav-tab" onclick="switchTab('audit')">Notification Logs</button>
    </div>

    <div id="statusAlert"></div>

    <!-- Tab 1: Applications -->
    <div id="tab-apps" class="section-panel active">
        <div class="action-row">
            <h3>Registered Client Applications</h3>
            <button class="btn" onclick="openCreateAppModal()">+ Register Application</button>
        </div>
        <div class="table-wrap">
            <table>
                <thead>
                    <tr>
                        <th>Name</th>
                        <th>Application ID</th>
                        <th>Status</th>
                        <th>Authorised Senders</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody id="appsTableBody">
                    <tr><td colspan="5" style="text-align: center; color: var(--text-muted);">Loading applications...</td></tr>
                </tbody>
            </table>
        </div>
    </div>

    <!-- Tab 2: Email Senders -->
    <div id="tab-senders" class="section-panel">
        <div class="action-row">
            <h3>Configured SMTP Email Senders</h3>
            <button class="btn" onclick="openCreateSenderModal()">+ Add Email Sender</button>
        </div>
        <div class="table-wrap">
            <table>
                <thead>
                    <tr>
                        <th>Name</th>
                        <th>From Address</th>
                        <th>Host / Port</th>
                        <th>Security</th>
                        <th>Credentials</th>
                        <th>Status</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody id="sendersTableBody">
                    <tr><td colspan="7" style="text-align: center; color: var(--text-muted);">Loading email senders...</td></tr>
                </tbody>
            </table>
        </div>
    </div>

    <!-- Tab 3: Recipients -->
    <div id="tab-recipients" class="section-panel">
        <div class="action-row">
            <h3>Tokenised Recipients Directory</h3>
            <input type="text" id="recipientSearchQuery" class="form-control" style="width: 320px;" placeholder="Search email or name..." onkeyup="debounceRecipientSearch()">
        </div>
        <div class="table-wrap">
            <table>
                <thead>
                    <tr>
                        <th>Recipient ID</th>
                        <th>Sanitized Email</th>
                        <th>Name</th>
                        <th>Created By App</th>
                        <th>Created At</th>
                    </tr>
                </thead>
                <tbody id="recipientsTableBody">
                    <tr><td colspan="5" style="text-align: center; color: var(--text-muted);">Loading recipients...</td></tr>
                </tbody>
            </table>
        </div>
    </div>

    <!-- Tab 4: Audit Logs -->
    <div id="tab-audit" class="section-panel">
        <div class="action-row">
            <h3>Notification Dispatch History</h3>
            <div>
                <select id="auditStatusFilter" class="form-control" style="width: 180px;" onchange="loadAuditLogs()">
                    <option value="">All Statuses</option>
                    <option value="PENDING">PENDING</option>
                    <option value="SENT">SENT</option>
                    <option value="FAILED">FAILED</option>
                </select>
            </div>
        </div>
        <div class="table-wrap">
            <table>
                <thead>
                    <tr>
                        <th>Notification ID</th>
                        <th>App</th>
                        <th>Type / Level</th>
                        <th>Status</th>
                        <th>Error Details</th>
                        <th>Created At</th>
                    </tr>
                </thead>
                <tbody id="auditTableBody">
                    <tr><td colspan="6" style="text-align: center; color: var(--text-muted);">Loading audit logs...</td></tr>
                </tbody>
            </table>
        </div>
    </div>

</div>

<!-- Modal: Create Application -->
<div id="createAppModal" class="modal-overlay">
    <div class="modal">
        <div class="modal-title">Register Application</div>
        <div class="form-group">
            <label for="appName">Application Name (e.g. eGarage, Veylor SSO)</label>
            <input type="text" id="appName" class="form-control" placeholder="Application Name">
        </div>
        <div class="form-group">
            <label for="createAppSender">Assigned Email Sender</label>
            <select id="createAppSender" class="form-control">
                <option value="">⚡ Default Relay SMTP (relay@veylor.com)</option>
            </select>
        </div>
        <div class="modal-actions">
            <button class="btn btn-secondary" onclick="closeModal('createAppModal')">Cancel</button>
            <button class="btn" onclick="submitCreateApp()">Create Application</button>
        </div>
    </div>
</div>

<!-- Modal: Created Key Notification -->
<div id="keyCreatedModal" class="modal-overlay">
    <div class="modal">
        <div class="modal-title">Application Credentials Created</div>
        <p style="font-size: 0.85rem; color: var(--text-muted); margin-bottom: 1rem;">
            Copy this API key now. For security, it cannot be displayed again in plaintext.
        </p>
        <div class="form-group">
            <label>API Key</label>
            <input type="text" id="createdApiKey" class="form-control code-box" readonly>
        </div>
        <div class="modal-actions">
            <button class="btn" onclick="closeModal('keyCreatedModal')">I Have Saved the Key</button>
        </div>
    </div>
</div>

<!-- Modal: Create Sender -->
<div id="createSenderModal" class="modal-overlay">
    <div class="modal">
        <div class="modal-title">Add SMTP Email Sender</div>
        <div class="form-group">
            <label>Sender Identifier Name</label>
            <input type="text" id="sName" class="form-control" placeholder="e.g. Veylor Corporate">
        </div>
        <div class="form-group">
            <label>From Address</label>
            <input type="email" id="sFrom" class="form-control" placeholder="no-reply@veylor.dev">
        </div>
        <div style="display: flex; gap: 1rem;">
            <div class="form-group" style="flex: 2;">
                <label>SMTP Host</label>
                <input type="text" id="sHost" class="form-control" placeholder="smtp.example.com">
            </div>
            <div class="form-group" style="flex: 1;">
                <label>Port</label>
                <input type="number" id="sPort" class="form-control" value="587">
            </div>
        </div>
        <div class="form-group">
            <label>Username</label>
            <input type="text" id="sUser" class="form-control" placeholder="smtp_user">
        </div>
        <div class="form-group">
            <label>Password</label>
            <input type="password" id="sPass" class="form-control" placeholder="Encrypted at rest with AES-GCM">
        </div>
        <div class="modal-actions">
            <button class="btn btn-secondary" onclick="closeModal('createSenderModal')">Cancel</button>
            <button class="btn" onclick="submitCreateSender()">Save Email Sender</button>
    </div>
</div>

</div> <!-- /appDashboard -->

<script>
    let currentToken = '';

    function getHeaders() {
        return {
            'Content-Type': 'application/json',
            'X-Admin-Token': currentToken
        };
    }

    async function handleLogin(e) {
        if (e) e.preventDefault();
        const tokenInput = document.getElementById('adminTokenInput');
        const token = tokenInput.value.trim();
        const errorEl = document.getElementById('loginError');
        const btn = document.getElementById('loginBtn');

        if (!token) return;

        errorEl.style.display = 'none';
        btn.disabled = true;
        btn.innerText = 'Verifying...';

        try {
            const res = await fetch('/api/v1/admin/summary', {
                headers: {
                    'Content-Type': 'application/json',
                    'X-Admin-Token': token
                }
            });

            if (res.ok) {
                currentToken = token;
                sessionStorage.setItem('relay_admin_token', token);
                tokenInput.value = '';
                document.getElementById('loginGate').style.display = 'none';
                document.getElementById('appDashboard').style.display = 'block';
                loadAll();
            } else {
                errorEl.innerText = 'Invalid Admin Secret Token (HTTP ' + res.status + ')';
                errorEl.style.display = 'block';
            }
        } catch (err) {
            errorEl.innerText = 'Connection error: Unable to reach Relay server.';
            errorEl.style.display = 'block';
        } finally {
            btn.disabled = false;
            btn.innerText = 'Access Console';
        }
    }

    function logoutAdmin() {
        currentToken = '';
        sessionStorage.removeItem('relay_admin_token');
        localStorage.removeItem('relay_admin_token');
        document.getElementById('appDashboard').style.display = 'none';
        document.getElementById('loginGate').style.display = 'flex';
        document.getElementById('adminTokenInput').value = '';
        document.getElementById('loginError').style.display = 'none';
        setTimeout(() => {
            const el = document.getElementById('adminTokenInput');
            if (el) el.focus();
        }, 50);
    }

    async function checkSavedAuth() {
        const saved = sessionStorage.getItem('relay_admin_token') || localStorage.getItem('relay_admin_token');
        if (!saved) {
            logoutAdmin();
            return;
        }

        try {
            const res = await fetch('/api/v1/admin/summary', {
                headers: {
                    'Content-Type': 'application/json',
                    'X-Admin-Token': saved
                }
            });
            if (res.ok) {
                currentToken = saved;
                document.getElementById('loginGate').style.display = 'none';
                document.getElementById('appDashboard').style.display = 'block';
                loadAll();
            } else {
                logoutAdmin();
            }
        } catch (e) {
            logoutAdmin();
        }
    }

    function switchTab(tabId) {
        document.querySelectorAll('.nav-tab').forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.section-panel').forEach(p => p.classList.remove('active'));
        
        event.target.classList.add('active');
        document.getElementById('tab-' + tabId).classList.add('active');
    }

    let allSenders = [];

    function openModal(id) { document.getElementById(id).classList.add('active'); }
    function closeModal(id) { document.getElementById(id).classList.remove('active'); }
    function openCreateAppModal() { 
        document.getElementById('appName').value = ''; 
        const sel = document.getElementById('createAppSender');
        if (sel) {
            sel.innerHTML = '<option value="">⚡ Default Relay SMTP (relay@veylor.com)</option>' +
                allSenders.map(s => `<option value="${s.id}">${escapeHtml(s.name)} (${escapeHtml(s.fromAddress)})</option>`).join('');
        }
        openModal('createAppModal'); 
    }
    function openCreateSenderModal() { openModal('createSenderModal'); }

    async function loadSummary() {
        try {
            const res = await fetch('/api/v1/admin/summary', { headers: getHeaders() });
            if (!res.ok) return;
            const data = await res.json();
            document.getElementById('m-apps').innerText = data.totalApplications;
            document.getElementById('m-senders').innerText = data.totalSenders;
            document.getElementById('m-outbox').innerText = data.queuedOutboxJobs;
            document.getElementById('m-sent').innerText = data.sentNotifications;
            document.getElementById('m-failed').innerText = data.failedNotifications;
        } catch (e) {
            console.error(e);
        }
    }

    async function loadApplications() {
        try {
            const res = await fetch('/api/v1/admin/applications', { headers: getHeaders() });
            if (!res.ok) {
                document.getElementById('appsTableBody').innerHTML = `<tr><td colspan="5" style="color: var(--accent-danger);">Auth error: Invalid admin token.</td></tr>`;
                return;
            }
            const apps = await res.json();
            if (apps.length === 0) {
                document.getElementById('appsTableBody').innerHTML = `<tr><td colspan="5" style="text-align:center; color: var(--text-muted);">No applications registered yet.</td></tr>`;
                return;
            }
            document.getElementById('appsTableBody').innerHTML = apps.map(app => `
                <tr>
                    <td><strong>${escapeHtml(app.name)}</strong></td>
                    <td><span class="code-box">${app.id}</span></td>
                    <td><span class="tag ${app.enabled ? 'tag-active' : 'tag-inactive'}">${app.enabled ? 'ENABLED' : 'DISABLED'}</span></td>
                    <td>
                        <select class="form-control" style="font-size: 0.78rem; padding: 0.25rem 0.5rem; width: auto; max-width: 250px;" onchange="changeAppSender('${app.id}', this.value)">
                            <option value="" ${!app.primarySenderId ? 'selected' : ''}>⚡ Default Relay SMTP</option>
                            ${allSenders.map(s => `
                                <option value="${s.id}" ${app.primarySenderId === s.id ? 'selected' : ''}>
                                    ${escapeHtml(s.name)} (${escapeHtml(s.fromAddress)})
                                </option>
                            `).join('')}
                        </select>
                    </td>
                    <td>
                        <button class="btn btn-secondary btn-sm" onclick="rotateKey('${app.id}')">Rotate Key</button>
                        ${app.enabled 
                            ? `<button class="btn btn-secondary btn-sm" onclick="toggleApp('${app.id}', false)">Disable</button>` 
                            : `<button class="btn btn-secondary btn-sm" onclick="toggleApp('${app.id}', true)">Enable</button>`}
                    </td>
                </tr>
            `).join('');
        } catch (e) {
            console.error(e);
        }
    }

    async function changeAppSender(appId, senderId) {
        try {
            const res = await fetch(`/api/v1/admin/applications/${appId}/sender`, {
                method: 'PUT',
                headers: getHeaders(),
                body: JSON.stringify({ senderId: senderId || null })
            });
            if (res.ok) {
                loadApplications();
            } else {
                alert('Failed to update application sender');
            }
        } catch (e) {
            alert('Network error updating sender');
        }
    }

    async function submitCreateApp() {
        const name = document.getElementById('appName').value.trim();
        const senderId = document.getElementById('createAppSender')?.value.trim() || null;
        if (!name) return;
        try {
            const res = await fetch('/api/v1/admin/applications', {
                method: 'POST',
                headers: getHeaders(),
                body: JSON.stringify({ name, senderId })
            });
            if (res.ok) {
                const data = await res.json();
                closeModal('createAppModal');
                document.getElementById('createdApiKey').value = data.apiKey;
                openModal('keyCreatedModal');
                loadAll();
            }
        } catch (e) {
            alert('Error creating application');
        }
    }

    async function rotateKey(id) {
        if (!confirm('Are you sure you want to rotate the API key for this application? The previous key will stop working immediately.')) return;
        try {
            const res = await fetch(`/api/v1/admin/applications/${id}/rotate-credentials`, {
                method: 'POST',
                headers: getHeaders()
            });
            if (res.ok) {
                const data = await res.json();
                document.getElementById('createdApiKey').value = data.newApiKey;
                openModal('keyCreatedModal');
                loadAll();
            }
        } catch (e) {
            alert('Error rotating key');
        }
    }

    async function toggleApp(id, enable) {
        const action = enable ? 'enable' : 'disable';
        await fetch(`/api/v1/admin/applications/${id}/${action}`, { method: 'POST', headers: getHeaders() });
        loadAll();
    }

    async function loadSenders() {
        try {
            const res = await fetch('/api/v1/admin/senders', { headers: getHeaders() });
            if (!res.ok) return;
            const senders = await res.json();
            allSenders = senders || [];
            if (allSenders.length === 0) {
                document.getElementById('sendersTableBody').innerHTML = `<tr><td colspan="7" style="text-align:center; color: var(--text-muted);">No email senders configured.</td></tr>`;
                return;
            }
            document.getElementById('sendersTableBody').innerHTML = allSenders.map(s => `
                <tr>
                    <td><strong>${escapeHtml(s.name)}</strong></td>
                    <td><span class="code-box">${escapeHtml(s.fromAddress)}</span></td>
                    <td>${escapeHtml(s.smtpHost)}:${s.smtpPort}</td>
                    <td><span class="tag tag-active">TLS Required</span></td>
                    <td>${s.hasPassword ? '<span class="tag tag-active">Encrypted (AES-GCM)</span>' : '<span class="tag tag-inactive">None</span>'}</td>
                    <td><span class="tag ${s.enabled ? 'tag-active' : 'tag-inactive'}">${s.enabled ? 'ENABLED' : 'DISABLED'}</span></td>
                    <td>
                        ${s.enabled 
                            ? `<button class="btn btn-secondary btn-sm" onclick="toggleSender('${s.id}', false)">Disable</button>` 
                            : `<button class="btn btn-secondary btn-sm" onclick="toggleSender('${s.id}', true)">Enable</button>`}
                    </td>
                </tr>
            `).join('');
        } catch (e) {
            console.error(e);
        }
    }

    async function submitCreateSender() {
        const payload = {
            name: document.getElementById('sName').value.trim(),
            fromAddress: document.getElementById('sFrom').value.trim(),
            smtpHost: document.getElementById('sHost').value.trim(),
            smtpPort: parseInt(document.getElementById('sPort').value) || 587,
            username: document.getElementById('sUser').value.trim(),
            password: document.getElementById('sPass').value.trim(),
            authEnabled: true,
            starttlsEnabled: true,
            starttlsRequired: true,
            enabled: true
        };
        try {
            const res = await fetch('/api/v1/admin/senders', {
                method: 'POST',
                headers: getHeaders(),
                body: JSON.stringify(payload)
            });
            if (res.ok) {
                closeModal('createSenderModal');
                loadAll();
            } else {
                alert('Failed to save sender');
            }
        } catch (e) {
            alert('Error creating sender');
        }
    }

    async function toggleSender(id, enable) {
        const action = enable ? 'enable' : 'disable';
        await fetch(`/api/v1/admin/senders/${id}/${action}`, { method: 'POST', headers: getHeaders() });
        loadAll();
    }

    let searchTimeout = null;
    function debounceRecipientSearch() {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(loadRecipients, 250);
    }

    async function loadRecipients() {
        const q = document.getElementById('recipientSearchQuery').value.trim();
        const url = `/api/v1/admin/recipients?size=50${q ? '&query=' + encodeURIComponent(q) : ''}`;
        try {
            const res = await fetch(url, { headers: getHeaders() });
            if (!res.ok) return;
            const data = await res.json();
            const recipients = data.content || [];
            if (recipients.length === 0) {
                document.getElementById('recipientsTableBody').innerHTML = `<tr><td colspan="5" style="text-align:center; color: var(--text-muted);">No recipients found.</td></tr>`;
                return;
            }
            document.getElementById('recipientsTableBody').innerHTML = recipients.map(r => `
                <tr>
                    <td><span class="code-box">${r.id}</span></td>
                    <td><strong>${escapeHtml(r.sanitizedEmail)}</strong></td>
                    <td>${escapeHtml(r.name || '—')}</td>
                    <td><span class="code-box">${r.createdByAppId || '—'}</span></td>
                    <td>${new Date(r.createdAt).toLocaleString()}</td>
                </tr>
            `).join('');
        } catch (e) {
            console.error(e);
        }
    }

    async function loadAuditLogs() {
        const status = document.getElementById('auditStatusFilter').value;
        const url = `/api/v1/admin/notifications?size=50${status ? '&status=' + status : ''}`;
        try {
            const res = await fetch(url, { headers: getHeaders() });
            if (!res.ok) return;
            const data = await res.json();
            const logs = data.content || [];
            if (logs.length === 0) {
                document.getElementById('auditTableBody').innerHTML = `<tr><td colspan="6" style="text-align:center; color: var(--text-muted);">No notification logs recorded.</td></tr>`;
                return;
            }
            document.getElementById('auditTableBody').innerHTML = logs.map(l => `
                <tr>
                    <td><span class="code-box">${l.id}</span></td>
                    <td>${escapeHtml(l.applicationName || l.applicationId || '—')}</td>
                    <td><span class="code-box">${l.type}</span> / ${l.level}</td>
                    <td><span class="tag ${l.status === 'SENT' ? 'tag-active' : (l.status === 'FAILED' ? 'tag-inactive' : 'tag-pending')}">${l.status}</span></td>
                    <td style="max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                        ${escapeHtml(l.errorDetails || '—')}
                    </td>
                    <td>${new Date(l.createdAt).toLocaleString()}</td>
                </tr>
            `).join('');
        } catch (e) {
            console.error(e);
        }
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#032;");
    }

    async function loadAll() {
        loadSummary();
        await loadSenders();
        loadApplications();
        loadRecipients();
        loadAuditLogs();
    }

    window.onload = checkSavedAuth;
</script>

</body>
</html>
""";
    }
}
