package com.jeejeekay.easyskyblockisland.event;

import com.jeejeekay.easyskyblockisland.EasySkyblockIsland;
import com.jeejeekay.easyskyblockisland.command.EsiCommands;
import com.jeejeekay.easyskyblockisland.config.EsiConfig;
import com.jeejeekay.easyskyblockisland.island.IslandPlacer;
import com.jeejeekay.easyskyblockisland.island.IslandSavedData;
import com.jeejeekay.easyskyblockisland.island.IslandTemplates;
import com.jeejeekay.easyskyblockisland.world.LayeredVoidChunkGenerator;
import com.jeejeekay.easyskyblockisland.world.WorldSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.util.Map;

@Mod.EventBusSubscriber(modid = EasySkyblockIsland.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerEvents {

    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        EsiCommands.register(event.getDispatcher());
    }

    /**
     * Vanilla searches up to a thousand chunks for a grassy spawn point, which is slow and pointless
     * in a void world. Skip it; the spawn island sets the real spawn once the server has started.
     */
    @SubscribeEvent
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || WorldSetup.snapshot() == null) {
            return;
        }
        ServerLevelData data = event.getSettings();
        data.setSpawn(new BlockPos(0, Math.max(level.getSeaLevel(), level.getMinBuildHeight() + 1), 0), 0);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        addNewDimensionsToConfig(server);

        EsiConfig snapshot = WorldSetup.snapshot();
        if (snapshot == null) {
            return;
        }
        IslandSavedData data = IslandSavedData.get(server);
        for (Map.Entry<String, EsiConfig.Dimension> entry : snapshot.dimensions.entrySet()) {
            EsiConfig.Dimension settings = entry.getValue();
            if (!EsiConfig.hasSpawnIsland(settings) || data.hasIsland(entry.getKey())) {
                continue;
            }
            ServerLevel level = level(server, entry.getKey());
            if (level == null) {
                EasySkyblockIsland.LOGGER.warn("Dimension {} has a spawn island but doesn't exist", entry.getKey());
                continue;
            }
            IslandPlacer.Placed placed = placeIsland(level, settings, new BlockPos(settings.island.x, 0, settings.island.z), true);
            if (placed != null) {
                data.setIsland(entry.getKey(), placed);
                if (level.dimension() == Level.OVERWORLD) {
                    level.setDefaultSpawnPos(placed.spawn(), placed.yaw());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WorldSetup.reset();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || WorldSetup.snapshot() == null) {
            return;
        }
        IslandSavedData data = IslandSavedData.get(player.server);
        if (data.hasStarted(player.getUUID())) {
            return;
        }
        if (sendToStartIsland(player, true)) {
            data.markStarted(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player) || WorldSetup.snapshot() == null) {
            return;
        }
        // Vanilla clears the respawn point when the bed or anchor is gone; only then step in.
        if (player.getRespawnPosition() == null) {
            sendToStartIsland(player, true);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || WorldSetup.snapshot() == null) {
            return;
        }
        ResourceKey<Level> to = event.getTo();
        String id = to.location().toString();
        EsiConfig.Dimension settings = WorldSetup.settings(to);
        if (!EsiConfig.hasArrivalIsland(settings)) {
            return;
        }
        IslandSavedData data = IslandSavedData.get(player.server);
        if (data.hasIsland(id)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        IslandPlacer.Placed placed = placeIsland(level, settings, player.blockPosition(), false);
        if (placed != null) {
            data.setIsland(id, placed);
            player.fallDistance = 0;
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Places a dimension's island. With {@code atSpawn} the spawn marker goes to the configured y
     * (or just above the generated surface); otherwise to the given position (the arrival point),
     * unless a y is configured.
     */
    @Nullable
    public static IslandPlacer.Placed placeIsland(ServerLevel level, EsiConfig.Dimension settings, BlockPos column, boolean atSpawn) {
        StructureTemplate template = IslandTemplates.load(level.getServer(), settings.island.template);
        if (template == null) {
            EasySkyblockIsland.LOGGER.error("Island '{}' for {} couldn't be loaded", settings.island.template, level.dimension().location());
            return null;
        }
        int y;
        if (settings.island.y != null) {
            y = settings.island.y;
        } else if (atSpawn) {
            // Sit the island's bottom on the surface of the layers/sea, or at y 64 in a void.
            Integer surface = level.getChunkSource().getGenerator() instanceof LayeredVoidChunkGenerator layered ? layered.surfaceY() : null;
            int bottom = surface != null ? surface : 64;
            y = bottom + IslandPlacer.anchor(template).offset().getY();
        } else {
            y = column.getY();
        }
        y = Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight() - 2, y));
        BlockPos spawn = new BlockPos(column.getX(), y, column.getZ());
        IslandPlacer.Placed placed = IslandPlacer.place(level, template, spawn);
        EasySkyblockIsland.LOGGER.info("Placed island '{}' in {} with its spawn at {}", settings.island.template, level.dimension().location(), placed.spawn().toShortString());
        return placed;
    }

    /** Teleports a player onto the start island. Returns false when there's no start island. */
    public static boolean sendToStartIsland(ServerPlayer player, boolean setRespawn) {
        EsiConfig snapshot = WorldSetup.snapshot();
        if (snapshot == null) {
            return false;
        }
        String startDimension = snapshot.startDimension();
        if (startDimension == null) {
            return false;
        }
        IslandPlacer.Placed island = IslandSavedData.get(player.server).island(startDimension);
        ServerLevel level = level(player.server, startDimension);
        if (island == null || level == null) {
            return false;
        }
        BlockPos spawn = island.spawn();
        player.teleportTo(level, spawn.getX() + 0.5, spawn.getY() + 0.1, spawn.getZ() + 0.5, island.yaw(), 0);
        player.fallDistance = 0;
        if (setRespawn) {
            player.setRespawnPosition(level.dimension(), spawn, island.yaw(), true, false);
        }
        return true;
    }

    @Nullable
    private static ServerLevel level(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /** Lists dimensions added by mods in the global config (switched off) so they're easy to find and enable. */
    private static void addNewDimensionsToConfig(MinecraftServer server) {
        EsiConfig config = Files.exists(EsiConfig.configFile()) ? EsiConfig.read(EsiConfig.configFile()) : EsiConfig.load();
        if (config == null) {
            return; // unreadable: leave the player's file alone
        }
        boolean changed = false;
        for (ResourceKey<Level> key : server.levelKeys()) {
            String id = key.location().toString();
            if (!config.dimensions.containsKey(id)) {
                EsiConfig.Dimension settings = EsiConfig.createDefaultFor(id);
                settings.enabled = false;
                config.dimensions.put(id, settings);
                changed = true;
            }
        }
        if (changed) {
            config.save(EsiConfig.configFile());
        }
    }
}
