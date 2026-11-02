package com.legalsuite.litigation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Litigation tracks, stages and stage-triggered deadline rules, loaded from litigation/tracks.json.
 * To add a track or change a period, edit that file; no code change is needed.
 */
@Component
public class LitigationCatalog {
    public record Stage(String id, String label, boolean side, boolean terminal, String after) {}
    public record Rule(String id, String trigger, String title, int amount, String unit, String source,
                       boolean needsReview, boolean diesNon, String note) {}
    public record Track(String id, String label, List<String> courtLevels, List<Stage> stages, List<Rule> rules, String note) {
        public Stage stage(String id) {
            return stages.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
        }
        public List<Stage> mainPath() {
            return stages.stream().filter(s -> !s.side()).toList();
        }
    }

    private final Map<String, Track> tracks = new LinkedHashMap<>();
    private final Map<String, Object> raw;

    @SuppressWarnings("unchecked")
    public LitigationCatalog() {
        try (InputStream in = LitigationCatalog.class.getResourceAsStream("/litigation/tracks.json")) {
            raw = new ObjectMapper().readValue(in, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read litigation/tracks.json", e);
        }
        for (Map<String, Object> t : (List<Map<String, Object>>) raw.get("tracks")) {
            List<Stage> stages = new ArrayList<>();
            for (Map<String, Object> s : (List<Map<String, Object>>) t.get("stages")) {
                stages.add(new Stage((String) s.get("id"), (String) s.get("label"), Boolean.TRUE.equals(s.get("side")),
                        Boolean.TRUE.equals(s.get("terminal")), (String) s.get("after")));
            }
            List<Rule> rules = new ArrayList<>();
            for (Map<String, Object> r : (List<Map<String, Object>>) t.get("rules")) {
                rules.add(new Rule((String) r.get("id"), (String) r.get("trigger"), (String) r.get("title"),
                        ((Number) r.get("amount")).intValue(), (String) r.get("unit"), (String) r.get("source"),
                        Boolean.TRUE.equals(r.get("needsReview")), Boolean.TRUE.equals(r.get("diesNon")), (String) r.get("note")));
            }
            Track track = new Track((String) t.get("id"), (String) t.get("label"), (List<String>) t.get("courtLevels"),
                    List.copyOf(stages), List.copyOf(rules), (String) t.get("note"));
            for (Rule r : rules) {
                if (track.stage(r.trigger()) == null) throw new IllegalStateException("Rule " + r.id() + " triggers on unknown stage " + r.trigger());
            }
            tracks.put(track.id(), track);
        }
    }

    public Track track(String id) {
        return id == null ? null : tracks.get(id);
    }

    public List<Track> tracks() {
        return List.copyOf(tracks.values());
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> courtLevels() {
        return (Map<String, String>) raw.get("courtLevels");
    }

    public String disclaimer() {
        return String.valueOf(raw.get("disclaimer"));
    }

    /**
     * Whether a matter whose history so far is {@code history} (stage ids, oldest first) may move to {@code next}.
     * Main-path stages only move forward (skipping is allowed). Side stages open once their "after" stage is reached.
     * A terminal stage ends the track. Returns null when allowed, otherwise the reason.
     */
    public static String transitionProblem(Track t, List<String> history, String next) {
        Stage target = t.stage(next);
        if (target == null) return "Unknown stage \"" + next + "\" for " + t.label() + ".";
        List<Stage> main = t.mainPath();
        int reached = -1;
        String last = history.isEmpty() ? null : history.get(history.size() - 1);
        for (String h : history) {
            Stage s = t.stage(h);
            if (s == null) continue;
            if (s.terminal()) return "This matter already reached " + s.label() + ". No further stages on this track.";
            if (!s.side()) reached = Math.max(reached, main.indexOf(s));
        }
        if (next.equals(last)) return "The matter is already at " + target.label() + ".";
        if (target.side()) {
            if (target.after() == null) return null;
            int need = main.indexOf(t.stage(target.after()));
            return reached >= need ? null : target.label() + " is only possible after " + t.stage(target.after()).label() + ".";
        }
        int idx = main.indexOf(target);
        if (idx <= reached) return "Stages only move forward. The matter is already past " + target.label() + ".";
        return null;
    }
}
