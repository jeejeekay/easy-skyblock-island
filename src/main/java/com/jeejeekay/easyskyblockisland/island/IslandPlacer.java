package com.jeejeekay.easyskyblockisland.island;

import com.jeejeekay.easyskyblockisland.Registration;
import com.jeejeekay.easyskyblockisland.block.SpawnMarkerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.List;

/** Places an island so that its spawn marker lands on a given position. */
public final class IslandPlacer {

    private IslandPlacer() {
    }

    /** Where a player should stand on a placed island, and which way they should face. */
    public record Placed(BlockPos spawn, float yaw) {
    }

    /** The spawn marker's position inside the template and its facing, or the top centre if there's no marker. */
    public record Anchor(BlockPos offset, float yaw, boolean hasMarker) {
    }

    public static Anchor anchor(StructureTemplate template) {
        List<StructureTemplate.StructureBlockInfo> markers = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Registration.SPAWN_MARKER.get());
        if (!markers.isEmpty()) {
            StructureTemplate.StructureBlockInfo marker = markers.get(0);
            Direction facing = marker.state().hasProperty(SpawnMarkerBlock.FACING) ? marker.state().getValue(SpawnMarkerBlock.FACING) : Direction.SOUTH;
            return new Anchor(marker.pos(), facing.toYRot(), true);
        }
        Vec3i size = template.getSize();
        return new Anchor(new BlockPos(size.getX() / 2, size.getY(), size.getZ() / 2), Direction.SOUTH.toYRot(), false);
    }

    /** Places the island with its spawn marker at {@code spawn}. */
    public static Placed place(ServerLevel level, StructureTemplate template, BlockPos spawn) {
        Anchor anchor = anchor(template);
        BlockPos origin = spawn.subtract(anchor.offset());
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setKnownShape(true)
                .setKeepLiquids(false)
                .addProcessor(KeepExistingProcessor.INSTANCE);
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        return new Placed(spawn, anchor.yaw());
    }
}
