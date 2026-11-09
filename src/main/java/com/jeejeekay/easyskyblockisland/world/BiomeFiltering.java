package com.jeejeekay.easyskyblockisland.world;

import com.jeejeekay.easyskyblockisland.EasySkyblockIsland;
import com.jeejeekay.easyskyblockisland.config.EsiConfig;
import com.jeejeekay.easyskyblockisland.util.IdMatcher;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.CheckerboardColumnBiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Builds the biome source for a dimension from its biome list. Where possible the dimension's own
 * climate layout is kept and only the unwanted biomes are taken out, so the remaining biomes spread
 * into the gaps naturally. Biomes that aren't native to the dimension fall back to a checkerboard.
 */
public final class BiomeFiltering {

    private static final Comparator<Holder<Biome>> BY_ID = Comparator.comparing(h -> h.unwrapKey().map(k -> k.location().toString()).orElse(""));

    private BiomeFiltering() {
    }

    public static BiomeSource apply(BiomeSource original, EsiConfig.Biomes config, Registry<Biome> registry, String context) {
        if (!config.useList) {
            return original;
        }
        IdMatcher matcher = IdMatcher.of(config.list);
        Predicate<Holder<Biome>> listed = holder -> matcher.matches(holder, Registries.BIOME);
        int checkerboard = Mth.clamp(config.checkerboardSize, 0, 62);

        if (config.listIsWhitelist) {
            List<Holder<Biome>> biomes = registry.holders().<Holder<Biome>>map(h -> h).filter(listed).sorted(BY_ID).toList();
            if (biomes.isEmpty()) {
                EasySkyblockIsland.LOGGER.warn("[{}] The biome whitelist matches no biomes, keeping normal biomes", context);
                return original;
            }
            if (biomes.size() == 1) {
                return new FixedBiomeSource(biomes.get(0));
            }
            Set<ResourceKey<Biome>> native_ = keys(original.possibleBiomes().stream().toList());
            if (original instanceof MultiNoiseBiomeSource multiNoise && native_.containsAll(keys(biomes))) {
                return climateFiltered(multiNoise, listed);
            }
            return new CheckerboardColumnBiomeSource(HolderSet.direct(biomes), checkerboard);
        }

        List<Holder<Biome>> remaining = original.possibleBiomes().stream().filter(listed.negate()).sorted(BY_ID).toList();
        if (remaining.isEmpty()) {
            EasySkyblockIsland.LOGGER.warn("[{}] The biome blacklist removes every biome, keeping normal biomes", context);
            return original;
        }
        if (remaining.size() == original.possibleBiomes().size()) {
            return original;
        }
        if (original instanceof MultiNoiseBiomeSource multiNoise) {
            return climateFiltered(multiNoise, listed.negate());
        }
        if (remaining.size() == 1) {
            return new FixedBiomeSource(remaining.get(0));
        }
        return new CheckerboardColumnBiomeSource(HolderSet.direct(remaining), checkerboard);
    }

    private static BiomeSource climateFiltered(MultiNoiseBiomeSource source, Predicate<Holder<Biome>> keep) {
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> kept = source.parameters().values().stream()
                .filter(pair -> keep.test(pair.getSecond()))
                .collect(Collectors.toList());
        return MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(kept));
    }

    private static Set<ResourceKey<Biome>> keys(List<Holder<Biome>> biomes) {
        return biomes.stream().flatMap(h -> h.unwrapKey().stream()).collect(Collectors.toSet());
    }
}
