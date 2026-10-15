package com.legalsuite.docgen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/** The system templates every firm gets, loaded once from the classpath. */
@Component
public class TemplateLibrary {
    /** A system template. Exactly one of layout (PDF-derived) or docx is set. */
    public record SystemTemplate(String key, String name, String category, String description,
                                 JsonNode layout, byte[] docx, Set<String> fields) {
        public String id() {
            return "sys-" + key;
        }

        public String format() {
            return layout != null ? "layout" : "docx";
        }
    }

    private final Map<String, SystemTemplate> templates = new LinkedHashMap<>();

    public TemplateLibrary() {
        ObjectMapper json = new ObjectMapper();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            List<SystemTemplate> found = new ArrayList<>();
            for (Resource r : resolver.getResources("classpath:doc-templates/system/*.json")) {
                String file = r.getFilename();
                if (file == null) continue;
                try (InputStream in = r.getInputStream()) {
                    JsonNode node = json.readTree(in);
                    if (file.endsWith(".meta.json")) {
                        String key = node.get("key").asText();
                        Resource docx = resolver.getResource("classpath:doc-templates/system/" + key + ".docx");
                        byte[] bytes;
                        try (InputStream d = docx.getInputStream()) {
                            bytes = d.readAllBytes();
                        }
                        found.add(new SystemTemplate(key, node.get("name").asText(), node.get("category").asText(),
                                node.path("description").asText(""), null, bytes, DocxTemplateEngine.fieldKeys(bytes)));
                    } else {
                        Set<String> keys = new LinkedHashSet<>();
                        for (JsonNode p : node.get("pages")) {
                            for (JsonNode l : p.get("lines")) {
                                for (JsonNode s : l.get("spans")) keys.addAll(TokenMerge.keys(s.get("text").asText()));
                            }
                        }
                        found.add(new SystemTemplate(node.get("key").asText(), node.get("name").asText(),
                                node.get("category").asText(), node.path("description").asText(""), node, null, keys));
                    }
                }
            }
            found.sort(Comparator.comparing(SystemTemplate::category).thenComparing(SystemTemplate::name));
            found.forEach(t -> templates.put(t.id(), t));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<SystemTemplate> all() {
        return List.copyOf(templates.values());
    }

    public Optional<SystemTemplate> find(String id) {
        return Optional.ofNullable(templates.get(id));
    }
}
