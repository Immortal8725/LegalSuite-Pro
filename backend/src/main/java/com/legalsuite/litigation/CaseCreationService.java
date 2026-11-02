package com.legalsuite.litigation;

import com.legalsuite.common.ApiException;
import com.legalsuite.litigation.LitigationCatalog.Track;
import com.legalsuite.service.PracticeService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opens a matter and, when asked, sets its litigation track and first stage in one transaction. */
@Service
public class CaseCreationService {
    private final PracticeService practice;
    private final LitigationService litigation;
    private final LitigationCatalog catalog;

    public CaseCreationService(PracticeService practice, LitigationService litigation, LitigationCatalog catalog) {
        this.practice = practice;
        this.litigation = litigation;
        this.catalog = catalog;
    }

    static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        String trackId = str(body.get("litigationTrack"));
        if ("none".equals(trackId)) trackId = null;
        String level = str(body.get("courtLevel"));
        String stage = str(body.get("initialStage"));
        String stageDate = str(body.get("initialStageDate"));
        if (trackId == null && stage != null) throw ApiException.badRequest("Choose a matter type before an initial stage.");
        if (trackId != null) {
            Track t = catalog.track(trackId);
            if (t == null) throw ApiException.badRequest("Unknown litigation track \"" + trackId + "\".");
            if (level != null && !t.courtLevels().contains(level)) {
                throw ApiException.badRequest(t.label() + " runs in: " + String.join(", ", t.courtLevels()) + ".");
            }
            if (stage != null) {
                String problem = LitigationCatalog.transitionProblem(t, List.of(), stage);
                if (problem != null) throw ApiException.badRequest(problem);
            }
        }
        Map<String, Object> clean = new LinkedHashMap<>(body);
        clean.remove("litigationTrack");
        clean.remove("courtLevel");
        clean.remove("litigationStage");
        clean.remove("initialStage");
        clean.remove("initialStageDate");
        Map<String, Object> created = practice.saveCase(null, clean);
        if (trackId == null) return created;
        UUID id = UUID.fromString(String.valueOf(created.get("id")));
        Map<String, Object> tb = new LinkedHashMap<>();
        tb.put("track", trackId);
        if (level != null) tb.put("courtLevel", level);
        litigation.setTrack(id, tb);
        List<?> made = List.of();
        if (stage != null) {
            Map<String, Object> sb = new LinkedHashMap<>();
            sb.put("stage", stage);
            if (stageDate != null) sb.put("date", stageDate);
            Object m = litigation.addStage(id, sb).get("createdDeadlines");
            if (m instanceof List<?> l) made = l;
        }
        Map<String, Object> out = new LinkedHashMap<>(practice.getCase(id.toString()));
        out.put("createdDeadlines", made);
        return out;
    }
}
