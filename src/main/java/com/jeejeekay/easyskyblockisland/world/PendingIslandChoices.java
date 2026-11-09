package com.jeejeekay.easyskyblockisland.world;

import com.jeejeekay.easyskyblockisland.config.EsiConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Island picks made on the Create World screen. The integrated server runs in the same process,
 * so it reads these when it writes the new world's settings.
 */
public final class PendingIslandChoices {

    private static final Map<String, String> CHOICES = new HashMap<>();

    private PendingIslandChoices() {
    }

    public static synchronized void set(String dimension, String template) {
        CHOICES.put(dimension, template);
    }

    public static synchronized String get(String dimension, String fallback) {
        return CHOICES.getOrDefault(dimension, fallback);
    }

    public static synchronized void clear() {
        CHOICES.clear();
    }

    /** Copies the picks into a new world's settings and forgets them. */
    public static synchronized void applyTo(EsiConfig config) {
        CHOICES.forEach((dimension, template) -> {
            EsiConfig.Dimension settings = config.dimensions.get(dimension);
            if (settings != null) {
                settings.island.template = template;
            }
        });
        CHOICES.clear();
    }
}
