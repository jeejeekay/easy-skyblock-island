package com.jeejeekay.easyskyblockisland.island;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Which dimensions already have their island, where its spawn is, and which players have started. */
public class IslandSavedData extends SavedData {

    private static final String NAME = "easy_skyblock_island";

    private final Map<String, IslandPlacer.Placed> islands = new HashMap<>();
    private final Set<UUID> startedPlayers = new HashSet<>();

    public static IslandSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(IslandSavedData::load, IslandSavedData::new, NAME);
    }

    public boolean hasIsland(String dimension) {
        return this.islands.containsKey(dimension);
    }

    @Nullable
    public IslandPlacer.Placed island(String dimension) {
        return this.islands.get(dimension);
    }

    public void setIsland(String dimension, IslandPlacer.Placed placed) {
        this.islands.put(dimension, placed);
        this.setDirty();
    }

    public boolean hasStarted(UUID player) {
        return this.startedPlayers.contains(player);
    }

    public void markStarted(UUID player) {
        if (this.startedPlayers.add(player)) {
            this.setDirty();
        }
    }

    private static IslandSavedData load(CompoundTag tag) {
        IslandSavedData data = new IslandSavedData();
        CompoundTag islands = tag.getCompound("Islands");
        for (String dimension : islands.getAllKeys()) {
            CompoundTag island = islands.getCompound(dimension);
            data.islands.put(dimension, new IslandPlacer.Placed(NbtUtils.readBlockPos(island.getCompound("Spawn")), island.getFloat("Yaw")));
        }
        ListTag players = tag.getList("StartedPlayers", Tag.TAG_STRING);
        for (int i = 0; i < players.size(); i++) {
            try {
                data.startedPlayers.add(UUID.fromString(players.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag islands = new CompoundTag();
        this.islands.forEach((dimension, placed) -> {
            CompoundTag island = new CompoundTag();
            island.put("Spawn", NbtUtils.writeBlockPos(placed.spawn()));
            island.putFloat("Yaw", placed.yaw());
            islands.put(dimension, island);
        });
        tag.put("Islands", islands);
        ListTag players = new ListTag();
        for (UUID player : this.startedPlayers) {
            players.add(StringTag.valueOf(player.toString()));
        }
        tag.put("StartedPlayers", players);
        return tag;
    }
}
