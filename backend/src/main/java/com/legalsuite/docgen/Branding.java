package com.legalsuite.docgen;

import java.util.Map;

/** Everything a generated document needs from the firm: merge values, logo and accent colour. */
public record Branding(Map<String, String> values, byte[] logo, String colour) {
    public String firmName() {
        return values.getOrDefault("firm_name", "");
    }

    public String footer() {
        return values.getOrDefault("footer_text", "");
    }

    public boolean hasLogo() {
        return logo != null && logo.length > 0;
    }

    public float[] rgb() {
        String c = colour == null || !colour.matches("#[0-9a-fA-F]{6}") ? "#1f2a44" : colour;
        return new float[] {
            Integer.parseInt(c.substring(1, 3), 16) / 255f,
            Integer.parseInt(c.substring(3, 5), 16) / 255f,
            Integer.parseInt(c.substring(5, 7), 16) / 255f
        };
    }
}
