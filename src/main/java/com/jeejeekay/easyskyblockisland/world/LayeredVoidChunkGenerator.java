package com.jeejeekay.easyskyblockisland.world;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A superflat-style generator: optional solid layers, an optional liquid up to a sea level, and
 * nothing else. It extends {@link NoiseBasedChunkGenerator} so biomes still follow the dimension's
 * normal climate noise, and so features and structures can be let through by id.
 */
public class LayeredVoidChunkGenerator extends NoiseBasedChunkGenerator {

    public static final Codec<LayeredVoidChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings),
            FlatLayerInfo.CODEC.listOf().optionalFieldOf("layers", List.of()).forGetter(g -> g.layers),
            Codec.INT.optionalFieldOf("layers_bottom_y").forGetter(g -> g.layersBottomY),
            BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("liquid").forGetter(g -> g.liquid),
            Codec.INT.optionalFieldOf("liquid_bottom_y").forGetter(g -> g.liquidBottomY),
            Codec.INT.optionalFieldOf("sea_level").forGetter(g -> g.seaLevel),
            GenFilter.CODEC.optionalFieldOf("features", GenFilter.NONE).forGetter(g -> g.features),
            GenFilter.CODEC.optionalFieldOf("structures", GenFilter.NONE).forGetter(g -> g.structures)
    ).apply(instance, LayeredVoidChunkGenerator::new));

    private final List<FlatLayerInfo> layers;
    private final Optional<Integer> layersBottomY;
    private final Optional<Block> liquid;
    private final Optional<Integer> liquidBottomY;
    private final Optional<Integer> seaLevel;
    private final GenFilter features;
    private final GenFilter structures;

    // Settings holders may still be unbound while world presets are being parsed, so read them lazily.
    private final Supplier<Column> column;
    private final Map<PlacedFeature, Boolean> featureDecisions = new ConcurrentHashMap<>();

    public LayeredVoidChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings, List<FlatLayerInfo> layers,
                                     Optional<Integer> layersBottomY, Optional<Block> liquid, Optional<Integer> liquidBottomY,
                                     Optional<Integer> seaLevel, GenFilter features, GenFilter structures) {
        super(biomeSource, settings);
        this.layers = List.copyOf(layers);
        this.layersBottomY = layersBottomY;
        this.liquid = liquid;
        this.liquidBottomY = liquidBottomY;
        this.seaLevel = seaLevel;
        this.features = features;
        this.structures = structures;
        this.column = Suppliers.memoize(this::buildColumn);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // ------------------------------------------------------------------ column

    private record Column(int minY, BlockState[] states, int seaLevel) {
    }

    private Column buildColumn() {
        NoiseGeneratorSettings settings = this.generatorSettings().value();
        int base = settings.noiseSettings().minY();
        int layersY = this.layersBottomY.orElse(base);
        int liquidY = this.liquidBottomY.orElse(base);
        int sea = this.seaLevel.orElse(settings.seaLevel());

        int layerTop = layersY;
        for (FlatLayerInfo layer : this.layers) {
            layerTop += layer.getHeight();
        }

        int min = layersY;
        int max = layerTop;
        if (this.liquid.isPresent() && sea > liquidY) {
            min = Math.min(min, liquidY);
            max = Math.max(max, sea);
        }
        BlockState[] states = new BlockState[Math.max(0, max - min)];

        int y = layersY;
        for (FlatLayerInfo layer : this.layers) {
            BlockState state = layer.getBlockState();
            for (int i = 0; i < layer.getHeight(); i++, y++) {
                states[y - min] = state.isAir() ? null : state;
            }
        }
        if (this.liquid.isPresent()) {
            BlockState fluid = this.liquid.get().defaultBlockState();
            for (int ly = liquidY; ly < sea; ly++) {
                if (states[ly - min] == null) {
                    states[ly - min] = fluid;
                }
            }
        }
        return new Column(min, states, sea);
    }

    @Override
    public int getSeaLevel() {
        return this.column.get().seaLevel();
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        Column column = this.column.get();
        if (column.states().length == 0) {
            return CompletableFuture.completedFuture(chunk);
        }
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minBuild = chunk.getMinBuildHeight();
        int maxBuild = chunk.getMaxBuildHeight();
        BlockState[] states = column.states();
        for (int i = 0; i < states.length; i++) {
            BlockState state = states[i];
            int y = column.minY() + i;
            if (state == null || y < minBuild || y >= maxBuild) {
                continue;
            }
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    chunk.setBlockState(pos.set(x, y, z), state, false);
                    oceanFloor.update(x, y, z, state);
                    worldSurface.update(x, y, z, state);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
        // Layers are placed in fillFromNoise; there's no terrain to put a surface on.
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
        // No caves or canyons in flat layers, same as superflat.
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        Column column = this.column.get();
        BlockState[] states = column.states();
        for (int i = states.length - 1; i >= 0; i--) {
            int y = column.minY() + i;
            BlockState state = states[i];
            if (state != null && y >= level.getMinBuildHeight() && y < level.getMaxBuildHeight() && type.isOpaque().test(state)) {
                return y + 1;
            }
        }
        return level.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        Column column = this.column.get();
        int min = level.getMinBuildHeight();
        BlockState[] result = new BlockState[level.getHeight()];
        Arrays.fill(result, Blocks.AIR.defaultBlockState());
        BlockState[] states = column.states();
        for (int i = 0; i < states.length; i++) {
            int index = column.minY() + i - min;
            if (states[i] != null && index >= 0 && index < result.length) {
                result[index] = states[i];
            }
        }
        return new NoiseColumn(min, result);
    }

    /** Top of the solid layers and liquid, i.e. the first free Y. Null when the column is empty. */
    public Integer surfaceY() {
        Column column = this.column.get();
        BlockState[] states = column.states();
        for (int i = states.length - 1; i >= 0; i--) {
            if (states[i] != null) {
                return column.minY() + i + 1;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ structures

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed) {
        if (this.structures.allowsEverything()) {
            return super.createState(structureSets, randomState, seed);
        }
        Stream<Holder<StructureSet>> allowed = this.structures.generatesNothing() ? Stream.empty() : structureSets.listElements()
                .map(this::filterStructureSet)
                .filter(Optional::isPresent)
                .map(Optional::get);
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, this.biomeSource, allowed);
    }

    private Optional<Holder<StructureSet>> filterStructureSet(Holder.Reference<StructureSet> set) {
        // Listing a structure set id keeps (whitelist) or drops (blacklist) all of its structures.
        if (this.structures.lists(set.key().location())) {
            return this.structures.isWhitelist() ? Optional.of(set) : Optional.empty();
        }
        List<StructureSet.StructureSelectionEntry> kept = new ArrayList<>();
        for (StructureSet.StructureSelectionEntry entry : set.value().structures()) {
            if (this.structures.test(entry.structure(), Registries.STRUCTURE)) {
                kept.add(entry);
            }
        }
        if (kept.isEmpty()) {
            return Optional.empty();
        }
        if (kept.size() == set.value().structures().size()) {
            return Optional.of(set);
        }
        return Optional.of(Holder.direct(new StructureSet(kept, set.value().placement())));
    }

    // ------------------------------------------------------------------ features

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        if (this.features.allowsEverything()) {
            super.applyBiomeDecoration(level, chunk, structureManager);
            return;
        }

        // [Vanilla copy of ChunkGenerator#applyBiomeDecoration] with a check before each placed feature.
        ChunkPos chunkPos = chunk.getPos();
        SectionPos sectionPos = SectionPos.of(chunkPos, level.getMinSection());
        BlockPos origin = sectionPos.origin();

        Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Map<Integer, List<Structure>> structuresByStep = structureRegistry.stream()
                .collect(Collectors.groupingBy(structure -> structure.step().ordinal()));
        List<FeatureSorter.StepFeatureData> stepFeatureData = this.featuresPerStep.get();
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(level.getSeed(), origin.getX(), origin.getZ());

        Set<Holder<Biome>> biomes = new ObjectArraySet<>();
        ChunkPos.rangeClosed(sectionPos.chunk(), 1).forEach(pos -> {
            ChunkAccess nearby = level.getChunk(pos.x, pos.z);
            for (LevelChunkSection section : nearby.getSections()) {
                section.getBiomes().getAll(biomes::add);
            }
        });
        biomes.retainAll(this.biomeSource.possibleBiomes());

        int stepCount = stepFeatureData.size();
        boolean anyFeatures = !this.features.generatesNothing();

        try {
            Registry<PlacedFeature> placedFeatures = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
            int steps = Math.max(GenerationStep.Decoration.values().length, stepCount);

            for (int step = 0; step < steps; ++step) {
                int structureIndex = 0;
                if (structureManager.shouldGenerateStructures()) {
                    for (Structure structure : structuresByStep.getOrDefault(step, Collections.emptyList())) {
                        random.setFeatureSeed(decorationSeed, structureIndex, step);
                        Supplier<String> name = () -> structureRegistry.getResourceKey(structure).map(Object::toString).orElseGet(structure::toString);
                        try {
                            level.setCurrentlyGenerating(name);
                            structureManager.startsForStructure(sectionPos, structure).forEach(start ->
                                    start.placeInChunk(level, structureManager, this, random, ChunkGenerator.getWritableArea(chunk), chunkPos));
                        } catch (Exception e) {
                            CrashReport report = CrashReport.forThrowable(e, "Feature placement");
                            report.addCategory("Feature").setDetail("Description", name::get);
                            throw new ReportedException(report);
                        }
                        ++structureIndex;
                    }
                }

                if (anyFeatures && step < stepCount) {
                    IntSet indices = new IntArraySet();
                    for (Holder<Biome> biome : biomes) {
                        List<HolderSet<PlacedFeature>> biomeFeatures = this.generationSettingsGetter.apply(biome).features();
                        if (step < biomeFeatures.size()) {
                            FeatureSorter.StepFeatureData data = stepFeatureData.get(step);
                            biomeFeatures.get(step).stream().map(Holder::value)
                                    .forEach(feature -> indices.add(data.indexMapping().applyAsInt(feature)));
                        }
                    }

                    int[] sorted = indices.toIntArray();
                    Arrays.sort(sorted);
                    FeatureSorter.StepFeatureData data = stepFeatureData.get(step);

                    for (int featureIndex : sorted) {
                        PlacedFeature feature = data.features().get(featureIndex);
                        if (!this.allowsFeature(feature, placedFeatures)) {
                            continue;
                        }
                        Supplier<String> name = () -> placedFeatures.getResourceKey(feature).map(Object::toString).orElseGet(feature::toString);
                        random.setFeatureSeed(decorationSeed, featureIndex, step);
                        try {
                            level.setCurrentlyGenerating(name);
                            feature.placeWithBiomeCheck(level, this, random, origin);
                        } catch (Exception e) {
                            CrashReport report = CrashReport.forThrowable(e, "Feature placement");
                            report.addCategory("Feature").setDetail("Description", name::get);
                            throw new ReportedException(report);
                        }
                    }
                }
            }
            level.setCurrentlyGenerating(null);
        } catch (Exception e) {
            CrashReport report = CrashReport.forThrowable(e, "Biome decoration");
            report.addCategory("Generation").setDetail("CenterX", chunkPos.x).setDetail("CenterZ", chunkPos.z).setDetail("Seed", decorationSeed);
            throw new ReportedException(report);
        }
    }

    private boolean allowsFeature(PlacedFeature feature, Registry<PlacedFeature> registry) {
        return this.featureDecisions.computeIfAbsent(feature, f -> {
            ResourceLocation placedId = registry.getKey(f);
            ResourceLocation configuredId = f.feature().unwrapKey().map(ResourceKey::location).orElse(null);
            return this.features.testAny(placedId, configuredId);
        });
    }
}
