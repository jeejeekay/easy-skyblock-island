package com.jeejeekay.easyskyblockisland.config;

import com.jeejeekay.easyskyblockisland.EasySkyblockIsland;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Parses superflat-style layer strings: {@code "minecraft:bedrock,31*minecraft:deepslate,3*minecraft:sand"}. */
public final class LayerParser {

    private LayerParser() {
    }

    public static List<FlatLayerInfo> parse(String layers, String context) {
        List<FlatLayerInfo> result = new ArrayList<>();
        if (layers == null || layers.isBlank()) {
            return result;
        }
        for (String raw : layers.split(",")) {
            String part = raw.trim();
            if (part.isEmpty()) {
                continue;
            }
            int height = 1;
            String blockId = part;
            int star = part.indexOf('*');
            if (star > 0) {
                try {
                    height = Integer.parseInt(part.substring(0, star).trim());
                } catch (NumberFormatException e) {
                    EasySkyblockIsland.LOGGER.warn("[{}] Bad layer count in '{}', skipping it", context, part);
                    continue;
                }
                blockId = part.substring(star + 1).trim();
            }
            if (height <= 0) {
                continue;
            }
            Optional<Block> block = block(blockId, context);
            if (block.isPresent()) {
                result.add(new FlatLayerInfo(height, block.get()));
            }
        }
        return result;
    }

    public static Optional<Block> block(String id, String context) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        ResourceLocation location = ResourceLocation.tryParse(id.trim());
        if (location == null || !BuiltInRegistries.BLOCK.containsKey(location)) {
            EasySkyblockIsland.LOGGER.warn("[{}] Unknown block '{}', skipping it", context, id);
            return Optional.empty();
        }
        return Optional.of(BuiltInRegistries.BLOCK.get(location));
    }
}
