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
import com.legalsuite.domain.TrustReconciliation;
import com.legalsuite.service.Pricing;
import com.legalsuite.domain.TrustTransaction;
import com.legalsuite.service.DocketEngine;
import com.legalsuite.service.PracticeService;
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
import com.legalsuite.repo.TrustReconciliationRepository;
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
    private final PracticeService practice;
    private final TrustReconciliationRepository recons;
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
            PracticeService practice,
            TrustReconciliationRepository recons,
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
        this.practice = practice;
        this.recons = recons;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (plans.count() > 0) return;

        Plan light = plan(
                "Light",
                Pricing.LIGHT_SLUG,
                "R1,199 per month for one attorney. The practice desk and section 86 trust are in the seat. Public-network minutes are pay-what-you-use. A local number is optional at about R79, or bundled.",
                Pricing.LIGHT_MONTHLY_ZAR.intValue(),
                Pricing.LIGHT_ATTORNEYS,
                -1,
                Pricing.INCLUDED_PSTN_MINUTES,
                1);
        light.setFeaturesJson(
                "{\"currency\":\"ZAR\",\"seats\":1,\"trustIncluded\":true,\"includedPstnMinutes\":0,\"phone\":\"pay-what-you-use\",\"didMonthlyZar\":79,\"did\":\"Optional local number about R79 per month, or bundled with the seat.\"}");
        plans.save(light);

        AppModule mCases = module("Case Management", "cases", "Matters, status, courts, and parties.", "practice", "⚖️", 0, true, "light", 1);
        module("Client CRM", "clients", "Every relationship the firm is responsible for.", "practice", "👥", 0, true, "light", 2);
        module("Contacts", "contacts", "Opposing counsel, judges, vendors.", "practice", "📇", 0, true, "light", 3);
        module("Calendar", "calendar", "Hearings, depositions, and filing deadlines.", "practice", "📅", 0, true, "light", 4);
        module("Tasks", "tasks", "Work that has an owner and a due date.", "practice", "✅", 0, true, "light", 5);
        module("Documents", "documents", "Case files with privilege flags.", "documents", "📄", 0, true, "light", 6);
        module("Time Tracking", "timetracking", "Timers and 6-minute increments.", "financial", "⏱️", 0, true, "light", 7);
        module("Billing & Invoices", "billing", "Draft, send, and record payment.", "financial", "💰", 0, true, "light", 8);
        module("Messaging", "messages", "Internal threads tied to matters.", "communication", "💬", 0, true, "light", 9);
        AppModule mTrust = module("Trust Accounting", "trust", "Included in Light. Per-client section 86 or IOLTA ledgers. Not a separate month-end charge.", "financial", "🏦", 0, true, "light", 10);
        module("Expenses", "expenses", "Costs advanced and billed back.", "financial", "🧾", 0, false, "light", 11);
        module("Client Portal", "clientportal", "Clients see their case, files, and invoices.", "communication", "🌐", 0, false, "light", 12);
        module("E-Signatures", "esignatures", "Send retainers out for in-app signature. No DocuSign key required.", "documents", "✍️", 0, false, "light", 13);
        module("Conflict Check", "conflicts", "Search clients, parties, and counsel.", "compliance", "🔍", 0, true, "light", 14);
        module("Reports", "reports", "Realization, pipeline, and aging.", "analytics", "📈", 0, true, "light", 15);
        module("AI Assistant", "ai", "Draft help on this tenant. The attorney remains responsible. No vendor key in the default build.", "analytics", "✨", 0, false, "light", 16);
        AppModule mVoice = module("Voice Calling", "voice", "In-app calls are included. Public-network minutes are pay-what-you-use. No minute bundle and no unlimited voice.", "communication", "📞", 0, true, "light", 17);
        module("Document Templates", "templates", "Merge letters and pleadings with matter fields.", "documents", "📝", 0, false, "light", 18);
        module("Integrations", "integrations", "Stripe and Twilio stay off until the operator sets keys in the environment.", "platform", "🔌", 0, false, "light", 19);
        module("Audit Log", "audit", "Who changed what, and when.", "compliance", "📋", 0, true, "light", 20);

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
        firm.setCountry("US");
        firm.setPlanId(light.getId());
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

        AppUser john = user(firm.getId(), "john@smithlaw.com", "password", "John", "Smith", "owner", "Managing Partner", "350", "TX 24081", "TX");
        AppUser maria = user(firm.getId(), "maria@smithlaw.com", "password", "Maria", "Jones", "attorney", "Partner", "325", "TX 25119", "TX");
        AppUser alex = user(firm.getId(), "alex@smithlaw.com", "password", "Alex", "Park", "associate", "Associate", "225", "TX 26802", "TX");

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
        stampDocket(c1, "TX");
        LegalCase c2 = matter(firm.getId(), davis, maria, "C-1045", "Davis v. Metro Transit", "Personal Injury", "open", "325");
        c2.setDescription("Bus collision. Claim against the transit authority.");
        c2.setOpposingParty("Austin Metro Transit Authority");
        c2.setGovernmentalDefendant(true);
        c2.setAccrualDate(LocalDate.now().plusDays(5).minusYears(2));
        stampDocket(c2, "TX");
        LegalCase c3 = matter(firm.getId(), martinez, john, "C-1038", "Martinez Estate", "Estate Planning", "pending", "350");
        c3.setDescription("Probate of the Martinez estate — inventory and creditor window.");
        c3.setProbateOpened(LocalDate.now().plusDays(12).minusMonths(4));
        stampDocket(c3, "TX");
        LegalCase c4 = matter(firm.getId(), abc, alex, "C-1050", "ABC Corp Formation", "Corporate", "open", "225");
        LegalCase c5 = matter(firm.getId(), taylor, maria, "C-1048", "Taylor Contract Dispute", "Contract", "mediation", "325");
        c5.setDescription("Breach of written supply agreement.");
        c5.setOpposingParty("Westlake Supply LLC");
        c5.setAccrualDate(LocalDate.now().plusDays(28).minusYears(4));
        stampDocket(c5, "TX");

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
        pstn.setCostPerMinute(BigDecimal.ZERO);
        pstn.setTotalCost(BigDecimal.ZERO);
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
        iolta.setBankBalance(new BigDecimal("156000.00"));
        iolta.setAccountType("trust");
        iolta.setLastReconciledAt(Instant.now().minus(3, ChronoUnit.DAYS));
        iolta = trusts.save(iolta);
        TrustTransaction tx = new TrustTransaction();
        tx.setTenantId(firm.getId());
        tx.setTrustAccountId(iolta.getId());
        tx.setClientId(sarah.getId());
        tx.setCaseId(c1.getId());
        tx.setType("deposit");
        tx.setAmount(new BigDecimal("25000"));
        tx.setBalanceAfter(new BigDecimal("25000.00"));
        tx.setDescription("Retainer deposited for Johnson v. Corp");
        tx.setCreatedBy(john.getId());
        trustTx.save(tx);
        TrustTransaction tx2 = new TrustTransaction();
        tx2.setTenantId(firm.getId());
        tx2.setTrustAccountId(iolta.getId());
        tx2.setClientId(martinez.getId());
        tx2.setCaseId(c3.getId());
        tx2.setType("deposit");
        tx2.setAmount(new BigDecimal("80000"));
        tx2.setBalanceAfter(new BigDecimal("105000.00"));
        tx2.setDescription("Estate funds — Martinez");
        tx2.setCreatedBy(john.getId());
        trustTx.save(tx2);
        TrustTransaction tx3 = new TrustTransaction();
        tx3.setTenantId(firm.getId());
        tx3.setTrustAccountId(iolta.getId());
        tx3.setClientId(davis.getId());
        tx3.setCaseId(c2.getId());
        tx3.setType("deposit");
        tx3.setAmount(new BigDecimal("51000"));
        tx3.setBalanceAfter(new BigDecimal("156000.00"));
        tx3.setDescription("Retainer — Davis v. Metro Transit");
        tx3.setCreatedBy(maria.getId());
        trustTx.save(tx3);

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

        seedSouthAfrica(light);

        if (light.getSlug() == null || mCases == null || mTrust == null || mVoice == null || c2 == null || c4 == null) {
            throw new IllegalStateException("seed failed");
        }
    }

    private void seedSouthAfrica(Plan light) {
        Tenant firm = new Tenant();
        firm.setFirmName("Ndlovu & Partners");
        firm.setSlug("ndlovu-partners");
        firm.setEmail("thabo@ndlovulaw.co.za");
        firm.setPhone("011 555 0180");
        firm.setWebsite("https://ndlovu-partners.legalsuite.local");
        firm.setAddressLine1("15 Alice Lane, 12th Floor");
        firm.setCity("Sandton");
        firm.setState("GP");
        firm.setZip("2196");
        firm.setCountry("ZA");
        firm.setPlanId(light.getId());
        firm.setStatus("active");
        firm.setOnboardingCompleted(true);
        firm.setTagline("Gauteng trial lawyers. RAF, delict, and the files that cannot wait.");
        firm.setPracticeAreasJson(JsonLists.toJson(List.of("RAF", "Personal Injury", "Labour", "Deceased Estates", "Commercial")));
        firm.setFfcNumber("FFC-GP-2026-44821");
        firm.setFfcExpiresOn(LocalDate.of(2027, 12, 31));
        firm.setFfcHolderName("Thabo Ndlovu");
        firm.setInformationOfficerName("Thabo Ndlovu");
        firm.setInformationOfficerEmail("thabo@ndlovulaw.co.za");
        firm.setPopiaOperatorAcknowledged(true);
        firm.setBankFeedImportedAt(Instant.now());
        firm.setLastBankFeedSource("csv:FNB");
        firm = tenants.save(firm);
        firm.setPaiaManualBody(com.legalsuite.service.PaiaManual.generate(firm));
        firm = tenants.save(firm);

        for (AppModule m : modules.findAll()) {
            TenantModule tm = new TenantModule();
            tm.setTenantId(firm.getId());
            tm.setModuleId(m.getId());
            tm.setEnabled(true);
            tenantModules.save(tm);
        }

        AppUser thabo = user(firm.getId(), "thabo@ndlovulaw.co.za", "password", "Thabo", "Ndlovu", "owner", "Director", "4200", "LPC GP 44821", "GP");
        AppUser lindiwe = user(firm.getId(), "lindiwe@ndlovulaw.co.za", "password", "Lindiwe", "Mokoena", "attorney", "Director", "3200", "LPC GP 51209", "GP");
        AppUser sipho = user(firm.getId(), "sipho@ndlovulaw.co.za", "password", "Sipho", "Dlamini", "associate", "Associate", "2100", "LPC GP 60114", "GP");

        Client nomsa = client(firm.getId(), "individual", "Nomsa", "Khumalo", null, "nomsa@example.com", "website", thabo);
        nomsa.setPortalEnabled(true);
        nomsa.setPortalPasswordHash(encoder.encode("portal123"));
        nomsa.setCity("Soweto");
        nomsa.setState("GP");
        nomsa.setPhone("082 555 0144");
        clients.save(nomsa);
        Client pieter = client(firm.getId(), "individual", "Pieter", "van der Merwe", null, "pieter.vdm@example.com", "referral", lindiwe);
        Client fatima = client(firm.getId(), "individual", "Fatima", "Patel", null, "fatima.patel@example.com", "website", sipho);
        Client estateClient = client(firm.getId(), "individual", "Sibusiso", "Dlamini", "Estate Late J. Dlamini", "estate@example.com", "referral", thabo);
        Client horizon = client(firm.getId(), "company", null, null, "Horizon Logistics (Pty) Ltd", "legal@horizonlog.co.za", "website", lindiwe);

        LegalCase raf = matter(firm.getId(), nomsa, thabo, "C-2001", "Khumalo v Road Accident Fund", "RAF", "open", "4200");
        raf.setDescription("N12 collision. Identified insured driver. Lodge the RAF 1 before prescription.");
        raf.setOpposingParty("Road Accident Fund");
        raf.setCourtName("Johannesburg High Court");
        raf.setAccrualDate(LocalDate.now().plusDays(18).minusYears(3));
        stampDocket(raf, "ZA");

        LegalCase pothole = matter(firm.getId(), pieter, lindiwe, "C-2002", "Van der Merwe v City of Johannesburg", "Personal Injury", "open", "3200");
        pothole.setDescription("Pothole on Oxford Road wrecked the bakkie and injured the driver. Municipality is the organ of state.");
        pothole.setOpposingParty("City of Johannesburg Metropolitan Municipality");
        pothole.setGovernmentalDefendant(true);
        pothole.setCourtName("Johannesburg High Court");
        pothole.setAccrualDate(LocalDate.now().minusDays(20).minusMonths(6));
        stampDocket(pothole, "ZA");

        LegalCase labour = matter(firm.getId(), fatima, sipho, "C-2003", "Patel unfair dismissal", "Labour", "intake", "2100");
        labour.setDescription("Unfair dismissal from the warehouse. Refer to the CCMA.");
        labour.setOpposingParty("Warehouse Group (Pty) Ltd");
        labour.setCourtName("CCMA Johannesburg");
        labour.setAccrualDate(LocalDate.now().minusDays(22));
        stampDocket(labour, "ZA");

        LegalCase deceased = matter(firm.getId(), estateClient, thabo, "C-2004", "Estate Late J. Dlamini", "Deceased Estates", "pending", "4200");
        deceased.setDescription("Letters of executorship issued. Creditor advertisement and L&D account.");
        deceased.setCourtName("Master of the High Court, Johannesburg");
        deceased.setProbateOpened(LocalDate.now().plusDays(10).minusMonths(3));
        stampDocket(deceased, "ZA");

        LegalCase commercial = matter(firm.getId(), horizon, lindiwe, "C-2005", "Horizon Logistics supply breach", "Commercial", "mediation", "3200");
        commercial.setDescription("Breach of written haulage agreement. Ordinary contractual debt.");
        commercial.setOpposingParty("Drakensberg Fuels (Pty) Ltd");
        commercial.setCourtName("Johannesburg High Court");
        commercial.setAccrualDate(LocalDate.now().plusDays(24).minusYears(3));
        stampDocket(commercial, "ZA");

        contact(firm.getId(), "opposing_counsel", "Naledi", "Botha", "Botha Inc", "n.botha@bothainc.co.za");
        contact(firm.getId(), "judge", "T.", "Mabena", "Johannesburg High Court", null);
        note(firm.getId(), raf, thabo, "RAF 1 pack", "Hospital records requested. Do not let s 23 run.");
        time(firm.getId(), raf, thabo, 120, "RAF 1 compilation and hospital follow-up", true, false);
        time(firm.getId(), pothole, lindiwe, 90, "Act 40 s 3 notice draft — overdue", true, false);

        Invoice inv = new Invoice();
        inv.setTenantId(firm.getId());
        inv.setClientId(nomsa.getId());
        inv.setCaseId(raf.getId());
        inv.setInvoiceNumber("INV-ZA-104");
        inv.setStatus("sent");
        inv.setSubtotal(new BigDecimal("21000.00"));
        inv.setTaxAmount(new BigDecimal("3150.00"));
        inv.setTotal(new BigDecimal("24150.00"));
        inv.setAmountPaid(BigDecimal.ZERO);
        inv.setDateDue(LocalDate.now().plusDays(14));
        inv.setNotes("VAT 15% (Value-Added Tax Act 89 of 1991 s 7).");
        inv.setLineItemsJson(JsonLists.toJson(List.of(Map.of("description", "RAF claim compilation", "amount", 21000))));
        invoices.save(inv);

        event(firm.getId(), raf, thabo, "Lodge RAF 1 — Khumalo", "filing", Instant.now().plus(6, ChronoUnit.DAYS), "RAF Parktown");
        event(firm.getId(), labour, sipho, "CCMA referral — Patel", "deadline", Instant.now().plus(2, ChronoUnit.DAYS), "CCMA Johannesburg");

        TrustAccount trust = new TrustAccount();
        trust.setTenantId(firm.getId());
        trust.setAccountName("Section 86(2) trust — FNB");
        trust.setBankName("First National Bank");
        trust.setAccountType("trust");
        trust.setBalance(new BigDecimal("450000.00"));
        trust.setBankBalance(new BigDecimal("438250.00"));
        trust = trusts.save(trust);
        zaDeposit(firm.getId(), trust, nomsa.getId(), raf.getId(), thabo.getId(), "180000", "180000", "RAF interim payment — Khumalo");
        zaDeposit(firm.getId(), trust, pieter.getId(), pothole.getId(), lindiwe.getId(), "95000", "275000", "Retainer — Van der Merwe");
        zaDeposit(firm.getId(), trust, horizon.getId(), commercial.getId(), lindiwe.getId(), "175000", "450000", "Retainer — Horizon Logistics");

        TrustReconciliation recon = new TrustReconciliation();
        recon.setTenantId(firm.getId());
        recon.setTrustAccountId(trust.getId());
        recon.setPeriodEnd(LocalDate.now().withDayOfMonth(1).minusDays(1));
        recon.setBankBalance(new BigDecimal("438250.00"));
        recon.setBookBalance(new BigDecimal("450000.00"));
        recon.setClientLedgerTotal(new BigDecimal("450000.00"));
        recon.setDifference(new BigDecimal("11750.00"));
        recon.setStatus("unbalanced");
        recon.setCertified(false);
        recon.setNotes("Bank is short R11,750 against the cashbook and client ledgers. Do not certify.");
        recons.save(recon);

        LandingPage page = new LandingPage();
        page.setTenantId(firm.getId());
        page.setTemplate("modern");
        page.setHeroTitle("Ndlovu & Partners");
        page.setHeroSubtitle("Sandton attorneys for RAF, delict against organs of state, labour, and deceased estates.");
        page.setAboutText("We take the files whose clocks actually kill the claim — Road Accident Fund, Act 40 notices, CCMA referrals — and we will not open a file until conflicts and a signed mandate are on the instrument.");
        page.setColorsJson("{\"primary\":\"#1a365d\",\"accent\":\"#c6a052\"}");
        page.setSeoTitle("Ndlovu & Partners | Sandton Law Firm");
        page.setPublished(true);
        landingPages.save(page);

        DocumentTemplate mandate = new DocumentTemplate();
        mandate.setTenantId(firm.getId());
        mandate.setName("Written mandate");
        mandate.setCategory("retainer");
        mandate.setBody("""
                {{firm.name}}
                {{firm.address}}
                {{firm.phone}} · {{firm.email}}

                {{today}}

                {{client.name}}
                Re: {{case.title}} ({{case.number}})

                Dear {{client.name}}:

                This is the written mandate. {{firm.name}} will represent you in {{case.title}}. Fees are hourly plus VAT. Trust money is held in the firm's Legal Practice Act s 86 trust account and is never mixed with business money or another client's ledger.

                Sign this instrument. A file stays limited until you do.

                Yours faithfully,
                {{attorney.name}}
                {{attorney.title}}
                """);
        templates.save(mandate);

        notify(firm.getId(), thabo.getId(), "Trust recon is short", "Section 86 three-way is unbalanced by R11,750. Do not certify.", "trust", "/trust");
        notify(firm.getId(), thabo.getId(), "Act 40 notice overdue", "Van der Merwe v City of Johannesburg. The s 3 notice is overdue. Condonation before you issue.", "docket", "/cases");
        notify(firm.getId(), lindiwe.getId(), "New website consult", "A RAF consult is waiting on the hire pipeline.", "intake", "/leads");

        Lead hot = new Lead();
        hot.setTenantId(firm.getId());
        hot.setName("Kagiso Molefe");
        hot.setEmail("kagiso.molefe@example.com");
        hot.setPhone("073 555 0190");
        hot.setCaseType("RAF");
        hot.setOpposingParty("Road Accident Fund");
        hot.setDescription("Taxi collision on the N1 yesterday. Identified driver. Urgent. The other insurer already called.");
        hot.setAccrualDate(LocalDate.now().minusDays(1));
        hot.setStatus("new");
        leads.save(hot);

        Lead conflictLead = new Lead();
        conflictLead.setTenantId(firm.getId());
        conflictLead.setName("Zanele Dlamini");
        conflictLead.setEmail("zanele.dlamini@example.com");
        conflictLead.setPhone("071 555 0166");
        conflictLead.setCaseType("Personal Injury");
        conflictLead.setOpposingParty("City of Johannesburg Metropolitan Municipality");
        conflictLead.setGovernmentalDefendant(true);
        conflictLead.setAccrualDate(LocalDate.now().minusDays(8));
        conflictLead.setDescription("City bus hit her at the Sandton stop eight days ago. The metro already called.");
        conflictLead.setStatus("consultation");
        leads.save(conflictLead);
    }

    private void zaDeposit(java.util.UUID tenant, TrustAccount acct, java.util.UUID clientId, java.util.UUID caseId, java.util.UUID actor, String amount, String after, String memo) {
        TrustTransaction tx = new TrustTransaction();
        tx.setTenantId(tenant);
        tx.setTrustAccountId(acct.getId());
        tx.setClientId(clientId);
        tx.setCaseId(caseId);
        tx.setType("deposit");
        tx.setAmount(new BigDecimal(amount));
        tx.setBalanceAfter(new BigDecimal(after));
        tx.setDescription(memo);
        tx.setCreatedBy(actor);
        trustTx.save(tx);
    }

    private Plan plan(String name, String slug, String desc, int price, int users, int cases, int mins, int order) {
        Plan p = new Plan();
        p.setName(name);
        p.setSlug(slug);
        p.setDescription(desc);
        p.setPriceMonthly(BigDecimal.valueOf(price));
        p.setMaxUsers(users);
        p.setMaxCases(cases);
        p.setIncludedVoiceMinutes(Pricing.capIncludedMinutes(mins));
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

    private AppUser user(java.util.UUID tenant, String email, String pass, String first, String last, String role, String title, String rate, String bar, String barState) {
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
        u.setBarState(barState);
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
        c.setEngagementStatus("signed");
        c.setAppearanceAuthorized(true);
        return cases.save(c);
    }

    private void stampDocket(LegalCase c, String jurisdiction) {
        DocketEngine.stamp(c, DocketEngine.compute(jurisdiction, TexasDocketRules.factsFromCase(c)));
        cases.save(c);
        practice.syncClockTasks(c, c.getLeadAttorneyId());
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
