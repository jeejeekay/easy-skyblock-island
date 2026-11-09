package com.jeejeekay.easyskyblockisland.client;

import com.jeejeekay.easyskyblockisland.island.IslandTemplates;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A 3D model of an island file for the picker screen. Blocks are drawn one by one with the
 * normal block renderer; chests, beds, shulker boxes and other "animated" block entities go
 * through their item renderer, turned to face the way they're placed. Blocks completely buried
 * inside the island are skipped.
 */
public final class IslandPreview {

    /** Above this many visible blocks the preview only draws the first ones. */
    private static final int MAX_BLOCKS = 40_000;

    private record Entry(BlockPos pos, BlockState state) {
    }

    private final List<Entry> blocks;
    private final Vec3i size;
    private final int totalBlocks;

    private IslandPreview(List<Entry> blocks, Vec3i size, int totalBlocks) {
        this.blocks = blocks;
        this.size = size;
        this.totalBlocks = totalBlocks;
    }

    public Vec3i size() {
        return this.size;
    }

    public int totalBlocks() {
        return this.totalBlocks;
    }

    public boolean truncated() {
        return this.blocks.size() >= MAX_BLOCKS;
    }

    @Nullable
    public static IslandPreview load(String name) {
        CompoundTag tag = IslandTemplates.readTag(null, name);
        if (tag == null) {
            return null;
        }
        ListTag palette = tag.contains("palette", Tag.TAG_LIST)
                ? tag.getList("palette", Tag.TAG_COMPOUND)
                : tag.getList("palettes", Tag.TAG_LIST).isEmpty() ? new ListTag() : tag.getList("palettes", Tag.TAG_LIST).getList(0);
        List<BlockState> states = new ArrayList<>(palette.size());
        for (int i = 0; i < palette.size(); i++) {
            states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i)));
        }

        Map<BlockPos, BlockState> all = new HashMap<>();
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            int index = block.getInt("state");
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            if (index < 0 || index >= states.size() || pos.size() != 3) {
                continue;
            }
            BlockState state = states.get(index);
            if (!state.isAir()) {
                all.put(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), state);
            }
        }

        List<Entry> visible = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : all.entrySet()) {
            if (isExposed(entry.getKey(), all)) {
                visible.add(new Entry(entry.getKey(), entry.getValue()));
                if (visible.size() >= MAX_BLOCKS) {
                    break;
                }
            }
        }

        ListTag sizeTag = tag.getList("size", Tag.TAG_INT);
        Vec3i size = sizeTag.size() == 3 ? new Vec3i(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2)) : new Vec3i(1, 1, 1);
        return new IslandPreview(visible, size, all.size());
    }

    private static boolean isExposed(BlockPos pos, Map<BlockPos, BlockState> all) {
        for (Direction direction : Direction.values()) {
            BlockState neighbour = all.get(pos.relative(direction));
            if (neighbour == null || !neighbour.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
                return true;
            }
        }
        return false;
    }

    /** Draws the island centred in the box, turned by {@code yaw} and tilted by {@code pitch} degrees. */
    public void render(GuiGraphics graphics, int x, int y, int width, int height, float yaw, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockRenderDispatcher renderer = minecraft.getBlockRenderer();
        PoseStack pose = graphics.pose();

        float sx = this.size.getX();
        float sy = this.size.getY();
        float sz = this.size.getZ();
        float diagonal = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        float scale = Math.min(width, height) / Math.max(diagonal, 1.0F) * 0.95F;

        graphics.enableScissor(x, y, x + width, y + height);
        pose.pushPose();
        // Far enough forward that the whole island stays in front of the panel behind it.
        pose.translate(x + width / 2.0F, y + height / 2.0F, 200.0F + scale * diagonal);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-sx / 2.0F, -sy / 2.0F, -sz / 2.0F);

        Lighting.setupFor3DItems();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        for (Entry entry : this.blocks) {
            BlockState state = entry.state();
            boolean blockEntity = state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED;
            if (blockEntity && state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT) {
                continue; // the bed's item model already covers both halves
            }
            pose.pushPose();
            pose.translate(entry.pos().getX(), entry.pos().getY(), entry.pos().getZ());
            if (blockEntity && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                // Item renderers draw these facing south; turn them to match the placed block.
                float facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot();
                pose.translate(0.5F, 0.0F, 0.5F);
                pose.mulPose(Axis.YP.rotationDegrees(-facing));
                pose.translate(-0.5F, 0.0F, -0.5F);
            }
            renderer.renderSingleBlock(state, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        buffers.endBatch();
        pose.popPose();
        graphics.disableScissor();
    }
}
