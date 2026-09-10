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
    <title>Veylor Relay // Admin Command Portal</title>
    <!-- IBM Plex Sans & IBM Plex Mono (Veylor Design System) -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500;600&family=IBM+Plex+Sans:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <!-- Tailwind CSS CDN -->
    <script src="https://cdn.tailwindcss.com"></script>
    <script>
      tailwind.config = {
        theme: {
          extend: {
            fontFamily: {
              sans: ['"IBM Plex Sans"', "system-ui", "-apple-system", "sans-serif"],
              mono: ['"IBM Plex Mono"', "monospace"],
            },
            colors: {
              veylor: {
                bg: "#111111",
                card: "#161616",
                cardAlt: "#1a1a1a",
                border: "#333333",
                borderLight: "#444444",
                foreground: "#fafafa",
                muted: "#a1a1aa",
                subtle: "#71717a",
                white: "#ffffff",
              },
            },
          },
        },
      };
    </script>
    <style>
      *,
      *::before,
      *::after {
        border-radius: 0px !important; /* Strict defense-grade: zero rounded corners */
      }
      body {
        font-family: "IBM Plex Sans", system-ui, -apple-system, sans-serif;
        background-color: #111111;
        color: #fafafa;
        -webkit-font-smoothing: antialiased;
        -moz-osx-font-smoothing: grayscale;
        min-height: 100vh;
      }
      .font-mono {
        font-family: "IBM Plex Mono", monospace;
      }
      ::-webkit-scrollbar {
        width: 6px;
        height: 6px;
      }
      ::-webkit-scrollbar-track {
        background: #111111;
      }
      ::-webkit-scrollbar-thumb {
        background: #333333;
      }
      ::-webkit-scrollbar-thumb:hover {
        background: #555555;
      }
      ::selection {
        background: #fafafa;
        color: #111111;
      }
      input[type="text"],
      input[type="email"],
      input[type="password"],
      input[type="number"],
      select,
      textarea {
        background-color: #111111;
        border: 1px solid #333333;
        color: #fafafa;
        font-family: "IBM Plex Mono", monospace;
        transition: border-color 0.15s ease;
      }
      input[type="text"]:focus,
      input[type="email"]:focus,
      input[type="password"]:focus,
      input[type="number"]:focus,
      select:focus,
      textarea:focus {
        border-color: #fafafa;
        outline: none;
        box-shadow: none;
      }
      .btn-veylor-primary {
        background-color: #fafafa;
        color: #111111;
        border: 1px solid #fafafa;
        font-family: "IBM Plex Sans", sans-serif;
        font-size: 0.8125rem;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.1em;
        transition: all 0.15s ease;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
      }
      .btn-veylor-primary:hover:not(:disabled) {
        background-color: #e5e5e5;
        border-color: #e5e5e5;
      }
      .btn-veylor-secondary {
        background-color: transparent;
        color: #fafafa;
        border: 1px solid #333333;
        font-family: "IBM Plex Sans", sans-serif;
        font-size: 0.8125rem;
        font-weight: 500;
        text-transform: uppercase;
        letter-spacing: 0.1em;
        transition: all 0.15s ease;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
      }
      .btn-veylor-secondary:hover {
        background-color: #1a1a1a;
        border-color: #666666;
      }
      .btn-veylor-danger {
        background-color: transparent;
        color: #ef4444;
        border: 1px solid #552222;
        font-family: "IBM Plex Sans", sans-serif;
        font-size: 0.75rem;
        font-weight: 500;
        text-transform: uppercase;
        letter-spacing: 0.08em;
        transition: all 0.15s ease;
        cursor: pointer;
      }
      .btn-veylor-danger:hover {
        background-color: #271313;
        border-color: #991b1b;
      }
      .tab-active {
        border-bottom-color: #fafafa !important;
        color: #fafafa !important;
        background-color: #161616 !important;
      }
      .tab-inactive {
        border-bottom-color: transparent !important;
        color: #71717a !important;
      }
      .tab-inactive:hover {
        color: #fafafa !important;
        background-color: #141414 !important;
      }
      .section-panel { display: none; }
      .section-panel.active { display: block; }
      .modal-backdrop {
        position: fixed;
        inset: 0;
        background-color: rgba(0, 0, 0, 0.8);
        display: none;
        align-items: center;
        justify-content: center;
        z-index: 100;
        padding: 1rem;
      }
      .modal-backdrop.active {
        display: flex;
      }
    </style>
</head>
<body class="min-h-screen flex flex-col justify-between bg-[#111111] text-[#fafafa]">

<!-- ================= AUTHENTICATION / LOGIN GATE ================= -->
<div id="loginGate" class="min-h-screen flex items-center justify-center p-4 sm:p-8">
    <div class="w-full max-w-md bg-[#161616] border border-[#333333] p-8 space-y-6">
        <div class="text-center space-y-2">
            <div class="inline-flex items-center gap-2 border border-[#333333] bg-[#1a1a1a] px-3 py-1 text-[11px] font-mono text-[#a1a1aa] uppercase tracking-widest">
                <span class="w-1.5 h-1.5 bg-amber-400"></span>
                Elevated Access Required
            </div>
            <h1 class="text-xl font-bold tracking-tight text-[#fafafa] uppercase">Admin Command Portal</h1>
            <p class="text-xs font-mono text-[#71717a]">Enter administrative secret token to access Veylor Relay console</p>
        </div>

        <div id="loginError" class="p-3 bg-[#1e1313] border border-[#7f1d1d] text-xs font-mono text-[#fca5a5]" style="display: none;"></div>

        <form onsubmit="handleLogin(event)" class="space-y-4">
            <div>
                <label for="adminTokenInput" class="block text-xs font-mono text-[#a1a1aa] uppercase tracking-wider mb-2">
                    Administrative Passkey (X-Admin-Token)
                </label>
                <input type="password" id="adminTokenInput" required autofocus placeholder="Enter administrative token..."
                       autocomplete="off"
                       class="w-full px-3 py-2 text-sm bg-[#111111] border border-[#333333] text-[#fafafa] font-mono focus:border-[#fafafa]">
            </div>
            <button type="submit" id="loginBtn" class="w-full btn-veylor-primary py-3 text-xs tracking-widest">
                Unlock Admin Console
            </button>
        </form>

        <div class="border-t border-[#2a2a2a] pt-4 text-center">
            <p class="text-[11px] text-[#71717a] font-mono uppercase tracking-wider">
                Veylor Ecosystem &bull; High-Assurance Dispatch
            </p>
        </div>
    </div>
</div>

<!-- ================= AUTHENTICATED DASHBOARD ================= -->
<div id="appDashboard" style="display: none;" class="w-full flex-grow flex flex-col">

    <!-- Top Defense Header -->
    <header class="border-b border-[#333333] bg-[#111111] px-6 py-3.5 sticky top-0 z-50">
        <div class="max-w-7xl mx-auto flex items-center justify-between">
            <div class="flex items-center space-x-3">
                <div class="h-7 w-7 border border-[#fafafa] bg-[#fafafa] flex items-center justify-center text-[#111111] font-bold text-xs tracking-tighter">
                    V
                </div>
                <div class="flex items-center space-x-2">
                    <span class="text-sm font-semibold tracking-wider uppercase text-[#fafafa]">Veylor</span>
                    <span class="text-[10px] font-mono uppercase px-1.5 py-0.5 border border-[#444444] text-[#a1a1aa] bg-[#1a1a1a] tracking-widest">RELAY</span>
                </div>
            </div>
            <div class="flex items-center space-x-4 text-xs font-mono text-[#71717a] uppercase tracking-widest">
                <span class="inline-flex items-center">
                    <span class="h-1.5 w-1.5 bg-emerald-400 mr-2"></span>
                    <span class="hidden sm:inline">Transactional Dispatch Mesh</span>
                </span>
                <span class="text-[#333333] hidden sm:inline">|</span>
                <button onclick="logoutAdmin()" class="btn-veylor-danger px-3 py-1 text-[10px]">
                    Lock Console
                </button>
            </div>
        </div>
    </header>

    <!-- Main Content Container -->
    <main class="w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6 flex-grow">

        <!-- Admin Header Banner -->
        <div class="border border-[#333333] bg-[#161616] p-4 flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div class="flex items-center gap-3">
                <div class="w-8 h-8 bg-[#fafafa] text-[#111111] font-mono font-black text-sm flex items-center justify-center">
                    R
                </div>
                <div>
                    <div class="flex items-center gap-2">
                        <h1 class="text-sm font-bold tracking-widest text-[#fafafa] uppercase">Veylor Relay // Admin Portal</h1>
                        <span class="text-[10px] font-mono bg-[#111111] border border-emerald-500/50 text-emerald-400 px-1.5 py-0.2">
                            AUTHORIZED
                        </span>
                    </div>
                    <p class="text-[11px] font-mono text-[#71717a] mt-0.5">High-Assurance Notification Mesh & Outbox Dispatch Control Plane</p>
                </div>
            </div>

            <div class="flex items-center gap-3 text-xs font-mono">
                <div class="bg-[#111111] border border-[#2a2a2a] px-3 py-1.5 text-emerald-400 flex items-center gap-2">
                    <span class="w-1.5 h-1.5 bg-emerald-400"></span>
                    <span>ADMIN_TOKEN_AUTH</span>
                </div>
                <button onclick="logoutAdmin()" class="btn-veylor-danger px-3 py-1.5 text-[11px]">Lock Console</button>
            </div>
        </div>

        <!-- Telemetry & Metrics Grid -->
        <div class="grid grid-cols-2 md:grid-cols-5 gap-4">
            <div class="bg-[#161616] border border-[#333333] p-4">
                <div class="text-[11px] font-mono text-[#71717a] uppercase tracking-wider">Registered Apps</div>
                <div class="text-2xl font-bold font-mono text-[#fafafa] mt-2" id="m-apps">-</div>
                <div class="text-[11px] font-mono text-[#a1a1aa] mt-1">Authorized clients</div>
            </div>

            <div class="bg-[#161616] border border-[#333333] p-4">
                <div class="text-[11px] font-mono text-[#71717a] uppercase tracking-wider">Active Senders</div>
                <div class="text-2xl font-bold font-mono text-[#fafafa] mt-2" id="m-senders">-</div>
                <div class="text-[11px] font-mono text-[#a1a1aa] mt-1">Configured SMTP nodes</div>
            </div>

            <div class="bg-[#161616] border border-[#333333] p-4">
                <div class="text-[11px] font-mono text-[#71717a] uppercase tracking-wider">Outbox In-Flight</div>
                <div class="text-2xl font-bold font-mono text-amber-400 mt-2" id="m-outbox">-</div>
                <div class="text-[11px] font-mono text-amber-400/80 mt-1">Queued worker jobs</div>
            </div>

            <div class="bg-[#161616] border border-[#333333] p-4">
                <div class="text-[11px] font-mono text-[#71717a] uppercase tracking-wider">Delivered (Sent)</div>
                <div class="text-2xl font-bold font-mono text-emerald-400 mt-2" id="m-sent">-</div>
                <div class="text-[11px] font-mono text-emerald-400/80 mt-1">Confirmed dispatches</div>
            </div>

            <div class="bg-[#161616] border border-[#333333] p-4 col-span-2 md:col-span-1">
                <div class="text-[11px] font-mono text-[#71717a] uppercase tracking-wider">Failed Dispatches</div>
                <div class="text-2xl font-bold font-mono text-red-400 mt-2" id="m-failed">-</div>
                <div class="text-[11px] font-mono text-red-400/80 mt-1">Dead-letter / error state</div>
            </div>
        </div>

        <!-- Navigation Tabs -->
        <div class="border-b border-[#333333] flex flex-wrap gap-1 text-xs font-mono">
            <button class="px-4 py-2.5 uppercase tracking-wider border-b-2 transition flex items-center gap-2 tab-active"
                    id="tabBtn-apps" onclick="switchTab('apps')">
                <span>[01]</span> Registered Applications
            </button>
            <button class="px-4 py-2.5 uppercase tracking-wider border-b-2 transition flex items-center gap-2 tab-inactive"
                    id="tabBtn-senders" onclick="switchTab('senders')">
                <span>[02]</span> Email Senders (SMTP)
            </button>
            <button class="px-4 py-2.5 uppercase tracking-wider border-b-2 transition flex items-center gap-2 tab-inactive"
                    id="tabBtn-recipients" onclick="switchTab('recipients')">
                <span>[03]</span> Tokenised Recipients
            </button>
            <button class="px-4 py-2.5 uppercase tracking-wider border-b-2 transition flex items-center gap-2 tab-inactive"
                    id="tabBtn-audit" onclick="switchTab('audit')">
                <span>[04]</span> Notification Dispatch Logs
            </button>
        </div>

        <div id="statusAlert"></div>

        <!-- ================= TAB 1: APPLICATIONS ================= -->
        <div id="tab-apps" class="section-panel active space-y-4">
            <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-[#333333] pb-4">
                <div>
                    <h2 class="text-sm font-bold font-mono text-[#fafafa] uppercase tracking-wider">Registered Client Applications</h2>
                    <p class="text-xs font-mono text-[#71717a] mt-0.5">Manage microservices and systems authorized to dispatch notifications</p>
                </div>
                <button onclick="openCreateAppModal()" class="btn-veylor-primary px-4 py-2 text-xs tracking-wider">
                    + Register Application
                </button>
            </div>

            <div class="bg-[#161616] border border-[#333333]">
                <div class="overflow-x-auto">
                    <table class="w-full text-left font-mono text-xs">
                        <thead>
                            <tr class="border-b border-[#2a2a2a] text-[#71717a] text-[10px] uppercase tracking-wider bg-[#111111]">
                                <th class="py-3 px-4">Application</th>
                                <th class="py-3 px-4">Application ID</th>
                                <th class="py-3 px-4">Status</th>
                                <th class="py-3 px-4">Authorised Sender</th>
                                <th class="py-3 px-4 text-right">Actions</th>
                            </tr>
                        </thead>
                        <tbody id="appsTableBody" class="divide-y divide-[#222222]">
                            <tr><td colspan="5" class="py-8 text-center text-[#71717a]">Loading applications...</td></tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>

        <!-- ================= TAB 2: EMAIL SENDERS ================= -->
        <div id="tab-senders" class="section-panel space-y-4">
            <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-[#333333] pb-4">
                <div>
                    <h2 class="text-sm font-bold font-mono text-[#fafafa] uppercase tracking-wider">Configured SMTP Email Senders</h2>
                    <p class="text-xs font-mono text-[#71717a] mt-0.5">Physical SMTP transport nodes and outbound routing definitions</p>
                </div>
                <button onclick="openCreateSenderModal()" class="btn-veylor-primary px-4 py-2 text-xs tracking-wider">
                    + Add Email Sender
                </button>
            </div>

            <div class="bg-[#161616] border border-[#333333]">
                <div class="overflow-x-auto">
                    <table class="w-full text-left font-mono text-xs">
                        <thead>
                            <tr class="border-b border-[#2a2a2a] text-[#71717a] text-[10px] uppercase tracking-wider bg-[#111111]">
                                <th class="py-3 px-4">Sender Name</th>
                                <th class="py-3 px-4">From Address</th>
                                <th class="py-3 px-4">Host / Port</th>
                                <th class="py-3 px-4">Security Protocol</th>
                                <th class="py-3 px-4">Credentials</th>
                                <th class="py-3 px-4">Status</th>
                                <th class="py-3 px-4 text-right">Actions</th>
                            </tr>
                        </thead>
                        <tbody id="sendersTableBody" class="divide-y divide-[#222222]">
                            <tr><td colspan="7" class="py-8 text-center text-[#71717a]">Loading email senders...</td></tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>

        <!-- ================= TAB 3: RECIPIENTS ================= -->
        <div id="tab-recipients" class="section-panel space-y-4">
            <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-[#333333] pb-4">
                <div>
                    <h2 class="text-sm font-bold font-mono text-[#fafafa] uppercase tracking-wider">Tokenised Recipients Directory</h2>
                    <p class="text-xs font-mono text-[#71717a] mt-0.5">Central recipient identities sanitized and partitioned across applications</p>
                </div>
                <div class="w-full sm:w-80">
                    <input type="text" id="recipientSearchQuery" class="w-full px-3 py-2 text-xs bg-[#111111] border border-[#333333] text-[#fafafa] placeholder-[#555555]"
                           placeholder="Search email or name..." onkeyup="debounceRecipientSearch()">
                </div>
            </div>

            <div class="bg-[#161616] border border-[#333333]">
                <div class="overflow-x-auto">
                    <table class="w-full text-left font-mono text-xs">
                        <thead>
                            <tr class="border-b border-[#2a2a2a] text-[#71717a] text-[10px] uppercase tracking-wider bg-[#111111]">
                                <th class="py-3 px-4">Recipient ID</th>
                                <th class="py-3 px-4">Sanitized Email</th>
                                <th class="py-3 px-4">Name</th>
                                <th class="py-3 px-4">Originating App</th>
                                <th class="py-3 px-4 text-right">Created At</th>
                            </tr>
                        </thead>
                        <tbody id="recipientsTableBody" class="divide-y divide-[#222222]">
                            <tr><td colspan="5" class="py-8 text-center text-[#71717a]">Loading recipients...</td></tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>

        <!-- ================= TAB 4: AUDIT LOGS ================= -->
        <div id="tab-audit" class="section-panel space-y-4">
            <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-[#333333] pb-4">
                <div>
                    <h2 class="text-sm font-bold font-mono text-[#fafafa] uppercase tracking-wider">Notification Dispatch History</h2>
                    <p class="text-xs font-mono text-[#71717a] mt-0.5">Real-time immutable outbox event and delivery telemetry</p>
                </div>
                <div>
                    <select id="auditStatusFilter" class="px-3 py-2 text-xs bg-[#111111] border border-[#333333] text-[#fafafa]" onchange="loadAuditLogs()">
                        <option value="">ALL STATUSES</option>
                        <option value="PENDING">PENDING (QUEUED)</option>
                        <option value="SENT">SENT (CONFIRMED)</option>
                        <option value="FAILED">FAILED (DEAD-LETTER)</option>
                    </select>
                </div>
            </div>

            <div class="bg-[#161616] border border-[#333333]">
                <div class="overflow-x-auto">
                    <table class="w-full text-left font-mono text-xs">
                        <thead>
                            <tr class="border-b border-[#2a2a2a] text-[#71717a] text-[10px] uppercase tracking-wider bg-[#111111]">
                                <th class="py-3 px-4">Notification ID</th>
                                <th class="py-3 px-4">App</th>
                                <th class="py-3 px-4">Type / Level</th>
                                <th class="py-3 px-4">Status</th>
                                <th class="py-3 px-4">Diagnostics / Error</th>
                                <th class="py-3 px-4 text-right">Created At</th>
                            </tr>
                        </thead>
                        <tbody id="auditTableBody" class="divide-y divide-[#222222]">
                            <tr><td colspan="6" class="py-8 text-center text-[#71717a]">Loading audit logs...</td></tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>

    </main>

    <!-- Tactical Defense Footer -->
    <footer class="border-t border-[#222222] py-4 px-6 bg-[#0d0d0d] mt-12">
        <div class="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between text-[11px] font-mono text-[#52525b] uppercase tracking-wider gap-2">
            <div>&copy; 2026 VEYLOR ECOSYSTEM &bull; DEFENSE-GRADE NOTIFICATION MESH</div>
            <div class="flex items-center space-x-4">
                <span>AES-GCM-256</span>
                <span>&bull;</span>
                <span>HMAC SHA-256</span>
                <span>&bull;</span>
                <span>ASYNC OUTBOX WORKER</span>
            </div>
        </div>
    </footer>

</div> <!-- /appDashboard -->


<!-- ================= MODALS ================= -->

<!-- Modal: Create Application -->
<div id="createAppModal" class="modal-backdrop">
    <div class="bg-[#161616] border border-[#333333] w-full max-w-lg p-6 space-y-4 font-mono text-xs">
        <div class="flex items-center justify-between border-b border-[#2a2a2a] pb-3">
            <h3 class="text-sm font-bold text-[#fafafa] uppercase tracking-wider">Register Application</h3>
            <button onclick="closeModal('createAppModal')" class="text-[#a1a1aa] hover:text-white text-base">&times;</button>
        </div>
        <div class="space-y-4">
            <div>
                <label for="appName" class="block text-[#a1a1aa] uppercase mb-1">Application Identifier Name</label>
                <input type="text" id="appName" placeholder="e.g. eGarage, Veylor SSO, Sentinel"
                       class="w-full px-3 py-2 bg-[#111111] border border-[#333333] text-[#fafafa]">
            </div>
            <div>
                <label for="createAppSender" class="block text-[#a1a1aa] uppercase mb-1">Assigned Email Sender Node</label>
                <select id="createAppSender" class="w-full px-3 py-2 bg-[#111111] border border-[#333333] text-[#fafafa]">
                    <option value="">Default Relay SMTP (relay@veylor.com)</option>
                </select>
            </div>
            <div class="flex justify-end gap-3 pt-3 border-t border-[#2a2a2a]">
                <button type="button" onclick="closeModal('createAppModal')" class="btn-veylor-secondary px-4 py-2">Cancel</button>
                <button type="button" onclick="submitCreateApp()" class="btn-veylor-primary px-4 py-2">Register Application</button>
            </div>
        </div>
    </div>
</div>

<!-- Modal: Created Key Notification -->
<div id="keyCreatedModal" class="modal-backdrop">
    <div class="bg-[#161616] border border-[#333333] w-full max-w-md p-6 space-y-4 font-mono text-xs">
        <div class="border-b border-[#2a2a2a] pb-3">
            <h3 class="text-sm font-bold text-amber-400 uppercase tracking-wider">Application Credentials Generated</h3>
            <p class="text-[11px] text-[#a1a1aa] mt-0.5">Copy this API key now. It is cryptographically hashed at rest and cannot be recovered.</p>
        </div>
        <div>
            <label class="block text-[#71717a] uppercase text-[10px] mb-1">HMAC API Key</label>
            <div class="flex gap-2">
                <input type="text" id="createdApiKey" readonly class="w-full px-3 py-2 bg-[#111111] border border-[#444444] text-emerald-400 font-bold">
                <button onclick="copyApiKey()" class="btn-veylor-secondary px-3 text-xs">Copy</button>
            </div>
        </div>
        <div class="flex justify-end pt-3 border-t border-[#2a2a2a]">
            <button onclick="closeModal('keyCreatedModal')" class="btn-veylor-primary px-4 py-2 text-xs">I Have Saved The Key</button>
        </div>
    </div>
</div>

<!-- Modal: Create Sender -->
<div id="createSenderModal" class="modal-backdrop">
    <div class="bg-[#161616] border border-[#333333] w-full max-w-lg p-6 space-y-4 font-mono text-xs">
        <div class="flex items-center justify-between border-b border-[#2a2a2a] pb-3">
            <h3 class="text-sm font-bold text-[#fafafa] uppercase tracking-wider">Add SMTP Email Sender Node</h3>
            <button onclick="closeModal('createSenderModal')" class="text-[#a1a1aa] hover:text-white text-base">&times;</button>
        </div>
        <div class="space-y-4">
            <div>
                <label class="block text-[#a1a1aa] uppercase mb-1">Sender Identifier Name</label>
                <input type="text" id="sName" placeholder="e.g. Veylor Corporate, Primary Postfix"
                       class="w-full px-3 py-2 bg-[#111111] border border-[#333333] text-[#fafafa]">
            </div>
            <div>
                <label class="block text-[#a1a1aa] uppercase mb-1">From Header Address</label>
                <input type="email" id="sFrom" placeholder="no-reply@veylor.dev"
                       class="w-full px-3 py-2 bg-[#111111] border border-[#333333] text-[#fafafa]">
            </div>
            <div class="grid grid-cols-3 gap-3">
                <div class="col-span-2">
                    <label class="block text-[#a1a1aa] uppercase mb-1">SMTP Host</label>
                    <input type="text" id="sHost" placeholder="smtp.example.com"
                           class="w-full px-3 py-2 bg-[#111111] border border-[#333333] text-[#fafafa]">
                </div>
                <div>
                    <label class="block text-[#a1a1aa] uppercase mb-1">Port</label>
                    <input type="number" id="sPort" value="587"
                           class="w-full px-3 py-2 bg-[#111111] border border-[#333333] text-[#fafafa]">
                </div>
            </div>
            <div>
                <label class="block text-[#a1a1aa] uppercase mb-1">SMTP Username</label>
                <input type="text" id="sUser" placeholder="smtp_user"
                       class="w-full px-3 py-2 bg-[#111111] border border-[#333333] text-[#fafafa]">
            </div>
            <div>
                <label class="block text-[#a1a1aa] uppercase mb-1">SMTP Password</label>
                <input type="password" id="sPass" placeholder="Encrypted at rest with AES-GCM"
                       class="w-full px-3 py-2 bg-[#111111] border border-[#333333] text-[#fafafa]">
            </div>
            <div class="flex justify-end gap-3 pt-3 border-t border-[#2a2a2a]">
                <button type="button" onclick="closeModal('createSenderModal')" class="btn-veylor-secondary px-4 py-2">Cancel</button>
                <button type="button" onclick="submitCreateSender()" class="btn-veylor-primary px-4 py-2">Save Email Sender</button>
            </div>
        </div>
    </div>
</div>


<!-- ================= APPLICATION SCRIPTS ================= -->
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
        btn.innerText = 'VERIFYING CREDENTIALS...';

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
                document.getElementById('appDashboard').style.display = 'flex';
                loadAll();
            } else {
                errorEl.innerText = '[ACCESS DENIED] Invalid Admin Secret Token (HTTP ' + res.status + ')';
                errorEl.style.display = 'block';
            }
        } catch (err) {
            errorEl.innerText = '[NETWORK ERROR] Unable to reach Veylor Relay backend.';
            errorEl.style.display = 'block';
        } finally {
            btn.disabled = false;
            btn.innerText = 'UNLOCK ADMIN CONSOLE';
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
                document.getElementById('appDashboard').style.display = 'flex';
                loadAll();
            } else {
                logoutAdmin();
            }
        } catch (e) {
            logoutAdmin();
        }
    }

    function switchTab(tabId) {
        ['apps', 'senders', 'recipients', 'audit'].forEach(id => {
            const btn = document.getElementById('tabBtn-' + id);
            const panel = document.getElementById('tab-' + id);
            if (btn) {
                if (id === tabId) {
                    btn.classList.add('tab-active');
                    btn.classList.remove('tab-inactive');
                } else {
                    btn.classList.remove('tab-active');
                    btn.classList.add('tab-inactive');
                }
            }
            if (panel) {
                if (id === tabId) panel.classList.add('active');
                else panel.classList.remove('active');
            }
        });
    }

    let allSenders = [];

    function openModal(id) {
        const el = document.getElementById(id);
        if (el) el.classList.add('active');
    }
    function closeModal(id) {
        const el = document.getElementById(id);
        if (el) el.classList.remove('active');
    }

    function openCreateAppModal() {
        document.getElementById('appName').value = '';
        const sel = document.getElementById('createAppSender');
        if (sel) {
            sel.innerHTML = '<option value="">Default Relay SMTP (relay@veylor.com)</option>' +
                allSenders.map(s => `<option value="${s.id}">${escapeHtml(s.name)} (${escapeHtml(s.fromAddress)})</option>`).join('');
        }
        openModal('createAppModal');
    }

    function openCreateSenderModal() {
        document.getElementById('sName').value = '';
        document.getElementById('sFrom').value = '';
        document.getElementById('sHost').value = '';
        document.getElementById('sPort').value = '587';
        document.getElementById('sUser').value = '';
        document.getElementById('sPass').value = '';
        openModal('createSenderModal');
    }

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
                document.getElementById('appsTableBody').innerHTML = `<tr><td colspan="5" class="py-8 text-center text-red-400">[AUTH ERROR] Invalid admin credentials.</td></tr>`;
                return;
            }
            const apps = await res.json();
            if (!apps || apps.length === 0) {
                document.getElementById('appsTableBody').innerHTML = `<tr><td colspan="5" class="py-8 text-center text-[#71717a]">No client applications registered.</td></tr>`;
                return;
            }
            document.getElementById('appsTableBody').innerHTML = apps.map(app => `
                <tr class="hover:bg-[#141414] transition">
                    <td class="py-3 px-4 font-bold text-[#fafafa]">${escapeHtml(app.name)}</td>
                    <td class="py-3 px-4"><span class="bg-[#111111] px-2 py-0.5 border border-[#2a2a2a] text-[#a1a1aa]">${app.id}</span></td>
                    <td class="py-3 px-4">
                        ${app.enabled
                            ? `<span class="border border-emerald-500/40 text-emerald-400 bg-[#111111] px-2 py-0.5 text-[10px] uppercase">Active</span>`
                            : `<span class="border border-red-500/40 text-red-400 bg-[#111111] px-2 py-0.5 text-[10px] uppercase">Disabled</span>`}
                    </td>
                    <td class="py-3 px-4">
                        <select class="px-2 py-1 text-xs bg-[#111111] border border-[#333333] text-[#fafafa] max-w-xs" onchange="changeAppSender('${app.id}', this.value)">
                            <option value="" ${!app.primarySenderId ? 'selected' : ''}>Default Relay SMTP</option>
                            ${allSenders.map(s => `
                                <option value="${s.id}" ${app.primarySenderId === s.id ? 'selected' : ''}>
                                    ${escapeHtml(s.name)} (${escapeHtml(s.fromAddress)})
                                </option>
                            `).join('')}
                        </select>
                    </td>
                    <td class="py-3 px-4 text-right space-x-2">
                        <button onclick="rotateKey('${app.id}')" class="text-[11px] text-amber-400 hover:underline bg-[#222222] px-2 py-1 border border-[#444444]">
                            Rotate Key
                        </button>
                        ${app.enabled
                            ? `<button onclick="toggleApp('${app.id}', false)" class="text-[11px] text-red-400 hover:underline bg-[#222222] px-2 py-1 border border-[#444444]">Disable</button>`
                            : `<button onclick="toggleApp('${app.id}', true)" class="text-[11px] text-emerald-400 hover:underline bg-[#222222] px-2 py-1 border border-[#444444]">Enable</button>`}
                        <button onclick="deleteApp('${app.id}', '${escapeHtml(app.name)}')" class="text-[11px] text-red-400 hover:underline bg-[#222222] px-2 py-1 border border-[#444444]">
                            Delete
                        </button>
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
                alert('Failed to update application sender routing.');
            }
        } catch (e) {
            alert('Network error updating application sender.');
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
            } else {
                alert('Failed to register application.');
            }
        } catch (e) {
            alert('Error creating application');
        }
    }

    async function rotateKey(id) {
        if (!confirm('Rotate API key for this application? The active key will become invalid immediately.')) return;
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
            } else {
                alert('Failed to rotate API credentials.');
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

    async function deleteApp(id, name) {
        if (!confirm(`Are you sure you want to PERMANENTLY delete application '${name}'?`)) return;
        try {
            const res = await fetch(`/api/v1/admin/applications/${id}`, {
                method: 'DELETE',
                headers: getHeaders()
            });
            if (res.ok) {
                loadAll();
            } else {
                alert('Failed to delete application.');
            }
        } catch (e) {
            alert('Network error deleting application.');
        }
    }

    async function loadSenders() {
        try {
            const res = await fetch('/api/v1/admin/senders', { headers: getHeaders() });
            if (!res.ok) return;
            const senders = await res.json();
            allSenders = senders || [];
            if (allSenders.length === 0) {
                document.getElementById('sendersTableBody').innerHTML = `<tr><td colspan="7" class="py-8 text-center text-[#71717a]">No SMTP senders configured.</td></tr>`;
                return;
            }
            document.getElementById('sendersTableBody').innerHTML = allSenders.map(s => `
                <tr class="hover:bg-[#141414] transition">
                    <td class="py-3 px-4 font-bold text-[#fafafa]">${escapeHtml(s.name)}</td>
                    <td class="py-3 px-4"><span class="bg-[#111111] px-2 py-0.5 border border-[#2a2a2a] text-[#a1a1aa]">${escapeHtml(s.fromAddress)}</span></td>
                    <td class="py-3 px-4 text-[#a1a1aa]">${escapeHtml(s.smtpHost)}:${s.smtpPort}</td>
                    <td class="py-3 px-4">
                        <span class="border border-blue-500/40 text-blue-400 bg-[#111111] px-2 py-0.5 text-[10px] uppercase">TLS 1.2+ Required</span>
                    </td>
                    <td class="py-3 px-4">
                        ${s.hasPassword
                            ? '<span class="border border-emerald-500/40 text-emerald-400 bg-[#111111] px-2 py-0.5 text-[10px] uppercase">AES-GCM (Encrypted)</span>'
                            : '<span class="border border-[#444444] text-[#a1a1aa] bg-[#111111] px-2 py-0.5 text-[10px] uppercase">None</span>'}
                    </td>
                    <td class="py-3 px-4">
                        ${s.enabled
                            ? `<span class="border border-emerald-500/40 text-emerald-400 bg-[#111111] px-2 py-0.5 text-[10px] uppercase">Active</span>`
                            : `<span class="border border-red-500/40 text-red-400 bg-[#111111] px-2 py-0.5 text-[10px] uppercase">Disabled</span>`}
                    </td>
                    <td class="py-3 px-4 text-right space-x-2">
                        ${s.enabled
                            ? `<button class="text-[11px] text-red-400 hover:underline bg-[#222222] px-2 py-1 border border-[#444444]" onclick="toggleSender('${s.id}', false)">Disable</button>`
                            : `<button class="text-[11px] text-emerald-400 hover:underline bg-[#222222] px-2 py-1 border border-[#444444]" onclick="toggleSender('${s.id}', true)">Enable</button>`}
                        <button onclick="deleteSender('${s.id}', '${escapeHtml(s.name)}')" class="text-[11px] text-red-400 hover:underline bg-[#222222] px-2 py-1 border border-[#444444]">
                            Delete
                        </button>
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
                alert('Failed to register SMTP sender.');
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

    async function deleteSender(id, name) {
        if (!confirm(`Are you sure you want to PERMANENTLY delete SMTP sender '${name}'?`)) return;
        try {
            const res = await fetch(`/api/v1/admin/senders/${id}`, {
                method: 'DELETE',
                headers: getHeaders()
            });
            if (res.ok) {
                loadAll();
            } else {
                alert('Failed to delete sender.');
            }
        } catch (e) {
            alert('Network error deleting sender.');
        }
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
                document.getElementById('recipientsTableBody').innerHTML = `<tr><td colspan="5" class="py-8 text-center text-[#71717a]">No recipients found matching query criteria.</td></tr>`;
                return;
            }
            document.getElementById('recipientsTableBody').innerHTML = recipients.map(r => `
                <tr class="hover:bg-[#141414] transition">
                    <td class="py-3 px-4"><span class="bg-[#111111] px-2 py-0.5 border border-[#2a2a2a] text-[#a1a1aa]">${r.id}</span></td>
                    <td class="py-3 px-4 font-bold text-[#fafafa]">${escapeHtml(r.sanitizedEmail)}</td>
                    <td class="py-3 px-4 text-[#a1a1aa]">${escapeHtml(r.name || '—')}</td>
                    <td class="py-3 px-4"><span class="bg-[#111111] px-2 py-0.5 border border-[#2a2a2a] text-[#71717a]">${r.createdByAppId || '—'}</span></td>
                    <td class="py-3 px-4 text-right text-[#71717a] text-[11px]">${new Date(r.createdAt).toISOString().slice(0, 19).replace('T', ' ')}</td>
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
                document.getElementById('auditTableBody').innerHTML = `<tr><td colspan="6" class="py-8 text-center text-[#71717a]">No notification logs recorded.</td></tr>`;
                return;
            }
            document.getElementById('auditTableBody').innerHTML = logs.map(l => `
                <tr class="hover:bg-[#141414] transition">
                    <td class="py-3 px-4"><span class="bg-[#111111] px-2 py-0.5 border border-[#2a2a2a] text-[#a1a1aa]">${l.id}</span></td>
                    <td class="py-3 px-4 font-semibold text-[#fafafa]">${escapeHtml(l.applicationName || l.applicationId || '—')}</td>
                    <td class="py-3 px-4">
                        <span class="border border-[#444444] text-[#a1a1aa] bg-[#111111] px-1.5 py-0.5 text-[10px] uppercase mr-1">${escapeHtml(l.type)}</span>
                        <span class="text-[#71717a] text-[11px]">${escapeHtml(l.level)}</span>
                    </td>
                    <td class="py-3 px-4">
                        ${l.status === 'SENT'
                            ? `<span class="border border-emerald-500/40 text-emerald-400 bg-[#111111] px-2 py-0.5 text-[10px] uppercase">SENT</span>`
                            : (l.status === 'FAILED'
                                ? `<span class="border border-red-500/40 text-red-400 bg-[#111111] px-2 py-0.5 text-[10px] uppercase">FAILED</span>`
                                : `<span class="border border-amber-500/40 text-amber-400 bg-[#111111] px-2 py-0.5 text-[10px] uppercase">${l.status}</span>`)}
                    </td>
                    <td class="py-3 px-4 max-w-xs text-[#a1a1aa] truncate text-[11px]" title="${escapeHtml(l.errorDetails || '')}">
                        ${escapeHtml(l.errorDetails || '—')}
                    </td>
                    <td class="py-3 px-4 text-right text-[#71717a] text-[11px]">${new Date(l.createdAt).toISOString().slice(0, 19).replace('T', ' ')}</td>
                </tr>
            `).join('');
        } catch (e) {
            console.error(e);
        }
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.toString().replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#032;");
    }

    function copyApiKey() {
        const el = document.getElementById('createdApiKey');
        el.select();
        document.execCommand('copy');
        alert('API Key copied to clipboard.');
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
