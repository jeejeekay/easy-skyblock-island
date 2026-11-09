package com.jeejeekay.easyskyblockisland.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.jeejeekay.easyskyblockisland.EasySkyblockIsland;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The mod's settings. The same shape is used for the global file in
 * {@code config/easy_skyblock_island/config.json} and for the copy stored inside each world
 * ({@code <world>/easy_skyblock_island.json}) when the world is created.
 */
public class EsiConfig {

    public static final String OVERWORLD = "minecraft:overworld";
    public static final String NETHER = "minecraft:the_nether";
    public static final String END = "minecraft:the_end";

    public static final String PLACEMENT_SPAWN = "spawn";
    public static final String PLACEMENT_FIRST_ARRIVAL = "first_arrival";

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .disableHtmlEscaping()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    public String comment = "Docs: https://github.com/jeejeekay/easy-skyblock-island#configuration . Each world keeps its own copy of these settings (easy_skyblock_island.json in the world folder) from the moment it is created.";

    /** Pre-selects the Skyblock Islands world type on the Create World screen. */
    public boolean makeDefaultWorldType = true;

    public LinkedHashMap<String, Dimension> dimensions = new LinkedHashMap<>();

    public static class Dimension {
        /** Whether this mod touches the dimension at all. */
        public boolean enabled = false;
        /** True: use the layered void generator. False: keep the dimension's own terrain (biome list still applies). */
        public boolean replaceGenerator = true;
        public Generator generator = new Generator();
        public Biomes biomes = new Biomes();
        public Filter features = new Filter();
        public Filter structures = new Filter();
        public Island island = new Island();
    }

    public static class Generator {
        /** Superflat-style layers, bottom first, e.g. "minecraft:bedrock,31*minecraft:deepslate". Empty = no floor. */
        public String layers = "";
        /** Y of the lowest layer. Null = the bottom of the dimension. */
        public Integer layersBottomY = null;
        /** Fluid (or any block) that fills empty space below sea_level. Empty = no sea. */
        public String liquid = "";
        /** Lowest Y the liquid fills. Null = the bottom of the dimension. */
        public Integer liquidBottomY = null;
        /** The liquid fills up to sea_level - 1, like vanilla. Null = the dimension's normal sea level. */
        public Integer seaLevel = null;
    }

    public static class Biomes {
        /** False: the dimension's normal biomes. True: apply the list below. */
        public boolean useList = false;
        /** True: only biomes in the list. False: every normal biome except those in the list. */
        public boolean listIsWhitelist = true;
        /** Biome ids, "#tags" or "modid:*". */
        public List<String> list = new ArrayList<>();
        /** Biome patch size (0-62, each step doubles) used when listed biomes can't follow the normal climate layout. */
        public int checkerboardSize = 3;
    }

    public static class Filter {
        /** False: none generate. True: generate, filtered by the list. */
        public boolean generate = false;
        /** True: only ids in the list. False: everything except ids in the list. */
        public boolean listIsWhitelist = false;
        /** Ids, "#tags" (structures only) or "modid:*". */
        public List<String> list = new ArrayList<>();

        public Filter() {
        }

        public Filter(boolean generate, boolean listIsWhitelist, List<String> list) {
            this.generate = generate;
            this.listIsWhitelist = listIsWhitelist;
            this.list = new ArrayList<>(list);
        }
    }

    public static class Island {
        /** Island file name without .nbt (config/easy_skyblock_island/islands). Empty = no island. */
        public String template = "";
        /** "spawn": placed when the world is created, players start there. "first_arrival": placed where the first player enters the dimension. */
        public String placement = PLACEMENT_FIRST_ARRIVAL;
        /** Y of the spawn marker. Null = on the surface for "spawn", the arrival point for "first_arrival". */
        public Integer y = null;
        public int x = 0;
        public int z = 0;
    }

    // ------------------------------------------------------------------ defaults

    public static EsiConfig createDefault() {
        EsiConfig config = new EsiConfig();

        Dimension overworld = new Dimension();
        overworld.enabled = true;
        overworld.generator.layers = "minecraft:bedrock,31*minecraft:deepslate,32*minecraft:stone,3*minecraft:sand";
        overworld.generator.liquid = "minecraft:water";
        overworld.generator.seaLevel = 63;
        overworld.island.template = "default";
        overworld.island.placement = PLACEMENT_SPAWN;
        config.dimensions.put(OVERWORLD, overworld);

        Dimension nether = new Dimension();
        nether.enabled = true;
        nether.island.template = "nether";
        nether.island.placement = PLACEMENT_FIRST_ARRIVAL;
        config.dimensions.put(NETHER, nether);

        Dimension end = new Dimension();
        end.enabled = false;
        // A void End still needs the obsidian pillars and return gateway for the dragon fight.
        end.features = new Filter(true, true, List.of("minecraft:end_spike", "minecraft:end_gateway_return"));
        config.dimensions.put(END, end);

        return config;
    }

    public static Dimension createDefaultFor(String dimensionId) {
        EsiConfig defaults = createDefault();
        Dimension dimension = defaults.dimensions.get(dimensionId);
        return dimension != null ? dimension : new Dimension();
    }

    // ------------------------------------------------------------------ files

    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get().resolve(EasySkyblockIsland.MODID);
    }

    public static Path configFile() {
        return configDir().resolve("config.json");
    }

    /** Reads the global config from disk, writing the defaults first if it doesn't exist. */
    public static EsiConfig load() {
        Path file = configFile();
        if (!Files.exists(file)) {
            EsiConfig config = createDefault();
            config.save(file);
            return config;
        }
        EsiConfig config = read(file);
        if (config == null) {
            EasySkyblockIsland.LOGGER.error("Couldn't read {}, using the defaults for now. Fix or delete the file to continue.", file);
            return createDefault();
        }
        return config;
    }

    public static EsiConfig read(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            EsiConfig config = GSON.fromJson(reader, EsiConfig.class);
            if (config == null) {
                return null;
            }
            config.fillMissing();
            return config;
        } catch (IOException | JsonParseException e) {
            EasySkyblockIsland.LOGGER.error("Couldn't read {}", file, e);
            return null;
        }
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            EasySkyblockIsland.LOGGER.error("Couldn't write {}", file, e);
        }
    }

    public EsiConfig copy() {
        EsiConfig copy = GSON.fromJson(GSON.toJson(this), EsiConfig.class);
        copy.fillMissing();
        return copy;
    }

    /** Gson leaves fields null when they're missing from the file; put the defaults back. */
    private void fillMissing() {
        if (this.dimensions == null) {
            this.dimensions = new LinkedHashMap<>();
        }
        for (Map.Entry<String, Dimension> entry : this.dimensions.entrySet()) {
            Dimension d = entry.getValue();
            if (d == null) {
                entry.setValue(d = new Dimension());
            }
            if (d.generator == null) d.generator = new Generator();
            if (d.generator.layers == null) d.generator.layers = "";
            if (d.generator.liquid == null) d.generator.liquid = "";
            if (d.biomes == null) d.biomes = new Biomes();
            if (d.biomes.list == null) d.biomes.list = new ArrayList<>();
            if (d.features == null) d.features = new Filter();
            if (d.features.list == null) d.features.list = new ArrayList<>();
            if (d.structures == null) d.structures = new Filter();
            if (d.structures.list == null) d.structures.list = new ArrayList<>();
            if (d.island == null) d.island = new Island();
            if (d.island.template == null) d.island.template = "";
            if (d.island.placement == null) d.island.placement = PLACEMENT_FIRST_ARRIVAL;
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The dimension new players start in: the overworld if it has a spawn island, otherwise the
     * first enabled dimension with one. Null if no dimension has a spawn island.
     */
    public String startDimension() {
        Dimension overworld = this.dimensions.get(OVERWORLD);
        if (hasSpawnIsland(overworld)) {
            return OVERWORLD;
        }
        for (Map.Entry<String, Dimension> entry : this.dimensions.entrySet()) {
            if (hasSpawnIsland(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static boolean hasSpawnIsland(Dimension d) {
        return d != null && d.enabled && !d.island.template.isBlank() && PLACEMENT_SPAWN.equals(d.island.placement);
    }

    public static boolean hasArrivalIsland(Dimension d) {
        return d != null && d.enabled && !d.island.template.isBlank() && PLACEMENT_FIRST_ARRIVAL.equals(d.island.placement);
    }
}
