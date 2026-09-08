package com.legalsuite.seed;

import com.legalsuite.common.JsonLists;
import com.legalsuite.domain.AppModule;
import com.legalsuite.domain.AppNotification;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.AuditLog;
import com.legalsuite.domain.CalendarEvent;
import com.legalsuite.domain.CallRecord;
import com.legalsuite.domain.ChatMessage;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.ConnectedIntegration;
import com.legalsuite.domain.Contact;
import com.legalsuite.domain.Conversation;
import com.legalsuite.domain.DocumentFile;
import com.legalsuite.domain.DocumentTemplate;
import com.legalsuite.domain.Expense;
import com.legalsuite.domain.Invoice;
import com.legalsuite.domain.LandingPage;
import com.legalsuite.domain.Lead;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.Note;
import com.legalsuite.domain.Plan;
import com.legalsuite.domain.SignatureRequest;
import com.legalsuite.domain.TaskItem;
import com.legalsuite.domain.Tenant;
import com.legalsuite.domain.TenantModule;
import com.legalsuite.domain.TimeEntry;
import com.legalsuite.domain.TrustAccount;
import com.legalsuite.domain.TrustTransaction;
import com.legalsuite.service.TexasDocketRules;
import com.legalsuite.repo.AppModuleRepository;
import com.legalsuite.repo.AppNotificationRepository;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.AuditLogRepository;
import com.legalsuite.repo.CalendarEventRepository;
import com.legalsuite.repo.CallRecordRepository;
import com.legalsuite.repo.ChatMessageRepository;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.ConnectedIntegrationRepository;
import com.legalsuite.repo.ContactRepository;
import com.legalsuite.repo.ConversationRepository;
import com.legalsuite.repo.DocumentFileRepository;
import com.legalsuite.repo.DocumentTemplateRepository;
import com.legalsuite.repo.ExpenseRepository;
import com.legalsuite.repo.InvoiceRepository;
import com.legalsuite.repo.LandingPageRepository;
import com.legalsuite.repo.LeadRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.NoteRepository;
import com.legalsuite.repo.PlanRepository;
import com.legalsuite.repo.SignatureRequestRepository;
import com.legalsuite.repo.TaskItemRepository;
import com.legalsuite.repo.TenantModuleRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.repo.TimeEntryRepository;
import com.legalsuite.repo.TrustAccountRepository;
import com.legalsuite.repo.TrustTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DemoDataLoader implements CommandLineRunner {
    private final PlanRepository plans;
    private final AppModuleRepository modules;
    private final TenantRepository tenants;
    private final TenantModuleRepository tenantModules;
    private final AppUserRepository users;
    private final ClientRepository clients;
    private final LegalCaseRepository cases;
    private final ContactRepository contacts;
    private final DocumentFileRepository documents;
    private final TimeEntryRepository timeEntries;
    private final InvoiceRepository invoices;
    private final CalendarEventRepository events;
    private final TaskItemRepository tasks;
    private final CallRecordRepository calls;
    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final TrustAccountRepository trusts;
    private final TrustTransactionRepository trustTx;
    private final ExpenseRepository expenses;
    private final NoteRepository notes;
    private final LandingPageRepository landingPages;
    private final AppNotificationRepository notifications;
    private final DocumentTemplateRepository templates;
    private final SignatureRequestRepository signatures;
    private final ConnectedIntegrationRepository integrations;
    private final AuditLogRepository auditLogs;
    private final LeadRepository leads;
    private final PasswordEncoder encoder;

    public DemoDataLoader(
            PlanRepository plans,
            AppModuleRepository modules,
            TenantRepository tenants,
            TenantModuleRepository tenantModules,
            AppUserRepository users,
            ClientRepository clients,
            LegalCaseRepository cases,
            ContactRepository contacts,
            DocumentFileRepository documents,
            TimeEntryRepository timeEntries,
            InvoiceRepository invoices,
            CalendarEventRepository events,
            TaskItemRepository tasks,
            CallRecordRepository calls,
            ConversationRepository conversations,
            ChatMessageRepository messages,
            TrustAccountRepository trusts,
            TrustTransactionRepository trustTx,
            ExpenseRepository expenses,
            NoteRepository notes,
            LandingPageRepository landingPages,
            AppNotificationRepository notifications,
            DocumentTemplateRepository templates,
            SignatureRequestRepository signatures,
            ConnectedIntegrationRepository integrations,
            AuditLogRepository auditLogs,
            LeadRepository leads,
            PasswordEncoder encoder) {
        this.plans = plans;
        this.modules = modules;
        this.tenants = tenants;
        this.tenantModules = tenantModules;
        this.users = users;
        this.clients = clients;
        this.cases = cases;
        this.contacts = contacts;
        this.documents = documents;
        this.timeEntries = timeEntries;
        this.invoices = invoices;
        this.events = events;
        this.tasks = tasks;
        this.calls = calls;
        this.conversations = conversations;
        this.messages = messages;
        this.trusts = trusts;
        this.trustTx = trustTx;
        this.expenses = expenses;
        this.notes = notes;
        this.landingPages = landingPages;
        this.notifications = notifications;
        this.templates = templates;
        this.signatures = signatures;
        this.integrations = integrations;
        this.auditLogs = auditLogs;
        this.leads = leads;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (plans.count() > 0) return;

        Plan free = plan("Free", "free", "Landing page, intake, and a starter docket.", 0, 2, 25, 30, 1);
        Plan ess = plan("Essentials", "essentials", "Solo and small firms ready to bill and file.", 49, 5, -1, 200, 2);
        Plan pro = plan("Professional", "professional", "Growing firms with trust accounting and voice.", 99, 25, -1, 1000, 3);
        plan("Enterprise", "enterprise", "Multi-office firms that need every module on.", 149, -1, -1, -1, 4);

        AppModule mCases = module("Case Management", "cases", "Matters, status, courts, and parties.", "practice", "⚖️", 0, true, "free", 1);
        module("Client CRM", "clients", "Every relationship the firm is responsible for.", "practice", "👥", 0, true, "free", 2);
        module("Contacts", "contacts", "Opposing counsel, judges, vendors.", "practice", "📇", 0, true, "free", 3);
        module("Calendar", "calendar", "Hearings, depositions, and filing deadlines.", "practice", "📅", 0, true, "free", 4);
        module("Tasks", "tasks", "Work that has an owner and a due date.", "practice", "✅", 0, true, "free", 5);
        module("Documents", "documents", "Case files with privilege flags.", "documents", "📄", 0, true, "free", 6);
        module("Time Tracking", "timetracking", "Timers and 6-minute increments.", "financial", "⏱️", 0, true, "essentials", 7);
        module("Billing & Invoices", "billing", "Draft, send, and record payment.", "financial", "💰", 0, true, "essentials", 8);
        module("Messaging", "messages", "Internal threads tied to matters.", "communication", "💬", 0, true, "essentials", 9);
        AppModule mTrust = module("Trust Accounting", "trust", "IOLTA ledgers that cannot overdraw.", "financial", "🏦", 29, false, "professional", 10);
        module("Expenses", "expenses", "Costs advanced and billed back.", "financial", "🧾", 10, false, "essentials", 11);
        module("Client Portal", "clientportal", "Clients see their case, files, and invoices.", "communication", "🌐", 19, false, "essentials", 12);
        module("E-Signatures", "esignatures", "Send retainers out for in-app signature. No DocuSign key required.", "documents", "✍️", 15, false, "professional", 13);
        module("Conflict Check", "conflicts", "Search clients, parties, and counsel.", "compliance", "🔍", 0, true, "essentials", 14);
        module("Reports", "reports", "Realization, pipeline, and aging.", "analytics", "📈", 0, true, "essentials", 15);
        module("AI Assistant", "ai", "Local summaries, intake screening, and drafts. No API key.", "analytics", "✨", 19, false, "professional", 16);
        AppModule mVoice = module("Voice Calling", "voice", "In-app WebRTC. Pay only for PSTN minutes.", "communication", "📞", 0, true, "essentials", 17);
        module("Document Templates", "templates", "Merge letters and pleadings with matter fields.", "documents", "📝", 10, false, "essentials", 18);
        module("Integrations", "integrations", "Connect Stripe, Twilio, Calendar, and import tools.", "platform", "🔌", 0, false, "professional", 19);
        module("Audit Log", "audit", "Who changed what, and when.", "compliance", "📋", 0, true, "essentials", 20);

        Tenant firm = new Tenant();
        firm.setFirmName("Smith & Associates");
        firm.setSlug("smith-associates");
        firm.setEmail("john@smithlaw.com");
        firm.setPhone("(555) 234-5678");
        firm.setWebsite("https://smith-associates.legalsuite.local");
        firm.setAddressLine1("1200 Commerce Street, Suite 400");
        firm.setCity("Austin");
        firm.setState("TX");
        firm.setZip("78701");
        firm.setPlanId(pro.getId());
        firm.setStatus("active");
        firm.setOnboardingCompleted(true);
        firm.setTagline("Justice with a steady hand.");
        firm.setPracticeAreasJson(JsonLists.toJson(List.of("Litigation", "Corporate", "Family Law", "Estate Planning", "Real Estate")));
        firm = tenants.save(firm);

        for (AppModule m : modules.findAll()) {
            TenantModule tm = new TenantModule();
            tm.setTenantId(firm.getId());
            tm.setModuleId(m.getId());
            tm.setEnabled(true);
            tenantModules.save(tm);
        }

        AppUser john = user(firm.getId(), "john@smithlaw.com", "password", "John", "Smith", "owner", "Managing Partner", "350", "TX 24081");
        AppUser maria = user(firm.getId(), "maria@smithlaw.com", "password", "Maria", "Jones", "attorney", "Partner", "325", "TX 25119");
        AppUser alex = user(firm.getId(), "alex@smithlaw.com", "password", "Alex", "Park", "associate", "Associate", "225", "TX 26802");

        Client sarah = client(firm.getId(), "individual", "Sarah", "Williams", null, "sarah@example.com", "referral", john);
        sarah.setPortalEnabled(true);
        sarah.setPortalPasswordHash(encoder.encode("portal123"));
        sarah.setCity("Austin");
        sarah.setState("TX");
        sarah.setPhone("(555) 010-4411");
        clients.save(sarah);
        Client abc = client(firm.getId(), "company", null, null, "ABC Corp", "gc@abccorp.com", "website", maria);
        Client martinez = client(firm.getId(), "individual", "Elena", "Martinez", "Martinez Estate", "elena@example.com", "referral", john);
        Client davis = client(firm.getId(), "individual", "Marcus", "Davis", null, "marcus.davis@example.com", "advertising", maria);
        Client taylor = client(firm.getId(), "company", null, null, "Taylor Holdings", "ops@taylorhold.com", "website", alex);

        LegalCase c1 = matter(firm.getId(), sarah, john, "C-1042", "Johnson v. Corp Inc.", "Litigation", "discovery", "350");
        c1.setDescription("Personal injury suit against Corp Inc. after a vehicle collision.");
        c1.setOpposingParty("Corp Inc.");
        c1.setAccrualDate(LocalDate.now().plusDays(16).minusYears(2));
        stampDocket(c1);
        LegalCase c2 = matter(firm.getId(), davis, maria, "C-1045", "Davis v. Metro Transit", "Personal Injury", "open", "325");
        c2.setDescription("Bus collision. Claim against the transit authority.");
        c2.setOpposingParty("Austin Metro Transit Authority");
        c2.setGovernmentalDefendant(true);
        c2.setAccrualDate(LocalDate.now().plusDays(5).minusYears(2));
        stampDocket(c2);
        LegalCase c3 = matter(firm.getId(), martinez, john, "C-1038", "Martinez Estate", "Estate Planning", "pending", "350");
        c3.setDescription("Probate of the Martinez estate — inventory and creditor window.");
        c3.setProbateOpened(LocalDate.now().plusDays(12).minusMonths(4));
        stampDocket(c3);
        LegalCase c4 = matter(firm.getId(), abc, alex, "C-1050", "ABC Corp Formation", "Corporate", "open", "225");
        LegalCase c5 = matter(firm.getId(), taylor, maria, "C-1048", "Taylor Contract Dispute", "Contract", "mediation", "325");
        c5.setDescription("Breach of written supply agreement.");
        c5.setOpposingParty("Westlake Supply LLC");
        c5.setAccrualDate(LocalDate.now().plusDays(28).minusYears(4));
        stampDocket(c5);

        contact(firm.getId(), "opposing_counsel", "Renee", "Hale", "Hale & Whit", "rhale@halewhit.com");
        contact(firm.getId(), "judge", "Harold", "Nguyen", "Travis County District Court", null);

        note(firm.getId(), c1, john, "Hearing prep", "Confirm exhibits 12–18 and the video deposition clip for Dr. Chen.");
        doc(firm.getId(), c1, john, "deposition_transcript.pdf", "discovery");
        doc(firm.getId(), c3, john, "will_draft_v4.docx", "estate");

        time(firm.getId(), c1, john, 150, "Hearing preparation and exhibit review", true, false);
        time(firm.getId(), c3, maria, 90, "Probate filing package", true, true);
        time(firm.getId(), c5, alex, 60, "Mediation brief", true, false);

        Invoice inv = new Invoice();
        inv.setTenantId(firm.getId());
        inv.setClientId(martinez.getId());
        inv.setCaseId(c3.getId());
        inv.setInvoiceNumber("INV-1084");
        inv.setStatus("paid");
        inv.setTotal(new BigDecimal("4875.00"));
        inv.setSubtotal(new BigDecimal("4875.00"));
        inv.setAmountPaid(new BigDecimal("4875.00"));
        inv.setDateDue(LocalDate.now().minusDays(2));
        inv.setLineItemsJson(JsonLists.toJson(List.of(Map.of("description", "Estate administration", "amount", 4875))));
        invoices.save(inv);

        Invoice openInv = new Invoice();
        openInv.setTenantId(firm.getId());
        openInv.setClientId(sarah.getId());
        openInv.setCaseId(c1.getId());
        openInv.setInvoiceNumber("INV-1087");
        openInv.setStatus("sent");
        openInv.setTotal(new BigDecimal("8750.00"));
        openInv.setSubtotal(new BigDecimal("8750.00"));
        openInv.setAmountPaid(BigDecimal.ZERO);
        openInv.setDateDue(LocalDate.now().plusDays(12));
        openInv.setLineItemsJson(JsonLists.toJson(List.of(Map.of("description", "Discovery and hearing prep", "amount", 8750))));
        invoices.save(openInv);

        event(firm.getId(), c1, john, "Hearing — Johnson v. Corp Inc.", "court_date", Instant.now().plus(4, ChronoUnit.HOURS), "County Courthouse, Room 4B");
        event(firm.getId(), c1, john, "Client meeting — Sarah Williams", "meeting", Instant.now().plus(8, ChronoUnit.HOURS), "Conference Room A");
        event(firm.getId(), c3, maria, "Filing deadline — Martinez Estate", "deadline", Instant.now().plus(28, ChronoUnit.HOURS), "Probate clerk");
        event(firm.getId(), c1, john, "Deposition — Dr. Robert Chen", "deposition", Instant.now().plus(11, ChronoUnit.DAYS), "Zoom");
        event(firm.getId(), c5, maria, "Mediation — Taylor Contract Dispute", "mediation", Instant.now().plus(13, ChronoUnit.DAYS), "Mediation Center");

        task(firm.getId(), c1, john, alex, "Draft motion in limine", "in_progress", "high");
        task(firm.getId(), c3, maria, maria, "File inventory with probate court", "todo", "urgent");
        task(firm.getId(), c2, maria, alex, "Request medical records set 2", "todo", "medium");
        task(firm.getId(), c4, alex, alex, "Prepare operating agreement", "in_review", "medium");
        task(firm.getId(), c5, maria, john, "Mediation brief to opposing counsel", "todo", "high");

        CallRecord call = new CallRecord();
        call.setTenantId(firm.getId());
        call.setCaseId(c1.getId());
        call.setClientId(sarah.getId());
        call.setCallerUserId(john.getId());
        call.setCallType("webrtc");
        call.setDirection("outbound");
        call.setStatus("completed");
        call.setStartedAt(Instant.now().minus(2, ChronoUnit.HOURS));
        call.setEndedAt(Instant.now().minus(2, ChronoUnit.HOURS).plusSeconds(754));
        call.setDurationSeconds(754);
        call.setTotalCost(BigDecimal.ZERO);
        calls.save(call);

        CallRecord pstn = new CallRecord();
        pstn.setTenantId(firm.getId());
        pstn.setCaseId(c2.getId());
        pstn.setCallerUserId(maria.getId());
        pstn.setCallType("pstn_outbound");
        pstn.setDirection("outbound");
        pstn.setStatus("completed");
        pstn.setStartedAt(Instant.now().minus(26, ChronoUnit.HOURS));
        pstn.setEndedAt(Instant.now().minus(26, ChronoUnit.HOURS).plusSeconds(480));
        pstn.setDurationSeconds(480);
        pstn.setCostPerMinute(new BigDecimal("0.02"));
        pstn.setTotalCost(new BigDecimal("0.16"));
        pstn.setRecordingEnabled(false);
        calls.save(pstn);

        Conversation conv = new Conversation();
        conv.setTenantId(firm.getId());
        conv.setTitle("Johnson hearing team");
        conv.setType("group");
        conv.setCaseId(c1.getId());
        conv.setLastMessageAt(Instant.now().minus(20, ChronoUnit.MINUTES));
        conv = conversations.save(conv);
        ChatMessage msg = new ChatMessage();
        msg.setTenantId(firm.getId());
        msg.setConversationId(conv.getId());
        msg.setSenderId(maria.getId());
        msg.setBody("Transcript is in the file. I flagged the privilege objection at page 41.");
        messages.save(msg);

        TrustAccount iolta = new TrustAccount();
        iolta.setTenantId(firm.getId());
        iolta.setAccountName("IOLTA — Operating trust");
        iolta.setBankName("First State Bank");
        iolta.setBalance(new BigDecimal("156000.00"));
        iolta = trusts.save(iolta);
        TrustTransaction tx = new TrustTransaction();
        tx.setTenantId(firm.getId());
        tx.setTrustAccountId(iolta.getId());
        tx.setClientId(sarah.getId());
        tx.setCaseId(c1.getId());
        tx.setType("deposit");
        tx.setAmount(new BigDecimal("25000"));
        tx.setBalanceAfter(new BigDecimal("156000.00"));
        tx.setDescription("Retainer deposited for Johnson v. Corp");
        tx.setCreatedBy(john.getId());
        trustTx.save(tx);

        Expense exp = new Expense();
        exp.setTenantId(firm.getId());
        exp.setCaseId(c1.getId());
        exp.setUserId(alex.getId());
        exp.setCategory("filing fees");
        exp.setDescription("County filing fee — motion");
        exp.setAmount(new BigDecimal("237.00"));
        exp.setVendor("Travis County Clerk");
        expenses.save(exp);

        LandingPage page = new LandingPage();
        page.setTenantId(firm.getId());
        page.setTemplate("classic");
        page.setHeroTitle("Smith & Associates");
        page.setHeroSubtitle("Trial lawyers for families and closely held companies in Texas.");
        page.setAboutText("We take matters that require both judgment and stamina — commercial disputes, serious injuries, and the private work of families putting their houses in order.");
        page.setColorsJson("{\"primary\":\"#1a365d\",\"accent\":\"#c6a052\"}");
        page.setSeoTitle("Smith & Associates | Austin Law Firm");
        page.setPublished(true);
        landingPages.save(page);

        notify(firm.getId(), john.getId(), "Hearing today", "Johnson v. Corp Inc. is on the 10:00 a.m. docket in Room 4B.", "calendar", "/calendar");
        notify(firm.getId(), john.getId(), "Invoice paid", "Invoice #1084 was marked paid.", "billing", "/billing");
        notify(firm.getId(), john.getId(), "New intake", "ABC Corp requested a corporate formation consult from the website.", "intake", "/clients");

        DocumentTemplate retainer = new DocumentTemplate();
        retainer.setTenantId(firm.getId());
        retainer.setName("Hourly engagement letter");
        retainer.setCategory("retainer");
        retainer.setBody("""
                {{firm.name}}
                {{firm.address}}
                {{firm.phone}} · {{firm.email}}

                {{today}}

                {{client.name}}
                Re: {{case.title}} ({{case.number}})

                Dear {{client.name}}:

                This letter confirms that {{firm.name}} will represent you in {{case.title}}. Work will be billed hourly. Trust funds, if any, will be held in the firm IOLTA account and applied only to earned fees and costs.

                Please sign the accompanying engagement agreement and return it so we may appear and file.

                Very truly yours,
                {{attorney.name}}
                {{attorney.title}}
                """);
        templates.save(retainer);

        DocumentTemplate status = new DocumentTemplate();
        status.setTenantId(firm.getId());
        status.setName("Client status letter");
        status.setCategory("correspondence");
        status.setBody("""
                {{today}}

                Dear {{client.name}},

                A brief update on {{case.title}} ({{case.number}}). The matter is currently {{case.status}}. {{case.court}} remains the venue. We will write again when a hearing date or filing is set.

                Sincerely,
                {{attorney.name}}
                {{firm.name}}
                """);
        templates.save(status);

        DocumentTemplate demand = new DocumentTemplate();
        demand.setTenantId(firm.getId());
        demand.setName("Demand letter");
        demand.setCategory("demand");
        demand.setBody("""
                {{today}}

                {{case.opposing}}

                Re: {{case.title}} — {{case.number}}

                We represent {{client.name}}. This letter demands that you cure the dispute described in the complaint and confirm a written response within fourteen (14) days.

                {{firm.name}}
                {{attorney.name}}
                """);
        templates.save(demand);

        SignatureRequest sig = new SignatureRequest();
        sig.setTenantId(firm.getId());
        sig.setCaseId(c1.getId());
        sig.setClientId(sarah.getId());
        sig.setTitle("Engagement letter — Johnson v. Corp Inc.");
        sig.setSignerName("Sarah Williams");
        sig.setSignerEmail("sarah@example.com");
        sig.setStatus("pending");
        sig.setDocumentBody("""
                ENGAGEMENT AGREEMENT

                Smith & Associates agrees to represent Sarah Williams in Johnson v. Corp Inc. Fees are hourly. Costs advanced will be billed separately. This agreement is not a guarantee of result.

                Sign below to retain the firm.
                """);
        signatures.save(sig);

        ConnectedIntegration gcal = new ConnectedIntegration();
        gcal.setTenantId(firm.getId());
        gcal.setProvider("google_calendar");
        gcal.setConnected(true);
        gcal.setConnectedAt(java.time.Instant.now().minus(12, ChronoUnit.DAYS));
        gcal.setStatusNote("Connected in this workspace. Live credentials are not required for the local demo.");
        integrations.save(gcal);

        AuditLog a1 = new AuditLog();
        a1.setTenantId(firm.getId());
        a1.setActorId(john.getId());
        a1.setActorEmail(john.getEmail());
        a1.setAction("case.create");
        a1.setEntityType("case");
        a1.setEntityId(c1.getId().toString());
        a1.setDetail("Opened Johnson v. Corp Inc.");
        auditLogs.save(a1);
        AuditLog a2 = new AuditLog();
        a2.setTenantId(firm.getId());
        a2.setActorId(maria.getId());
        a2.setActorEmail(maria.getEmail());
        a2.setAction("invoice.pay");
        a2.setEntityType("invoice");
        a2.setEntityId(inv.getId().toString());
        a2.setDetail("Marked INV-1084 paid");
        auditLogs.save(a2);

        Lead hot = new Lead();
        hot.setTenantId(firm.getId());
        hot.setName("Priya Nair");
        hot.setEmail("priya.nair@example.com");
        hot.setPhone("(555) 010-8822");
        hot.setCaseType("Personal Injury");
        hot.setDescription("Rear-end crash on I-35 yesterday. Urgent — opposing insurer already called.");
        hot.setAccrualDate(LocalDate.now().minusDays(1));
        hot.setStatus("new");
        leads.save(hot);

        Lead conflictLead = new Lead();
        conflictLead.setTenantId(firm.getId());
        conflictLead.setName("Aisha Rahman");
        conflictLead.setEmail("aisha.rahman@example.com");
        conflictLead.setPhone("(555) 010-2290");
        conflictLead.setCaseType("Personal Injury");
        conflictLead.setOpposingParty("Austin Metro Transit Authority");
        conflictLead.setGovernmentalDefendant(true);
        conflictLead.setAccrualDate(LocalDate.now().minusDays(10));
        conflictLead.setDescription("City bus hit her at the stop ten days ago. Metro already called.");
        conflictLead.setStatus("consultation");
        leads.save(conflictLead);

        // unused vars to keep compiler happy if modules referenced
        if (free.getSlug() == null || ess.getSlug() == null || mCases == null || mTrust == null || mVoice == null || c2 == null || c4 == null) {
            throw new IllegalStateException("seed failed");
        }
    }

    private Plan plan(String name, String slug, String desc, int price, int users, int cases, int mins, int order) {
        Plan p = new Plan();
        p.setName(name);
        p.setSlug(slug);
        p.setDescription(desc);
        p.setPriceMonthly(BigDecimal.valueOf(price));
        p.setMaxUsers(users);
        p.setMaxCases(cases);
        p.setIncludedVoiceMinutes(mins);
        p.setSortOrder(order);
        p.setActive(true);
        p.setFeaturesJson("{}");
        return plans.save(p);
    }

    private AppModule module(String name, String slug, String desc, String cat, String icon, int price, boolean core, String min, int order) {
        AppModule m = new AppModule();
        m.setName(name);
        m.setSlug(slug);
        m.setDescription(desc);
        m.setCategory(cat);
        m.setIcon(icon);
        m.setPriceMonthly(BigDecimal.valueOf(price));
        m.setCore(core);
        m.setMinPlanSlug(min);
        m.setSortOrder(order);
        m.setActive(true);
        return modules.save(m);
    }

    private AppUser user(java.util.UUID tenant, String email, String pass, String first, String last, String role, String title, String rate, String bar) {
        AppUser u = new AppUser();
        u.setTenantId(tenant);
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(pass));
        u.setFirstName(first);
        u.setLastName(last);
        u.setRole(role);
        u.setTitle(title);
        u.setStatus("active");
        u.setHourlyRate(new BigDecimal(rate));
        u.setBarNumber(bar);
        u.setBarState("TX");
        u.setOnlineStatus("offline");
        return users.save(u);
    }

    private Client client(java.util.UUID tenant, String type, String first, String last, String company, String email, String source, AppUser atty) {
        Client c = new Client();
        c.setTenantId(tenant);
        c.setType(type);
        c.setStatus("active");
        c.setFirstName(first);
        c.setLastName(last);
        c.setCompanyName(company);
        c.setEmail(email);
        c.setSource(source);
        c.setAssignedAttorneyId(atty.getId());
        return clients.save(c);
    }

    private LegalCase matter(java.util.UUID tenant, Client client, AppUser atty, String num, String title, String area, String status, String rate) {
        LegalCase c = new LegalCase();
        c.setTenantId(tenant);
        c.setClientId(client.getId());
        c.setCaseNumber(num);
        c.setTitle(title);
        c.setPracticeArea(area);
        c.setCaseType(area);
        c.setStatus(status);
        c.setLeadAttorneyId(atty.getId());
        c.setBillingType("hourly");
        c.setBillingRate(new BigDecimal(rate));
        c.setDateOpened(LocalDate.now().minusDays(40));
        c.setConflictChecked(true);
        c.setCourtName("Travis County District Court");
        return cases.save(c);
    }

    private void stampDocket(LegalCase c) {
        TexasDocketRules.stamp(c, TexasDocketRules.compute(TexasDocketRules.factsFromCase(c)));
        cases.save(c);
    }

    private void contact(java.util.UUID tenant, String type, String first, String last, String company, String email) {
        Contact c = new Contact();
        c.setTenantId(tenant);
        c.setType(type);
        c.setFirstName(first);
        c.setLastName(last);
        c.setCompany(company);
        c.setEmail(email);
        contacts.save(c);
    }

    private void note(java.util.UUID tenant, LegalCase matter, AppUser user, String title, String body) {
        Note n = new Note();
        n.setTenantId(tenant);
        n.setCaseId(matter.getId());
        n.setUserId(user.getId());
        n.setTitle(title);
        n.setBody(body);
        notes.save(n);
    }

    private void doc(java.util.UUID tenant, LegalCase matter, AppUser user, String name, String cat) {
        DocumentFile d = new DocumentFile();
        d.setTenantId(tenant);
        d.setCaseId(matter.getId());
        d.setUploadedBy(user.getId());
        d.setName(name);
        d.setOriginalName(name);
        d.setCategory(cat);
        d.setMimeType("application/pdf");
        d.setSizeBytes(240_000);
        d.setStoragePath("seed://" + name);
        documents.save(d);
    }

    private void time(java.util.UUID tenant, LegalCase matter, AppUser user, int minutes, String desc, boolean billable, boolean billed) {
        TimeEntry t = new TimeEntry();
        t.setTenantId(tenant);
        t.setCaseId(matter.getId());
        t.setUserId(user.getId());
        t.setDurationMinutes(minutes);
        t.setHourlyRate(user.getHourlyRate());
        t.setTotalAmount(user.getHourlyRate().multiply(BigDecimal.valueOf(minutes)).divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP));
        t.setDescription(desc);
        t.setBillable(billable);
        t.setBilled(billed);
        timeEntries.save(t);
    }

    private void event(java.util.UUID tenant, LegalCase matter, AppUser user, String title, String type, Instant start, String loc) {
        CalendarEvent e = new CalendarEvent();
        e.setTenantId(tenant);
        e.setCaseId(matter.getId());
        e.setCreatedBy(user.getId());
        e.setTitle(title);
        e.setType(type);
        e.setStartTime(start);
        e.setEndTime(start.plus(2, ChronoUnit.HOURS));
        e.setLocation(loc);
        events.save(e);
    }

    private void task(java.util.UUID tenant, LegalCase matter, AppUser creator, AppUser assignee, String title, String status, String priority) {
        TaskItem t = new TaskItem();
        t.setTenantId(tenant);
        t.setCaseId(matter.getId());
        t.setCreatedBy(creator.getId());
        t.setAssignedTo(assignee.getId());
        t.setTitle(title);
        t.setStatus(status);
        t.setPriority(priority);
        t.setDueDate(Instant.now().plus(3, ChronoUnit.DAYS));
        tasks.save(t);
    }

    private void notify(java.util.UUID tenant, java.util.UUID user, String title, String body, String type, String link) {
        AppNotification n = new AppNotification();
        n.setTenantId(tenant);
        n.setUserId(user);
        n.setTitle(title);
        n.setBody(body);
        n.setType(type);
        n.setLink(link);
        notifications.save(n);
    }
}
