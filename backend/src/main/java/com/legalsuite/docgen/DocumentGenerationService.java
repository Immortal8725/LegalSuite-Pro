package com.legalsuite.docgen;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.Ids;
import com.legalsuite.common.Roles;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.DocumentFile;
import com.legalsuite.domain.DocumentTemplate;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.MatterFieldValue;
import com.legalsuite.domain.Tenant;
import com.legalsuite.mail.MailGateway;
import com.legalsuite.mail.SmtpProperties;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.DocumentFileRepository;
import com.legalsuite.repo.DocumentTemplateRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.MatterFieldValueRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.service.AuditService;
import com.legalsuite.service.SignatureService;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Firm-branded document generation from the system library and the firm's own Word templates. */
@Service
public class DocumentGenerationService {
    static final DateTimeFormatter SA_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final long MAX_TEMPLATE_BYTES = 5L * 1024 * 1024;

    private final TemplateLibrary library;
    private final DocumentTemplateRepository firmTemplates;
    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final LegalCaseRepository cases;
    private final ClientRepository clients;
    private final DocumentFileRepository documents;
    private final MatterFieldValueRepository remembered;
    private final SignatureService signatures;
    private final MailGateway mail;
    private final SmtpProperties smtp;
    private final AuditService audit;
    private final BrandingService branding;
    private final Path uploadRoot;

    public DocumentGenerationService(TemplateLibrary library, DocumentTemplateRepository firmTemplates, TenantRepository tenants,
                                     AppUserRepository users, LegalCaseRepository cases, ClientRepository clients,
                                     DocumentFileRepository documents, MatterFieldValueRepository remembered,
                                     SignatureService signatures, MailGateway mail, SmtpProperties smtp, AuditService audit,
                                     BrandingService branding,
                                     @Value("${legalsuite.upload-dir:./uploads}") String uploadDir) {
        this.library = library;
        this.firmTemplates = firmTemplates;
        this.tenants = tenants;
        this.users = users;
        this.cases = cases;
        this.clients = clients;
        this.documents = documents;
        this.remembered = remembered;
        this.signatures = signatures;
        this.mail = mail;
        this.smtp = smtp;
        this.audit = audit;
        this.branding = branding;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath();
    }

    /** A template as generation sees it: system or firm, layout or Word. */
    record Source(String id, String name, String category, String description, boolean system,
                  JsonNode layout, byte[] docx, Set<String> fields) {}

    // ---------------------------------------------------------------- library

    public List<Map<String, Object>> library() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TemplateLibrary.SystemTemplate t : library.all()) {
            out.add(summary(t.id(), t.name(), t.category(), t.description(), true, t.format(), t.fields(), null));
        }
        for (DocumentTemplate t : firmTemplates.findByTenantIdOrderByNameAsc(tid())) {
            if (!"docx".equals(t.getSourceFormat())) continue;
            Set<String> keys = DocxTemplateEngine.fieldKeys(read(t));
            out.add(summary(t.getId().toString(), t.getName(), t.getCategory(), t.getDescription(), false, "docx", keys, t.getCreatedAt()));
        }
        return out;
    }

    public List<FieldCatalog.Field> fieldReference() {
        return FieldCatalog.FIELDS;
    }

    private Map<String, Object> summary(String id, String name, String category, String description, boolean system,
                                        String format, Set<String> fields, Instant created) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("category", category);
        m.put("description", description == null ? "" : description);
        m.put("system", system);
        m.put("format", format);
        m.put("fieldCount", fields.size());
        m.put("fillInCount", fields.stream().filter(k -> !FieldCatalog.SYSTEM.contains(k)).count());
        if (created != null) m.put("createdAt", created);
        return m;
    }

    /** Fields for the generate form, with what Owlaw already knows filled in. */
    public Map<String, Object> form(String id, UUID caseId) {
        Source src = source(id);
        Map<String, String> values = context(caseId, null, Map.of());
        List<Map<String, Object>> fields = new ArrayList<>();
        for (String key : src.fields()) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("key", key);
            f.put("label", FieldCatalog.label(key));
            f.put("group", FieldCatalog.group(key));
            f.put("system", FieldCatalog.SYSTEM.contains(key));
            f.put("value", TokenMerge.LOGO.equals(key) ? (branding.current().hasLogo() ? "(logo)" : "") : values.getOrDefault(key, ""));
            fields.add(f);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", src.id());
        out.put("name", src.name());
        out.put("category", src.category());
        out.put("system", src.system());
        out.put("fields", fields);
        return out;
    }

    // ---------------------------------------------------------------- generate

    public byte[] preview(String id, Map<String, Object> body) {
        Source src = source(id);
        UUID caseId = Ids.optUuid(body.get("caseId"), "matter");
        Map<String, String> values = context(caseId, Ids.optUuid(body.get("clientId"), "client"), stringMap(body.get("values")));
        return pdf(src, values, branding.current(values));
    }

    @Transactional
    public Map<String, Object> generate(String id, Map<String, Object> body) {
        Source src = source(id);
        UUID caseId = Ids.optUuid(body.get("caseId"), "matter");
        UUID clientId = Ids.optUuid(body.get("clientId"), "client");
        Map<String, String> entered = stringMap(body.get("values"));
        Map<String, String> values = context(caseId, clientId, entered);
        Branding brand = branding.current(values);
        LegalCase matter = caseId == null ? null : cases.findByIdAndTenantId(caseId, tid()).orElse(null);
        if (clientId == null && matter != null) clientId = matter.getClientId();

        List<String> formats = new ArrayList<>();
        if (body.get("formats") instanceof List<?> list) list.forEach(f -> formats.add(String.valueOf(f).toLowerCase(Locale.ROOT)));
        if (formats.isEmpty()) formats.addAll(List.of("pdf", "docx"));

        String base = src.name() + (matter != null && matter.getCaseNumber() != null ? " - " + matter.getCaseNumber() : "");
        base = base.replaceAll("[\\\\/:*?\"<>|]", "-");
        List<Map<String, Object>> saved = new ArrayList<>();
        List<MailGateway.Attachment> attachments = new ArrayList<>();
        byte[] pdfBytes = null;
        for (String fmt : formats) {
            byte[] bytes;
            String mime;
            if ("pdf".equals(fmt)) {
                bytes = pdfBytes = pdf(src, values, brand);
                mime = "application/pdf";
            } else if ("docx".equals(fmt)) {
                bytes = docx(src, values, brand);
                mime = DOCX_MIME;
            } else {
                throw ApiException.badRequest("Formats are pdf and docx.");
            }
            DocumentFile doc = store(base + "." + fmt, mime, bytes, caseId, clientId);
            saved.add(docView(doc));
            attachments.add(new MailGateway.Attachment(doc.getName(), mime, bytes));
        }
        if (caseId != null) rememberValues(caseId, entered);
        audit.record("document.generate", "template", src.id(), src.name() + " (" + String.join(",", formats) + ")");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("template", src.name());
        out.put("documents", saved);

        if (body.get("sign") instanceof Map<?, ?> sign && sign.get("signerEmail") != null
                && !String.valueOf(sign.get("signerEmail")).isBlank()) {
            Map<String, Object> req = new HashMap<>();
            if (caseId != null) req.put("caseId", caseId.toString());
            if (clientId != null) req.put("clientId", clientId.toString());
            req.put("purpose", "document");
            req.put("title", src.name());
            req.put("documentBody", plainText(src, values));
            req.put("signerName", String.valueOf(sign.get("signerName") == null ? values.getOrDefault("client_name", "Signer") : sign.get("signerName")));
            req.put("signerEmail", String.valueOf(sign.get("signerEmail")));
            out.put("signature", signatures.create(req));
        }
        if (body.get("email") instanceof Map<?, ?> email && email.get("to") != null && !String.valueOf(email.get("to")).isBlank()) {
            out.put("email", sendEmail(String.valueOf(email.get("to")).trim(), email, src, brand, attachments));
        }
        if (pdfBytes != null) out.put("pdfPages", pageCount(pdfBytes));
        return out;
    }

    private Map<String, Object> sendEmail(String to, Map<?, ?> email, Source src, Branding brand, List<MailGateway.Attachment> attachments) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("to", to);
        if (!to.matches("[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;]+")) throw ApiException.badRequest("Enter one valid email address.");
        if (!mail.configured()) {
            result.put("sent", false);
            result.put("reason", mail.unconfiguredReason());
            return result;
        }
        String subject = email.get("subject") == null ? src.name() + " | " + brand.firmName() : String.valueOf(email.get("subject"));
        String message = email.get("message") == null
                ? "Good day,\n\nPlease find the attached " + src.name().toLowerCase(Locale.ROOT) + ".\n\nKind regards\n" + brand.footer()
                : String.valueOf(email.get("message"));
        mail.send(smtp.getFrom(), to, subject, message, attachments);
        audit.record("document.email", "template", src.id(), to);
        result.put("sent", true);
        return result;
    }

    byte[] pdf(Source src, Map<String, String> values, Branding brand) {
        if (src.layout() != null) return LayoutPdfRenderer.render(src.layout(), values, brand);
        byte[] merged = DocxTemplateEngine.merge(src.docx(), values, brand);
        boolean logoInBody = DocxTemplateEngine.paragraphs(merged).stream().anyMatch(DocxTemplateEngine.Para::logo);
        return FlowPdfRenderer.render(DocxTemplateEngine.paragraphs(merged), values, brand, !logoInBody);
    }

    byte[] docx(Source src, Map<String, String> values, Branding brand) {
        if (src.layout() != null) return LayoutDocxRenderer.render(src.layout(), values, brand);
        return DocxTemplateEngine.merge(src.docx(), values, brand);
    }

    private String plainText(Source src, Map<String, String> values) {
        StringBuilder sb = new StringBuilder();
        if (src.layout() != null) {
            for (JsonNode p : src.layout().get("pages")) {
                for (JsonNode l : p.get("lines")) {
                    StringBuilder row = new StringBuilder();
                    for (JsonNode s : l.get("spans")) row.append(TokenMerge.stripLogo(TokenMerge.merge(s.get("text").asText(), values)));
                    if (!row.toString().isBlank()) sb.append(row.toString().trim()).append('\n');
                }
                sb.append('\n');
            }
        } else {
            for (DocxTemplateEngine.Para p : DocxTemplateEngine.paragraphs(DocxTemplateEngine.merge(src.docx(), values, branding.current(values)))) {
                sb.append(p.text()).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static int pageCount(byte[] pdf) {
        try (var d = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            return d.getNumberOfPages();
        } catch (IOException e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- firm templates

    @Transactional
    public Map<String, Object> upload(MultipartFile file, String name, String category, String description) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a Word (.docx) file.");
        if (file.getSize() > MAX_TEMPLATE_BYTES) throw ApiException.badRequest("Templates can be up to 5 MB.");
        String original = file.getOriginalFilename() == null ? "template.docx" : file.getOriginalFilename();
        if (!original.toLowerCase(Locale.ROOT).endsWith(".docx")) throw ApiException.badRequest("Upload a .docx file. Older .doc files must be saved as .docx first.");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') throw ApiException.badRequest("That file is not a readable Word (.docx) document.");
        try {
            bytes = DocxTemplateEngine.normalise(bytes);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        Set<String> keys = DocxTemplateEngine.fieldKeys(bytes);
        DocumentTemplate t = new DocumentTemplate();
        t.setTenantId(tid());
        t.setName(name == null || name.isBlank() ? original.replaceAll("(?i)\\.docx$", "") : name.trim());
        t.setCategory(category == null || category.isBlank() ? "Firm templates" : category.trim());
        t.setDescription(description == null ? "" : description.trim());
        t.setSourceFormat("docx");
        t.setBody("");
        try {
            t.setStoragePath(storage.put(tid() + "/templates/" + UUID.randomUUID() + ".docx", bytes,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        firmTemplates.save(t);
        audit.record("template.upload", "template", t.getId().toString(), t.getName());
        Map<String, Object> out = summary(t.getId().toString(), t.getName(), t.getCategory(), t.getDescription(), false, "docx", keys, t.getCreatedAt());
        out.put("fields", keys);
        out.put("unknownFields", keys.stream().filter(k -> !FieldCatalog.SYSTEM.contains(k)).toList());
        return out;
    }

    @Transactional
    public Map<String, Object> deleteFirmTemplate(String id) {
        if (id.startsWith("sys-")) throw ApiException.badRequest("System templates cannot be deleted.");
        Roles.requireTenantAdmin("Only a firm owner or admin can remove firm templates.");
        DocumentTemplate t = firmTemplates.findByIdAndTenantId(parseId(id), tid())
                .filter(x -> "docx".equals(x.getSourceFormat()))
                .orElseThrow(() -> ApiException.notFound("Template not found"));
        firmTemplates.delete(t);
        audit.record("template.delete", "template", t.getId().toString(), t.getName());
        return Map.of("deleted", true);
    }

    Source source(String id) {
        if (id == null) throw ApiException.notFound("Template not found");
        if (id.startsWith("sys-")) {
            TemplateLibrary.SystemTemplate t = library.find(id).orElseThrow(() -> ApiException.notFound("Template not found"));
            return new Source(t.id(), t.name(), t.category(), t.description(), true, t.layout(), t.docx(), t.fields());
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("Template not found");
        }
        DocumentTemplate t = firmTemplates.findByIdAndTenantId(uuid, tid())
                .filter(x -> "docx".equals(x.getSourceFormat()))
                .orElseThrow(() -> ApiException.notFound("Template not found"));
        byte[] bytes = read(t);
        return new Source(t.getId().toString(), t.getName(), t.getCategory(), t.getDescription(), false, null, bytes,
                DocxTemplateEngine.fieldKeys(bytes));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.legalsuite.storage.FileStorage storage;

    private byte[] read(DocumentTemplate t) {
        try {
            return storage.read(t.getStoragePath());
        } catch (IOException e) {
            throw ApiException.notFound("The template file is missing. Upload it again.");
        }
    }

    // ---------------------------------------------------------------- values

    /** Firm, attorney, matter and remembered values, then whatever the user typed (which wins). */
    Map<String, String> context(UUID caseId, UUID clientId, Map<String, String> entered) {
        Map<String, String> v = new HashMap<>(branding.values());
        v.put("date", LocalDate.now().format(SA_DATE));
        v.put("letter_date", LocalDate.now().format(SA_DATE));
        LegalCase matter = null;
        if (caseId != null) {
            matter = cases.findByIdAndTenantId(caseId, tid()).orElseThrow(() -> ApiException.notFound("Matter not found"));
            put(v, "matter_ref", matter.getCaseNumber());
            put(v, "file_number", matter.getCaseNumber());
            put(v, "matter_title", matter.getTitle());
            put(v, "court_name", matter.getCourtName());
            put(v, "court_case_number", matter.getCourtCaseNumber());
            put(v, "opposing_party", matter.getOpposingParty());
            if (matter.getAccrualDate() != null && isRaf(matter)) put(v, "accident_date", matter.getAccrualDate().format(SA_DATE));
            if (matter.getClientId() != null && clientId == null) clientId = matter.getClientId();
        }
        if (clientId != null) {
            Client c = clients.findByIdAndTenantId(clientId, tid()).orElseThrow(() -> ApiException.notFound("Client not found"));
            put(v, "client_name", c.displayName());
            put(v, "client_email", c.getEmail());
            put(v, "client_phone", c.getMobile() != null && !c.getMobile().isBlank() ? c.getMobile() : c.getPhone());
            List<String> addr = new ArrayList<>();
            for (String s : new String[] {c.getAddressLine1(), c.getCity(), c.getState(), c.getZip()}) if (s != null && !s.isBlank()) addr.add(s);
            put(v, "client_address", String.join(", ", addr));
        }
        if (matter != null) {
            for (MatterFieldValue mv : remembered.findByTenantIdAndCaseId(tid(), matter.getId())) put(v, mv.getFieldKey(), mv.getFieldValue());
        }
        entered.forEach((k, val) -> {
            if (val != null && !TokenMerge.LOGO.equals(k)) v.put(k, val);
        });
        if (entered.get("footer_text") == null && branding.footerIsDefault()
                && (entered.containsKey("attorney_name") || entered.containsKey("attorney_title"))) {
            v.put("footer_text", BrandingService.defaultFooter(v.get("attorney_name"), v.get("attorney_title")));
        }
        return v;
    }

    private static boolean isRaf(LegalCase c) {
        String s = ((c.getPracticeArea() == null ? "" : c.getPracticeArea()) + " " + (c.getCaseType() == null ? "" : c.getCaseType())).toLowerCase(Locale.ROOT);
        return c.isRafClaimLodged() || s.contains("raf") || s.contains("road accident") || s.contains("motor vehicle");
    }

    private void rememberValues(UUID caseId, Map<String, String> entered) {
        Set<String> skip = new LinkedHashSet<>(FieldCatalog.SYSTEM);
        skip.addAll(List.of("letter_date", "signed_day", "signed_month", "signed_year", "letter_day", "letter_month"));
        entered.forEach((k, val) -> {
            if (val == null || val.isBlank() || skip.contains(k) || k.length() > 80) return;
            MatterFieldValue mv = remembered.findByTenantIdAndCaseIdAndFieldKey(tid(), caseId, k).orElseGet(() -> {
                MatterFieldValue n = new MatterFieldValue();
                n.setTenantId(tid());
                n.setCaseId(caseId);
                n.setFieldKey(k);
                return n;
            });
            mv.setFieldValue(val.length() > 2000 ? val.substring(0, 2000) : val);
            mv.setUpdatedAt(Instant.now());
            remembered.save(mv);
        });
    }

    private DocumentFile store(String name, String mime, byte[] bytes, UUID caseId, UUID clientId) {
        try {
            String ref = storage.put(tid() + "/" + UUID.randomUUID() + "-" + name.replaceAll("[^A-Za-z0-9._ -]", "_"), bytes, mime);
            DocumentFile doc = new DocumentFile();
            doc.setTenantId(tid());
            doc.setCaseId(caseId);
            doc.setClientId(clientId);
            doc.setUploadedBy(TenantContext.requireUser());
            doc.setName(name);
            doc.setOriginalName(name);
            doc.setCategory("generated");
            doc.setMimeType(mime);
            doc.setSizeBytes(bytes.length);
            doc.setStoragePath(ref);
            documents.save(doc);
            return doc;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, Object> docView(DocumentFile d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("name", d.getName());
        m.put("mimeType", d.getMimeType());
        m.put("sizeBytes", d.getSizeBytes());
        m.put("caseId", d.getCaseId());
        m.put("downloadUrl", "/api/v1/documents/" + d.getId() + "/download");
        return m;
    }

    private static void put(Map<String, String> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v);
    }

    static Map<String, String> stringMap(Object o) {
        Map<String, String> out = new HashMap<>();
        if (o instanceof Map<?, ?> m) {
            m.forEach((k, v) -> {
                if (k != null && v != null) out.put(String.valueOf(k).toLowerCase(Locale.ROOT), String.valueOf(v));
            });
        }
        return out;
    }

    private static UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("Template not found");
        }
    }

    private UUID tid() {
        return TenantContext.requireTenant();
    }

    @SuppressWarnings("unused")
    private AppUser me() {
        return users.findById(TenantContext.requireUser()).orElseThrow(() -> ApiException.unauthorized("Sign in again."));
    }

    @SuppressWarnings("unused")
    private Tenant firm() {
        return tenants.findById(tid()).orElseThrow(() -> ApiException.notFound("Firm not found"));
    }
}
