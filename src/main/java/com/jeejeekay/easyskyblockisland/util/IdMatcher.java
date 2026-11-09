package com.jeejeekay.easyskyblockisland.util;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Matches registry entries against a list of exact ids, {@code modid:*} wildcards and {@code #tags}. */
public final class IdMatcher {

    private final Set<ResourceLocation> ids = new HashSet<>();
    private final Set<String> namespaces = new HashSet<>();
    private final List<ResourceLocation> tags = new ArrayList<>();

    private IdMatcher() {
    }

    public static IdMatcher of(List<String> entries) {
        IdMatcher matcher = new IdMatcher();
        for (String raw : entries) {
            if (raw == null) {
                continue;
            }
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            if (entry.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(entry.substring(1));
                if (tag != null) {
                    matcher.tags.add(tag);
                }
            } else if (entry.endsWith(":*")) {
                matcher.namespaces.add(entry.substring(0, entry.length() - 2));
            } else {
                ResourceLocation id = ResourceLocation.tryParse(entry);
                if (id != null) {
                    matcher.ids.add(id);
                }
            }
        }
        return matcher;
    }

    public boolean isEmpty() {
        return this.ids.isEmpty() && this.namespaces.isEmpty() && this.tags.isEmpty();
    }

    public boolean matchesId(ResourceLocation id) {
        return id != null && (this.ids.contains(id) || this.namespaces.contains(id.getNamespace()));
    }

    public <T> boolean matches(Holder<T> holder, ResourceKey<? extends Registry<T>> registry) {
        Optional<ResourceKey<T>> key = holder.unwrapKey();
        if (key.isPresent() && this.matchesId(key.get().location())) {
            return true;
        }
        for (ResourceLocation tag : this.tags) {
            if (holder.is(TagKey.create(registry, tag))) {
                return true;
            }
        }
        return false;
    }
}
