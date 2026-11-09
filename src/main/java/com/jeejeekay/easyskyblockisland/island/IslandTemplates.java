package com.jeejeekay.easyskyblockisland.island;

import com.jeejeekay.easyskyblockisland.EasySkyblockIsland;
import com.jeejeekay.easyskyblockisland.config.EsiConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Island files are vanilla structure NBT. They live in {@code config/easy_skyblock_island/islands};
 * a world copies the ones it uses into {@code <world>/easy_skyblock_island_islands} when it's created,
 * so editing or deleting a file later doesn't change islands that haven't been placed yet.
 */
public final class IslandTemplates {

    public static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_\\-]{1,64}");
    public static final List<String> BUNDLED = List.of("default", "nether");
    private static final String WORLD_FOLDER = "easy_skyblock_island_islands";

    private IslandTemplates() {
    }

    public static Path islandsDir() {
        return EsiConfig.configDir().resolve("islands");
    }

    public static Path worldIslandsDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(WORLD_FOLDER);
    }

    public static Path fileFor(String name) {
        return islandsDir().resolve(name + ".nbt");
    }

    public static boolean isValidName(String name) {
        return VALID_NAME.matcher(name).matches();
    }

    /** Copies the bundled islands into the config folder the first time the folder is created. */
    public static void installBundledIslands() {
        Path dir = islandsDir();
        if (Files.isDirectory(dir)) {
            return;
        }
        try {
            Files.createDirectories(dir);
            for (String name : BUNDLED) {
                try (InputStream in = bundled(name)) {
                    if (in != null) {
                        Files.copy(in, dir.resolve(name + ".nbt"), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException e) {
            EasySkyblockIsland.LOGGER.error("Couldn't install the bundled islands into {}", dir, e);
        }
    }

    @Nullable
    private static InputStream bundled(String name) {
        return IslandTemplates.class.getResourceAsStream("/islands/" + name + ".nbt");
    }

    /** Names of the islands in the config folder plus the bundled ones, sorted. */
    public static List<String> listNames() {
        List<String> names = new ArrayList<>();
        Path dir = islandsDir();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.nbt")) {
                for (Path file : files) {
                    String fileName = file.getFileName().toString();
                    names.add(fileName.substring(0, fileName.length() - 4).toLowerCase(Locale.ROOT));
                }
            } catch (IOException e) {
                EasySkyblockIsland.LOGGER.error("Couldn't list islands in {}", dir, e);
            }
        }
        for (String name : BUNDLED) {
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        names.sort(String::compareTo);
        return names;
    }

    /** Copies every island a world's settings use into the world folder. */
    public static void copyIntoWorld(MinecraftServer server, EsiConfig config) {
        Path target = worldIslandsDir(server);
        for (EsiConfig.Dimension dimension : config.dimensions.values()) {
            String name = dimension.island.template;
            if (!dimension.enabled || name == null || name.isBlank()) {
                continue;
            }
            try {
                Files.createDirectories(target);
                Path source = fileFor(name);
                Path destination = target.resolve(name + ".nbt");
                if (Files.exists(source)) {
                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    try (InputStream in = bundled(name)) {
                        if (in != null) {
                            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
                        } else {
                            EasySkyblockIsland.LOGGER.warn("Island '{}' doesn't exist in {}", name, islandsDir());
                        }
                    }
                }
            } catch (IOException e) {
                EasySkyblockIsland.LOGGER.error("Couldn't copy island '{}' into the world", name, e);
            }
        }
    }

    /** Reads an island, looking in the world folder, then the config folder, then the mod jar. */
    @Nullable
    public static StructureTemplate load(@Nullable MinecraftServer server, String name) {
        CompoundTag tag = readTag(server, name);
        if (tag == null) {
            return null;
        }
        StructureTemplate template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK.asLookup(), tag);
        return template;
    }

    @Nullable
    private static CompoundTag readTag(@Nullable MinecraftServer server, String name) {
        if (!isValidName(name)) {
            EasySkyblockIsland.LOGGER.warn("'{}' isn't a valid island name", name);
            return null;
        }
        List<Path> candidates = new ArrayList<>();
        if (server != null) {
            candidates.add(worldIslandsDir(server).resolve(name + ".nbt"));
        }
        candidates.add(fileFor(name));
        for (Path file : candidates) {
            if (Files.exists(file)) {
                try {
                    return NbtIo.readCompressed(file.toFile());
                } catch (IOException e) {
                    EasySkyblockIsland.LOGGER.error("Couldn't read island {}", file, e);
                }
            }
        }
        try (InputStream in = bundled(name)) {
            if (in != null) {
                return NbtIo.readCompressed(in);
            }
        } catch (IOException e) {
            EasySkyblockIsland.LOGGER.error("Couldn't read bundled island '{}'", name, e);
        }
        return null;
    }

    public static void save(String name, StructureTemplate template) throws IOException {
        Path file = fileFor(name);
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(template.save(new CompoundTag()), file.toFile());
    }
}
