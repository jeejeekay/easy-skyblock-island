package com.jeejeekay.easyskyblockisland.world;

import com.jeejeekay.easyskyblockisland.EasySkyblockIsland;
import com.jeejeekay.easyskyblockisland.config.EsiConfig;
import com.jeejeekay.easyskyblockisland.config.LayerParser;
import com.jeejeekay.easyskyblockisland.island.IslandTemplates;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Swaps each dimension's generator as the server creates its levels (called from
 * {@code MinecraftServerMixin}). A world counts as a Skyblock Islands world when its overworld
 * uses our generator, which only the Skyblock Islands world preset sets up.
 */
public final class WorldSetup {

    public static final String SNAPSHOT_FILE = "easy_skyblock_island.json";

    @Nullable
    private static volatile EsiConfig snapshot;

    private WorldSetup() {
    }

    /** The settings of the running world, or null if it isn't a Skyblock Islands world. */
    @Nullable
    public static EsiConfig snapshot() {
        return snapshot;
    }

    @Nullable
    public static EsiConfig.Dimension settings(ResourceKey<Level> dimension) {
        EsiConfig current = snapshot;
        return current == null ? null : current.dimensions.get(dimension.location().toString());
    }

    public static void reset() {
        snapshot = null;
    }

    public static Path snapshotFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(SNAPSHOT_FILE);
    }

    public static LevelStem modifyStem(MinecraftServer server, ResourceKey<Level> dimension, LevelStem stem) {
        try {
            if (dimension == Level.OVERWORLD) {
                begin(server, stem);
            }
            EsiConfig current = snapshot;
            if (current == null) {
                return stem;
            }
            EsiConfig.Dimension settings = current.dimensions.get(dimension.location().toString());
            ChunkGenerator original = stem.generator();
            if (settings == null || !settings.enabled) {
                if (original instanceof LayeredVoidChunkGenerator ours) {
                    // The preset's overworld was turned off: give it normal terrain back.
                    return new LevelStem(stem.type(), new NoiseBasedChunkGenerator(ours.getBiomeSource(), ours.generatorSettings()));
                }
                return stem;
            }
            ChunkGenerator generator = buildGenerator(server.registryAccess(), dimension, stem, settings);
            if (generator == original) {
                return stem;
            }
            EasySkyblockIsland.LOGGER.info("Using Easy Skyblock Island generation for {}", dimension.location());
            return new LevelStem(stem.type(), generator);
        } catch (RuntimeException e) {
            EasySkyblockIsland.LOGGER.error("Couldn't set up {}, leaving its generation unchanged", dimension.location(), e);
            return stem;
        }
    }

    private static void begin(MinecraftServer server, LevelStem overworld) {
        snapshot = null;
        if (!(overworld.generator() instanceof LayeredVoidChunkGenerator)) {
            return;
        }
        Path file = snapshotFile(server);
        if (Files.exists(file)) {
            EsiConfig saved = EsiConfig.read(file);
            if (saved != null) {
                snapshot = saved;
                return;
            }
            // Don't overwrite a file the player is probably in the middle of editing.
            EasySkyblockIsland.LOGGER.error("{} is unreadable; using the global config for this session without saving it", file);
            snapshot = EsiConfig.load().copy();
            return;
        }
        EsiConfig created = EsiConfig.load().copy();
        PendingIslandChoices.applyTo(created);
        created.save(file);
        IslandTemplates.copyIntoWorld(server, created);
        EasySkyblockIsland.LOGGER.info("Created Skyblock Islands settings for this world at {}", file);
        snapshot = created;
    }

    private static ChunkGenerator buildGenerator(RegistryAccess registries, ResourceKey<Level> dimension, LevelStem stem, EsiConfig.Dimension settings) {
        String context = dimension.location().toString();
        ChunkGenerator original = stem.generator();
        BiomeSource biomes = BiomeFiltering.apply(original.getBiomeSource(), settings.biomes, registries.registryOrThrow(Registries.BIOME), context);
        Holder<NoiseGeneratorSettings> noise = original instanceof NoiseBasedChunkGenerator noiseBased
                ? noiseBased.generatorSettings()
                : Holder.direct(NoiseGeneratorSettings.dummy());

        if (!settings.replaceGenerator) {
            if (original instanceof NoiseBasedChunkGenerator) {
                if (biomes == original.getBiomeSource() && !(original instanceof LayeredVoidChunkGenerator)) {
                    return original;
                }
                return new NoiseBasedChunkGenerator(biomes, noise);
            }
            if (biomes != original.getBiomeSource()) {
                EasySkyblockIsland.LOGGER.warn("[{}] A biome list on a non-noise dimension needs replace_generator: true; ignoring it", context);
            }
            return original;
        }

        int bottom = stem.type().value().minY();
        List<FlatLayerInfo> layers = LayerParser.parse(settings.generator.layers, context);
        Optional<Block> liquid = LayerParser.block(settings.generator.liquid, context);
        int layersBottom = settings.generator.layersBottomY != null ? settings.generator.layersBottomY : bottom;
        int liquidBottom = settings.generator.liquidBottomY != null ? settings.generator.liquidBottomY : bottom;
        int seaLevel = settings.generator.seaLevel != null ? settings.generator.seaLevel : original.getSeaLevel();

        return new LayeredVoidChunkGenerator(biomes, noise, layers, Optional.of(layersBottom), liquid,
                Optional.of(liquidBottom), Optional.of(seaLevel), GenFilter.of(settings.features), GenFilter.of(settings.structures));
    }
}
