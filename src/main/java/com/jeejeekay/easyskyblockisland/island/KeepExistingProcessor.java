package com.jeejeekay.easyskyblockisland.island;

import com.jeejeekay.easyskyblockisland.Registration;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;

/**
 * Drops the spawn marker from placed islands, and never overwrites solid blocks already in the
 * world, so an island placed around a Nether portal or the End platform leaves them intact.
 * Air, water and other replaceable blocks are overwritten.
 */
public class KeepExistingProcessor extends StructureProcessor {

    public static final KeepExistingProcessor INSTANCE = new KeepExistingProcessor();
    public static final Codec<KeepExistingProcessor> CODEC = Codec.unit(() -> INSTANCE);

    @Nullable
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pivot,
                                                             StructureTemplate.StructureBlockInfo original,
                                                             StructureTemplate.StructureBlockInfo placed,
                                                             StructurePlaceSettings settings) {
        if (placed.state().is(Registration.SPAWN_MARKER.get())) {
            return null;
        }
        BlockState existing = level.getBlockState(placed.pos());
        if (!existing.isAir() && !existing.canBeReplaced()) {
            return null;
        }
        return placed;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return Registration.KEEP_EXISTING.get();
    }
}
