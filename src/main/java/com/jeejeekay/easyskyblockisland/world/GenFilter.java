package com.jeejeekay.easyskyblockisland.world;

import com.jeejeekay.easyskyblockisland.config.EsiConfig;
import com.jeejeekay.easyskyblockisland.util.IdMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Which features or structures a {@link LayeredVoidChunkGenerator} lets through. */
public final class GenFilter {

    public static final Codec<GenFilter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("generate", false).forGetter(f -> f.generate),
            Codec.BOOL.optionalFieldOf("list_is_whitelist", false).forGetter(f -> f.whitelist),
            Codec.STRING.listOf().optionalFieldOf("list", List.of()).forGetter(f -> f.entries)
    ).apply(instance, GenFilter::new));

    public static final GenFilter NONE = new GenFilter(false, false, List.of());
    public static final GenFilter ALL = new GenFilter(true, false, List.of());

    private final boolean generate;
    private final boolean whitelist;
    private final List<String> entries;
    private final IdMatcher matcher;

    public GenFilter(boolean generate, boolean whitelist, List<String> entries) {
        this.generate = generate;
        this.whitelist = whitelist;
        this.entries = List.copyOf(entries);
        this.matcher = IdMatcher.of(this.entries);
    }

    public static GenFilter of(EsiConfig.Filter filter) {
        return new GenFilter(filter.generate, filter.listIsWhitelist, filter.list);
    }

    public boolean generatesNothing() {
        return !this.generate || (this.whitelist && this.matcher.isEmpty());
    }

    public boolean allowsEverything() {
        return this.generate && !this.whitelist && this.matcher.isEmpty();
    }

    public boolean isWhitelist() {
        return this.whitelist;
    }

    /** Whether the id is named in the list (regardless of whitelist or blacklist). */
    public boolean lists(ResourceLocation id) {
        return this.matcher.matchesId(id);
    }

    public boolean test(ResourceLocation id) {
        if (!this.generate) {
            return false;
        }
        return this.matcher.matchesId(id) == this.whitelist;
    }

    /** Like {@link #test(ResourceLocation)} but also checks any of several ids (e.g. placed and configured feature). */
    public boolean testAny(ResourceLocation first, ResourceLocation second) {
        if (!this.generate) {
            return false;
        }
        boolean matched = this.matcher.matchesId(first) || this.matcher.matchesId(second);
        return matched == this.whitelist;
    }

    public <T> boolean test(Holder<T> holder, ResourceKey<? extends Registry<T>> registry) {
        if (!this.generate) {
            return false;
        }
        return this.matcher.matches(holder, registry) == this.whitelist;
    }
}
