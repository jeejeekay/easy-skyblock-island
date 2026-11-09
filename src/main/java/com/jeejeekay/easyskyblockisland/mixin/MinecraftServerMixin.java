package com.jeejeekay.easyskyblockisland.mixin;

import com.jeejeekay.easyskyblockisland.world.WorldSetup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Replaces the {@link LevelStem} handed to each {@code new ServerLevel(...)} in
 * {@code MinecraftServer#createLevels}, so every dimension (vanilla or modded) can get our generator
 * without touching the saved world settings.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @ModifyArg(
            method = "createLevels",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;<init>(Lnet/minecraft/server/MinecraftServer;Ljava/util/concurrent/Executor;Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/world/level/storage/ServerLevelData;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/level/dimension/LevelStem;Lnet/minecraft/server/level/progress/ChunkProgressListener;ZJLjava/util/List;ZLnet/minecraft/world/RandomSequences;)V"
            ),
            index = 5
    )
    private LevelStem easySkyblockIsland$swapGenerator(MinecraftServer server, Executor executor, LevelStorageSource.LevelStorageAccess storage,
                                                       ServerLevelData levelData, ResourceKey<Level> dimension, LevelStem stem,
                                                       ChunkProgressListener progress, boolean debug, long seed, List<CustomSpawner> spawners,
                                                       boolean tickTime, @Nullable RandomSequences randomSequences) {
        return WorldSetup.modifyStem(server, dimension, stem);
    }
}
