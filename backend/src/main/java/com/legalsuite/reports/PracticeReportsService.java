package com.legalsuite.reports;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.DocumentFile;
import com.legalsuite.domain.Invoice;
import com.legalsuite.domain.InvoicePayment;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.TimeEntry;
import com.legalsuite.importer.Csv;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.DocumentFileRepository;
import com.legalsuite.repo.InvoicePaymentRepository;
import com.legalsuite.repo.InvoiceRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.TimeEntryRepository;
import com.legalsuite.service.AuditService;
import com.legalsuite.storage.FileStorage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.springframework.stereotype.Service;

/** Practice dashboard, accounting exports (Xero, Sage) and court bundles. */
@Service
public class PracticeReportsService {
    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private final InvoiceRepository invoices;
    private final InvoicePaymentRepository payments;
    private final TimeEntryRepository times;
    private final LegalCaseRepository cases;
    private final ClientRepository clients;
    private final AppUserRepository users;
    private final DocumentFileRepository documents;
    private final FileStorage storage;
    private final AuditService audit;

    public PracticeReportsService(InvoiceRepository invoices, InvoicePaymentRepository payments, TimeEntryRepository times,
                                  LegalCaseRepository cases, ClientRepository clients, AppUserRepository users,
                                  DocumentFileRepository documents, FileStorage storage, AuditService audit) {
        this.invoices = invoices;
        this.payments = payments;
        this.times = times;
        this.cases = cases;
        this.clients = clients;
        this.users = users;
        this.documents = documents;
        this.storage = storage;
        this.audit = audit;
    }

    private static UUID tid() { return TenantContext.requireTenant(); }

    static BigDecimal due(Invoice i) {
        return nz(i.getTotal()).subtract(nz(i.getAmountPaid())).subtract(nz(i.getWriteOffAmount()));
    }

    static BigDecimal nz(BigDecimal b) { return b == null ? BigDecimal.ZERO : b; }

    // ---- Dashboard --------------------------------------------------------------------------------

    public Map<String, Object> dashboard() {
        UUID tid = tid();
        LocalDate today = LocalDate.now(SAST);
        Map<UUID, String> userNames = new HashMap<>();
        for (AppUser u : users.findByTenantIdOrderByLastNameAsc(tid)) userNames.put(u.getId(), (nzs(u.getFirstName()) + " " + nzs(u.getLastName())).trim());
        BigDecimal wip = BigDecimal.ZERO;
        long wipMinutes = 0;
        for (TimeEntry t : times.findByTenantIdAndBilledFalseAndBillableTrue(tid)) { wip = wip.add(nz(t.getTotalAmount())); wipMinutes += t.getDurationMinutes(); }
        Map<String, BigDecimal> ageing = new LinkedHashMap<>();
        for (String k : List.of("current", "31-60", "61-90", "90+")) ageing.put(k, BigDecimal.ZERO);
        TreeMap<String, BigDecimal[]> monthly = new TreeMap<>();
        YearMonth start = YearMonth.from(today).minusMonths(11);
        for (int i = 0; i < 12; i++) monthly.put(start.plusMonths(i).toString(), new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
        List<Map<String, Object>> topDebtors = new ArrayList<>();
        Map<UUID, BigDecimal> byClient = new HashMap<>();
        for (Invoice inv : invoices.findByTenantIdOrderByDateIssuedDesc(tid)) {
            if ("draft".equals(inv.getStatus()) || "void".equals(inv.getStatus())) continue;
            String ym = YearMonth.from(inv.getDateIssued()).toString();
            if (monthly.containsKey(ym)) monthly.get(ym)[0] = monthly.get(ym)[0].add(nz(inv.getTotal()));
            for (InvoicePayment p : payments.findByTenantIdAndInvoiceIdOrderByPaidAtAscCreatedAtAsc(tid, inv.getId())) {
                String pm = YearMonth.from(p.getPaidAt()).toString();
                if (monthly.containsKey(pm)) monthly.get(pm)[1] = monthly.get(pm)[1].add(nz(p.getAmount()));
            }
            BigDecimal d = due(inv);
            if (d.signum() <= 0) continue;
            long age = ChronoUnit.DAYS.between(inv.getDateDue() != null ? inv.getDateDue() : inv.getDateIssued(), today);
            String bucket = age <= 30 ? "current" : age <= 60 ? "31-60" : age <= 90 ? "61-90" : "90+";
            ageing.merge(bucket, d, BigDecimal::add);
            if (inv.getClientId() != null) byClient.merge(inv.getClientId(), d, BigDecimal::add);
        }
        byClient.entrySet().stream().sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed()).limit(5).forEach(e -> {
            Client c = clients.findByIdAndTenantId(e.getKey(), tid).orElse(null);
            topDebtors.add(Map.of("client", c == null ? "" : c.displayName(), "owing", e.getValue()));
        });
        Map<String, Map<String, Object>> perAttorney = new TreeMap<>();
        LocalDate since = today.minusDays(90);
        for (TimeEntry t : times.findByTenantIdOrderByDateDesc(tid)) {
            if (t.getDate() == null || t.getDate().isBefore(since)) continue;
            String name = userNames.getOrDefault(t.getUserId(), "Unassigned");
            Map<String, Object> m = perAttorney.computeIfAbsent(name, k -> new LinkedHashMap<>(Map.of("attorney", k, "minutes", 0L, "billableValue", BigDecimal.ZERO)));
            m.put("minutes", (Long) m.get("minutes") + t.getDurationMinutes());
            if (t.isBillable()) m.put("billableValue", ((BigDecimal) m.get("billableValue")).add(nz(t.getTotalAmount())));
        }
        Map<String, Long> openByArea = new TreeMap<>();
        long open = 0;
        for (LegalCase c : cases.findByTenantIdOrderByUpdatedAtDesc(tid)) {
            if (List.of("closed", "settled", "archived").contains(String.valueOf(c.getStatus()))) continue;
            open++;
            openByArea.merge(c.getPracticeArea() == null || c.getPracticeArea().isBlank() ? "Other" : c.getPracticeArea(), 1L, Long::sum);
        }
        List<Map<String, Object>> months = new ArrayList<>();
        monthly.forEach((k, v) -> months.add(Map.of("month", k, "billed", v[0], "collected", v[1])));
        BigDecimal billed12 = months.stream().map(m -> (BigDecimal) m.get("billed")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal coll12 = months.stream().map(m -> (BigDecimal) m.get("collected")).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("wip", wip);
        out.put("wipHours", BigDecimal.valueOf(wipMinutes).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP));
        out.put("debtors", ageing.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        out.put("ageing", ageing);
        out.put("topDebtors", topDebtors);
        out.put("months", months);
        out.put("billed12", billed12);
        out.put("collected12", coll12);
        out.put("collectionRate", billed12.signum() == 0 ? null : coll12.multiply(BigDecimal.valueOf(100)).divide(billed12, 0, RoundingMode.HALF_UP));
        out.put("perAttorney90", new ArrayList<>(perAttorney.values()));
        out.put("openMatters", open);
        out.put("openByArea", openByArea);
        return out;
    }

    // ---- Accounting export ------------------------------------------------------------------------

    /**
     * format=xero: Xero "Import sales invoices" CSV (one line per invoice, tax "Standard Rate").
     * format=sage: Sage Business Cloud Accounting customer invoice lines, plus a customers header block.
     * Totals are ex VAT per line with VAT 15%, matching the invoice.
     */
    public String accountingCsv(String format, LocalDate from, LocalDate to, String accountCode) {
        if (from == null || to == null) throw ApiException.badRequest("Choose a period.");
        UUID tid = tid();
        String acct = accountCode == null || accountCode.isBlank() ? ("sage".equals(format) ? "1000" : "200") : accountCode.trim();
        StringBuilder sb = new StringBuilder();
        if ("xero".equals(format)) {
            sb.append("*ContactName,EmailAddress,*InvoiceNumber,Reference,*InvoiceDate,*DueDate,*Description,*Quantity,*UnitAmount,*AccountCode,*TaxType,Currency\r\n");
        } else if ("sage".equals(format)) {
            sb.append("Customer Name,Customer Email,Document Number,Reference,Document Date,Due Date,Description,Quantity,Unit Price Excl,VAT Type,VAT Amount,Total Incl,Sales Account\r\n");
        } else {
            throw ApiException.badRequest("Format is xero or sage.");
        }
        int n = 0;
        for (Invoice inv : invoices.findByTenantIdOrderByDateIssuedDesc(tid)) {
            if ("draft".equals(inv.getStatus()) || "void".equals(inv.getStatus())) continue;
            if (inv.getDateIssued().isBefore(from) || inv.getDateIssued().isAfter(to)) continue;
            Client c = inv.getClientId() == null ? null : clients.findByIdAndTenantId(inv.getClientId(), tid).orElse(null);
            String name = c == null ? "Unknown client" : c.displayName();
            String email = c == null || c.getEmail() == null ? "" : c.getEmail();
            String ref = "";
            if (inv.getCaseId() != null) ref = cases.findByIdAndTenantId(inv.getCaseId(), tid).map(LegalCase::getCaseNumber).orElse("");
            String due = (inv.getDateDue() == null ? inv.getDateIssued() : inv.getDateDue()).toString();
            String desc = "Professional fees and disbursements per invoice " + inv.getInvoiceNumber();
            if ("xero".equals(format)) {
                sb.append(String.join(",", Csv.escape(name), Csv.escape(email), Csv.escape(inv.getInvoiceNumber()), Csv.escape(ref),
                        inv.getDateIssued().toString(), due, Csv.escape(desc), "1", nz(inv.getSubtotal()).setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        acct, nz(inv.getTaxAmount()).signum() == 0 ? "No VAT" : "Standard Rate", "ZAR")).append("\r\n");
            } else {
                sb.append(String.join(",", Csv.escape(name), Csv.escape(email), Csv.escape(inv.getInvoiceNumber()), Csv.escape(ref),
                        inv.getDateIssued().toString(), due, Csv.escape(desc), "1", nz(inv.getSubtotal()).setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        nz(inv.getTaxAmount()).signum() == 0 ? "No VAT" : "Standard Rate (15%)", nz(inv.getTaxAmount()).setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        nz(inv.getTotal()).setScale(2, RoundingMode.HALF_UP).toPlainString(), acct)).append("\r\n");
            }
            n++;
        }
        audit.record("export.accounting", "invoice", format, n + " invoices " + from + ".." + to);
        return sb.toString();
    }

    // ---- Court bundle -----------------------------------------------------------------------------

    public List<Map<String, Object>> bundleCandidates(UUID caseId) {
        cases.findByIdAndTenantId(caseId, tid()).orElseThrow(() -> ApiException.notFound("Matter not found"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (DocumentFile d : documents.findByTenantIdAndCaseId(tid(), caseId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("name", d.getName());
            m.put("category", d.getCategory());
            m.put("pdf", "application/pdf".equals(d.getMimeType()) && d.getStoragePath() != null && !d.getStoragePath().startsWith("seed://"));
            m.put("createdAt", d.getCreatedAt());
            out.add(m);
        }
        return out;
    }

    /**
     * Body: {"title": "...", "sections": [{"name": "Pleadings", "documentIds": [..]}]}. Sections are lettered A, B, C;
     * every page is stamped "A-1", "A-2" (CaseLines style) and with a running page number. Index on page 1, bookmarks per document.
     */
    @SuppressWarnings("unchecked")
    public byte[] bundle(UUID caseId, Map<String, Object> body) {
        UUID tid = tid();
        LegalCase matter = cases.findByIdAndTenantId(caseId, tid).orElseThrow(() -> ApiException.notFound("Matter not found"));
        List<Map<String, Object>> sections = (List<Map<String, Object>>) body.getOrDefault("sections", List.of());
        if (sections.isEmpty()) throw ApiException.badRequest("Add at least one section with documents.");
        if (sections.size() > 26) throw ApiException.badRequest("At most 26 sections (A to Z).");
        String title = String.valueOf(body.getOrDefault("title", "Bundle")).trim();
        try (PDDocument out = new PDDocument()) {
            PDType1Font reg = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            record Entry(String section, String name, int start, int end, String sectionLetter, int firstLocal) {}
            List<Entry> index = new ArrayList<>();
            List<String> pageLabels = new ArrayList<>();
            PDDocumentOutline outline = new PDDocumentOutline();
            PDFMergerUtility merger = new PDFMergerUtility();
            int total = 0;
            int totalDocs = 0;
            // Reserve index pages: estimate 40 lines a page.
            int lines = 6;
            for (Map<String, Object> s : sections) lines += 2 + ((List<Object>) s.getOrDefault("documentIds", List.of())).size();
            int indexPages = Math.max(1, (int) Math.ceil(lines / 40.0));
            for (int i = 0; i < indexPages; i++) { out.addPage(new PDPage()); pageLabels.add(""); }
            total = indexPages;
            List<PDDocument> opened = new ArrayList<>();
            try {
                for (int si = 0; si < sections.size(); si++) {
                    Map<String, Object> s = sections.get(si);
                    String letter = String.valueOf((char) ('A' + si));
                    String sname = String.valueOf(s.getOrDefault("name", "Section " + letter));
                    int local = 0;
                    for (Object idObj : (List<Object>) s.getOrDefault("documentIds", List.of())) {
                        DocumentFile d = documents.findByIdAndTenantId(UUID.fromString(String.valueOf(idObj)), tid)
                                .orElseThrow(() -> ApiException.notFound("Document not found"));
                        if (!caseId.equals(d.getCaseId())) throw ApiException.badRequest(d.getName() + " is not on this matter.");
                        if (!"application/pdf".equals(d.getMimeType())) throw ApiException.badRequest(d.getName() + " is not a PDF. Convert it to PDF first.");
                        PDDocument src;
                        try { src = Loader.loadPDF(storage.read(d.getStoragePath())); } catch (Exception e) { throw ApiException.badRequest(d.getName() + " could not be opened as a PDF."); }
                        if (src.isEncrypted()) { src.close(); throw ApiException.badRequest(d.getName() + " is password protected."); }
                        opened.add(src);
                        int pages = src.getNumberOfPages();
                        int start = total + 1;
                        merger.appendDocument(out, src);
                        for (int p = 1; p <= pages; p++) pageLabels.add(letter + "-" + (local + p));
                        index.add(new Entry(sname, d.getName(), start, total + pages, letter, local + 1));
                        PDOutlineItem item = new PDOutlineItem();
                        item.setTitle(letter + ": " + d.getName());
                        item.setDestination(out.getPage(start - 1));
                        outline.addLast(item);
                        local += pages;
                        total += pages;
                        totalDocs++;
                    }
                }
                if (totalDocs == 0) throw ApiException.badRequest("Choose at least one PDF document.");
                out.getDocumentCatalog().setDocumentOutline(outline);
                // Index pages.
                int pi = 0;
                PDPageContentStream cs = new PDPageContentStream(out, out.getPage(0));
                float y = 740;
                cs.beginText(); cs.setFont(bold, 15); cs.newLineAtOffset(50, y); cs.showText(SimplePdf.clean(title)); cs.endText(); y -= 20;
                cs.beginText(); cs.setFont(reg, 10); cs.newLineAtOffset(50, y);
                cs.showText(SimplePdf.clean((matter.getCourtCaseNumber() != null ? "Case no " + matter.getCourtCaseNumber() + "  |  " : "") + matter.getTitle()));
                cs.endText(); y -= 26;
                cs.beginText(); cs.setFont(bold, 11); cs.newLineAtOffset(50, y); cs.showText("INDEX"); cs.endText(); y -= 18;
                String lastSection = null;
                for (Entry e : index) {
                    if (y < 70) { cs.close(); pi++; cs = new PDPageContentStream(out, out.getPage(pi)); y = 750; }
                    if (!e.section().equals(lastSection)) {
                        cs.beginText(); cs.setFont(bold, 10); cs.newLineAtOffset(50, y); cs.showText(SimplePdf.clean(e.sectionLetter() + "   " + e.section())); cs.endText();
                        y -= 15;
                        lastSection = e.section();
                    }
                    String pages = e.sectionLetter() + "-" + e.firstLocal() + " to " + e.sectionLetter() + "-" + (e.firstLocal() + e.end() - e.start());
                    String name = SimplePdf.clean(e.name());
                    if (name.length() > 70) name = name.substring(0, 67) + "...";
                    cs.beginText(); cs.setFont(reg, 9.5f); cs.newLineAtOffset(70, y); cs.showText(name); cs.endText();
                    cs.beginText(); cs.setFont(reg, 9.5f); cs.newLineAtOffset(440, y); cs.showText(pages + "  (p " + e.start() + ")"); cs.endText();
                    y -= 14;
                }
                cs.close();
                // Stamp every page.
                int n = out.getNumberOfPages();
                for (int i = 0; i < n; i++) {
                    PDPage page = out.getPage(i);
                    var box = page.getMediaBox();
                    try (PDPageContentStream st = new PDPageContentStream(out, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                        String label = i < pageLabels.size() ? pageLabels.get(i) : "";
                        String text = (label.isEmpty() ? "Index" : label) + "   |   " + (i + 1) + " of " + n;
                        float w = bold.getStringWidth(text) / 1000 * 9;
                        st.setNonStrokingColor(1f, 1f, 1f);
                        st.addRect(box.getUpperRightX() - w - 26, box.getUpperRightY() - 26, w + 12, 16);
                        st.fill();
                        st.setNonStrokingColor(0f, 0f, 0f);
                        st.beginText(); st.setFont(bold, 9); st.newLineAtOffset(box.getUpperRightX() - w - 20, box.getUpperRightY() - 21); st.showText(text); st.endText();
                    }
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                out.save(bos);
                audit.record("case.bundle", "case", caseId.toString(), totalDocs + " documents, " + n + " pages");
                return bos.toByteArray();
            } finally {
                for (PDDocument d : opened) try { d.close(); } catch (Exception ignored) { }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Bundle failed", e);
        }
    }

    private static String nzs(String s) { return s == null ? "" : s; }
}
