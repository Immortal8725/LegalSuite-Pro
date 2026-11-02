package com.legalsuite.litigation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Plain-language, client-facing text for each litigation stage, loaded from litigation/summaries/{locale}.json.
 * A track-specific entry wins over the shared "common" entry for the same stage id. Only en is written today;
 * af.json or zu.json can be dropped in later (written by a person, not machine-translated) without code changes.
 */
@Component
public class ClientSummaries {
    public static final List<String> LOCALES = List.of("en", "af", "zu");
    private final Map<String, Map<String, Object>> byLocale = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    public ClientSummaries() {
        for (String loc : LOCALES) {
            try (InputStream in = ClientSummaries.class.getResourceAsStream("/litigation/summaries/" + loc + ".json")) {
                if (in == null) continue;
                byLocale.put(loc, new ObjectMapper().readValue(in, Map.class));
            } catch (Exception e) {
                throw new IllegalStateException("Could not read litigation/summaries/" + loc + ".json", e);
            }
        }
        if (!byLocale.containsKey("en")) throw new IllegalStateException("litigation/summaries/en.json is missing");
    }

    private Map<String, Object> pack(String locale) {
        Map<String, Object> p = locale == null ? null : byLocale.get(locale);
        return p == null ? byLocale.get("en") : p;
    }

    /** {means, next, needFromYou?} or null when the stage has no text. Falls back to English. */
    @SuppressWarnings("unchecked")
    public Map<String, String> stage(String locale, String track, String stage) {
        if (stage == null) return null;
        for (Map<String, Object> p : List.of(pack(locale), byLocale.get("en"))) {
            Map<String, Object> tracks = (Map<String, Object>) p.getOrDefault("tracks", Map.of());
            Map<String, Object> t = track == null ? null : (Map<String, Object>) tracks.get(track);
            Object hit = t == null ? null : t.get(stage);
            if (hit == null) hit = ((Map<String, Object>) p.getOrDefault("common", Map.of())).get(stage);
            if (hit != null) return (Map<String, String>) hit;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> labels(String locale) {
        return (Map<String, String>) pack(locale).get("labels");
    }

    public String disclaimer(String locale) {
        return String.valueOf(pack(locale).get("disclaimer"));
    }
}
