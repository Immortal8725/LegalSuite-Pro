package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.JsonLists;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.CalendarEvent;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.Contact;
import com.legalsuite.domain.DocumentFile;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.Note;
import com.legalsuite.domain.TaskItem;
import com.legalsuite.repo.CalendarEventRepository;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.ContactRepository;
import com.legalsuite.repo.DocumentFileRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.NoteRepository;
import com.legalsuite.repo.TaskItemRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PracticeService {
    private final ClientRepository clients;
    private final LegalCaseRepository cases;
    private final ContactRepository contacts;
    private final DocumentFileRepository documents;
    private final CalendarEventRepository events;
    private final TaskItemRepository tasks;
    private final NoteRepository notes;
    private final Path uploadRoot;

    public PracticeService(
            ClientRepository clients,
            LegalCaseRepository cases,
            ContactRepository contacts,
            DocumentFileRepository documents,
            CalendarEventRepository events,
            TaskItemRepository tasks,
            NoteRepository notes,
            @Value("${legalsuite.upload-dir:./uploads}") String uploadDir) throws IOException {
        this.clients = clients;
        this.cases = cases;
        this.contacts = contacts;
        this.documents = documents;
        this.events = events;
        this.tasks = tasks;
        this.notes = notes;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath();
        Files.createDirectories(this.uploadRoot);
    }

    public List<Map<String, Object>> listClients() {
        return clients.findByTenantIdOrderByLastNameAsc(tid()).stream().map(this::clientView).toList();
    }

    public Map<String, Object> getClient(UUID id) {
        Client c = clients.findByIdAndTenantId(id, tid()).orElseThrow(() -> ApiException.notFound("Client not found"));
        Map<String, Object> view = clientView(c);
        view.put("cases", cases.findByTenantIdAndClientId(tid(), id).stream().map(this::caseView).toList());
        return view;
    }

    @Transactional
    public Map<String, Object> saveClient(UUID id, Map<String, Object> body) {
        Client c = id == null
                ? new Client()
                : clients.findByIdAndTenantId(id, tid()).orElseThrow(() -> ApiException.notFound("Client not found"));
        c.setTenantId(tid());
        c.setType(str(body, "type", c.getType() == null ? "individual" : c.getType()));
        c.setStatus(str(body, "status", c.getStatus() == null ? "active" : c.getStatus()));
        c.setFirstName(str(body, "firstName", c.getFirstName()));
        c.setLastName(str(body, "lastName", c.getLastName()));
        c.setCompanyName(str(body, "companyName", c.getCompanyName()));
        c.setEmail(str(body, "email", c.getEmail()));
        c.setPhone(str(body, "phone", c.getPhone()));
        c.setCity(str(body, "city", c.getCity()));
        c.setState(str(body, "state", c.getState()));
        c.setAddressLine1(str(body, "addressLine1", c.getAddressLine1()));
        c.setSource(str(body, "source", c.getSource()));
        c.setNotes(str(body, "notes", c.getNotes()));
        if (body.get("portalEnabled") != null) {
            c.setPortalEnabled(Boolean.parseBoolean(String.valueOf(body.get("portalEnabled"))));
        }
        c.setUpdatedAt(Instant.now());
        clients.save(c);
        return clientView(c);
    }

    public List<Map<String, Object>> listCases() {
        return cases.findByTenantIdOrderByUpdatedAtDesc(tid()).stream().map(this::caseView).toList();
    }

    public Map<String, Object> getCase(String ref) {
        LegalCase c = resolveCase(ref);
        Map<String, Object> view = caseView(c);
        view.put("notes", notes.findByTenantIdAndCaseIdOrderByCreatedAtDesc(tid(), c.getId()).stream().map(this::noteView).toList());
        view.put("documents", documents.findByTenantIdAndCaseId(tid(), c.getId()).stream().map(this::docView).toList());
        return view;
    }

    @Transactional
    public Map<String, Object> saveCase(UUID id, Map<String, Object> body) {
        LegalCase c = id == null
                ? new LegalCase()
                : requireCase(id);
        c.setTenantId(tid());
        if (body.get("clientId") != null) c.setClientId(UUID.fromString(String.valueOf(body.get("clientId"))));
        c.setTitle(str(body, "title", c.getTitle()));
        c.setDescription(str(body, "description", c.getDescription()));
        c.setCaseType(str(body, "caseType", c.getCaseType()));
        c.setPracticeArea(str(body, "practiceArea", c.getPracticeArea()));
        c.setStatus(str(body, "status", c.getStatus() == null ? "open" : c.getStatus()));
        c.setPriority(str(body, "priority", c.getPriority() == null ? "medium" : c.getPriority()));
        c.setCourtName(str(body, "courtName", c.getCourtName()));
        c.setJudgeName(str(body, "judgeName", c.getJudgeName()));
        c.setOpposingParty(str(body, "opposingParty", c.getOpposingParty()));
        c.setOpposingCounsel(str(body, "opposingCounsel", c.getOpposingCounsel()));
        c.setBillingType(str(body, "billingType", c.getBillingType() == null ? "hourly" : c.getBillingType()));
        if (body.get("billingRate") != null) c.setBillingRate(new BigDecimal(String.valueOf(body.get("billingRate"))));
        if (body.get("leadAttorneyId") != null) c.setLeadAttorneyId(UUID.fromString(String.valueOf(body.get("leadAttorneyId"))));
        LocalDate accrual = TexasDocketRules.parseDate(body.get("accrualDate"));
        if (accrual != null) c.setAccrualDate(accrual);
        LocalDate discovery = TexasDocketRules.parseDate(body.get("discoveryDate"));
        if (discovery != null) c.setDiscoveryDate(discovery);
        LocalDate dob = TexasDocketRules.parseDate(body.get("dateOfBirth") != null ? body.get("dateOfBirth") : body.get("plaintiffDob"));
        if (dob != null) c.setPlaintiffDob(dob);
        LocalDate probate = TexasDocketRules.parseDate(body.get("probateOpened"));
        if (probate != null) c.setProbateOpened(probate);
        if (body.get("governmentalDefendant") != null) {
            c.setGovernmentalDefendant(TexasDocketRules.bool(body.get("governmentalDefendant")));
        }
        TexasDocketRules.Result docket = TexasDocketRules.compute(TexasDocketRules.factsFromCase(c));
        TexasDocketRules.stamp(c, docket);
        if (body.get("statuteOfLimitations") != null && !String.valueOf(body.get("statuteOfLimitations")).isBlank()) {
            c.setStatuteOfLimitations(LocalDate.parse(String.valueOf(body.get("statuteOfLimitations"))));
        }
        if (c.getCaseNumber() == null) {
            c.setCaseNumber("C-" + (1000 + cases.countByTenantId(tid()) + 1));
        }
        if (c.getDateOpened() == null) c.setDateOpened(LocalDate.now());
        c.setConflictChecked(true);
        c.setUpdatedAt(Instant.now());
        cases.save(c);
        return caseView(c);
    }

    @Transactional
    public Map<String, Object> changeCaseStatus(UUID id, String status) {
        LegalCase c = requireCase(id);
        c.setStatus(status);
        if ("closed".equals(status) || "settled".equals(status) || "archived".equals(status)) {
            c.setDateClosed(LocalDate.now());
        }
        c.setUpdatedAt(Instant.now());
        cases.save(c);
        return caseView(c);
    }

    public List<Map<String, Object>> contacts() {
        return contacts.findByTenantIdOrderByLastNameAsc(tid()).stream().map(this::contactView).toList();
    }

    @Transactional
    public Map<String, Object> saveContact(Map<String, Object> body) {
        Contact c = new Contact();
        c.setTenantId(tid());
        c.setType(str(body, "type", "other"));
        c.setFirstName(str(body, "firstName", null));
        c.setLastName(str(body, "lastName", null));
        c.setCompany(str(body, "company", null));
        c.setEmail(str(body, "email", null));
        c.setPhone(str(body, "phone", null));
        c.setTitle(str(body, "title", null));
        contacts.save(c);
        return contactView(c);
    }

    public List<Map<String, Object>> documents() {
        return documents.findByTenantIdOrderByCreatedAtDesc(tid()).stream().map(this::docView).toList();
    }

    @Transactional
    public Map<String, Object> upload(MultipartFile file, UUID caseId, UUID clientId, String category) throws IOException {
        UUID tenantId = tid();
        Path dir = uploadRoot.resolve(tenantId.toString());
        Files.createDirectories(dir);
        String stored = UUID.randomUUID() + "-" + file.getOriginalFilename();
        Path dest = dir.resolve(stored);
        file.transferTo(dest.toFile());
        DocumentFile doc = new DocumentFile();
        doc.setTenantId(tenantId);
        doc.setCaseId(caseId);
        doc.setClientId(clientId);
        doc.setUploadedBy(TenantContext.requireUser());
        doc.setName(file.getOriginalFilename());
        doc.setOriginalName(file.getOriginalFilename());
        doc.setCategory(category == null ? "other" : category);
        doc.setMimeType(file.getContentType());
        doc.setSizeBytes(file.getSize());
        doc.setStoragePath(dest.toString());
        documents.save(doc);
        return docView(doc);
    }

    public Resource download(UUID id) {
        DocumentFile doc = documents.findByIdAndTenantId(id, tid())
                .orElseThrow(() -> ApiException.notFound("Document not found"));
        if (doc.getStoragePath() == null || doc.getStoragePath().startsWith("seed://")) {
            throw ApiException.notFound("This demo file has metadata only. Upload a real document to download it.");
        }
        return new FileSystemResource(doc.getStoragePath());
    }

    public DocumentFile documentMeta(UUID id) {
        return documents.findByIdAndTenantId(id, tid()).orElseThrow(() -> ApiException.notFound("Document not found"));
    }

    public List<Map<String, Object>> upcomingEvents() {
        return events.findByTenantIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(tid(), Instant.now().minusSeconds(3600))
                .stream().limit(20).map(this::eventView).toList();
    }

    public List<Map<String, Object>> eventsBetween(Instant from, Instant to) {
        return events.findByTenantIdAndStartTimeBetweenOrderByStartTimeAsc(tid(), from, to)
                .stream().map(this::eventView).toList();
    }

    @Transactional
    public Map<String, Object> saveEvent(Map<String, Object> body) {
        CalendarEvent e = new CalendarEvent();
        e.setTenantId(tid());
        e.setCreatedBy(TenantContext.requireUser());
        e.setTitle(str(body, "title", "Untitled event"));
        e.setDescription(str(body, "description", null));
        e.setType(str(body, "type", "meeting"));
        e.setLocation(str(body, "location", null));
        if (body.get("caseId") != null) e.setCaseId(UUID.fromString(String.valueOf(body.get("caseId"))));
        e.setStartTime(Instant.parse(String.valueOf(body.get("startTime"))));
        if (body.get("endTime") != null) e.setEndTime(Instant.parse(String.valueOf(body.get("endTime"))));
        events.save(e);
        return eventView(e);
    }

    public List<Map<String, Object>> listTasks() {
        return tasks.findByTenantIdOrderByDueDateAsc(tid()).stream().map(this::taskView).toList();
    }

    @Transactional
    public Map<String, Object> saveTask(UUID id, Map<String, Object> body) {
        TaskItem t = id == null
                ? new TaskItem()
                : tasks.findByIdAndTenantId(id, tid()).orElseThrow(() -> ApiException.notFound("Task not found"));
        t.setTenantId(tid());
        t.setCreatedBy(TenantContext.requireUser());
        t.setTitle(str(body, "title", t.getTitle()));
        t.setDescription(str(body, "description", t.getDescription()));
        t.setStatus(str(body, "status", t.getStatus() == null ? "todo" : t.getStatus()));
        t.setPriority(str(body, "priority", t.getPriority() == null ? "medium" : t.getPriority()));
        if (body.get("caseId") != null) t.setCaseId(UUID.fromString(String.valueOf(body.get("caseId"))));
        if (body.get("assignedTo") != null) t.setAssignedTo(UUID.fromString(String.valueOf(body.get("assignedTo"))));
        if (body.get("dueDate") != null) t.setDueDate(Instant.parse(String.valueOf(body.get("dueDate"))));
        if ("completed".equals(t.getStatus())) t.setCompletedAt(Instant.now());
        tasks.save(t);
        return taskView(t);
    }

    @Transactional
    public Map<String, Object> addNote(UUID caseId, Map<String, Object> body) {
        requireCase(caseId);
        Note n = new Note();
        n.setTenantId(tid());
        n.setCaseId(caseId);
        n.setUserId(TenantContext.requireUser());
        n.setTitle(str(body, "title", "Note"));
        n.setBody(str(body, "body", ""));
        n.setType(str(body, "type", "note"));
        notes.save(n);
        return noteView(n);
    }

    public Map<String, Object> clientView(Client c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("type", c.getType());
        m.put("status", c.getStatus());
        m.put("firstName", c.getFirstName());
        m.put("lastName", c.getLastName());
        m.put("companyName", c.getCompanyName());
        m.put("displayName", c.displayName());
        m.put("email", c.getEmail());
        m.put("phone", c.getPhone());
        m.put("city", c.getCity());
        m.put("state", c.getState());
        m.put("source", c.getSource());
        m.put("portalEnabled", c.isPortalEnabled());
        m.put("notes", c.getNotes());
        m.put("createdAt", c.getCreatedAt());
        return m;
    }

    public Map<String, Object> caseView(LegalCase c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("clientId", c.getClientId());
        m.put("caseNumber", c.getCaseNumber());
        m.put("title", c.getTitle());
        m.put("description", c.getDescription());
        m.put("caseType", c.getCaseType());
        m.put("practiceArea", c.getPracticeArea());
        m.put("status", c.getStatus());
        m.put("priority", c.getPriority());
        m.put("courtName", c.getCourtName());
        m.put("judgeName", c.getJudgeName());
        m.put("opposingParty", c.getOpposingParty());
        m.put("opposingCounsel", c.getOpposingCounsel());
        m.put("leadAttorneyId", c.getLeadAttorneyId());
        m.put("billingType", c.getBillingType());
        m.put("billingRate", c.getBillingRate());
        m.put("dateOpened", c.getDateOpened());
        m.put("statuteOfLimitations", c.getStatuteOfLimitations());
        m.put("accrualDate", c.getAccrualDate());
        m.put("discoveryDate", c.getDiscoveryDate());
        m.put("plaintiffDob", c.getPlaintiffDob());
        m.put("probateOpened", c.getProbateOpened());
        m.put("governmentalDefendant", c.isGovernmentalDefendant());
        m.put("docketTrack", c.getDocketTrack());
        m.put("controllingKind", c.getControllingKind());
        m.put("solRuleId", c.getSolRuleId());
        m.put("solCitation", c.getSolCitation());
        m.put("solReason", c.getSolReason());
        m.put("docketClocks", JsonLists.objects(c.getDocketClocksJson()));
        clients.findById(c.getClientId()).ifPresent(cl -> m.put("clientName", cl.displayName()));
        return m;
    }

    private Map<String, Object> contactView(Contact c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("type", c.getType());
        m.put("firstName", c.getFirstName());
        m.put("lastName", c.getLastName());
        m.put("company", c.getCompany());
        m.put("title", c.getTitle());
        m.put("email", c.getEmail());
        m.put("phone", c.getPhone());
        m.put("name", ((c.getFirstName() == null ? "" : c.getFirstName()) + " " + (c.getLastName() == null ? "" : c.getLastName())).trim());
        return m;
    }

    private Map<String, Object> docView(DocumentFile d) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", d.getId());
        m.put("name", d.getName());
        m.put("category", d.getCategory());
        m.put("mimeType", d.getMimeType());
        m.put("sizeBytes", d.getSizeBytes());
        m.put("caseId", d.getCaseId());
        m.put("createdAt", d.getCreatedAt());
        m.put("privileged", d.isPrivileged());
        return m;
    }

    private Map<String, Object> eventView(CalendarEvent e) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", e.getId());
        m.put("title", e.getTitle());
        m.put("description", e.getDescription());
        m.put("type", e.getType());
        m.put("startTime", e.getStartTime());
        m.put("endTime", e.getEndTime());
        m.put("location", e.getLocation());
        m.put("caseId", e.getCaseId());
        m.put("status", e.getStatus());
        return m;
    }

    private Map<String, Object> taskView(TaskItem t) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", t.getId());
        m.put("title", t.getTitle());
        m.put("description", t.getDescription());
        m.put("status", t.getStatus());
        m.put("priority", t.getPriority());
        m.put("dueDate", t.getDueDate());
        m.put("caseId", t.getCaseId());
        m.put("assignedTo", t.getAssignedTo());
        return m;
    }

    private Map<String, Object> noteView(Note n) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", n.getId());
        m.put("title", n.getTitle());
        m.put("body", n.getBody());
        m.put("type", n.getType());
        m.put("createdAt", n.getCreatedAt());
        return m;
    }

    private LegalCase resolveCase(String ref) {
        if (ref == null || ref.isBlank()) {
            throw ApiException.badRequest("Missing matter id");
        }
        try {
            return requireCase(UUID.fromString(ref.trim()));
        } catch (IllegalArgumentException ignored) {
            return cases.findByTenantIdAndCaseNumberIgnoreCase(tid(), ref.trim())
                    .orElseThrow(() -> ApiException.notFound("Matter not found"));
        }
    }

    private LegalCase requireCase(UUID id) {
        return cases.findByIdAndTenantId(id, tid()).orElseThrow(() -> ApiException.notFound("Case not found"));
    }

    private UUID tid() {
        return TenantContext.requireTenant();
    }

    private String str(Map<String, Object> body, String key, String fallback) {
        Object v = body.get(key);
        return v == null ? fallback : String.valueOf(v);
    }
}
