package com.jeejeekay.easyskyblockisland.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Places like a normal block against other blocks, and can also be placed in mid-air: using it
 * while not looking at a block puts it two blocks in front of you. Handy for marking the corners
 * of an island selection out in the void.
 */
public class BoundaryMarkerItem extends BlockItem {

    private static final double AIR_PLACE_DISTANCE = 2.0;

    public BoundaryMarkerItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Vec3 target = player.getEyePosition().add(player.getLookAngle().scale(AIR_PLACE_DISTANCE));
        BlockPos pos = BlockPos.containing(target);
        BlockState existing = level.getBlockState(pos);
        if (!level.isInWorldBounds(pos) || !existing.canBeReplaced() || !level.isUnobstructed(this.getBlock().defaultBlockState(), pos, CollisionContext.of(player))) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            BlockState state = this.getBlock().defaultBlockState();
            level.setBlock(pos, state, Block.UPDATE_ALL);
            level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
            SoundType sound = state.getSoundType();
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.easy_skyblock_island.boundary_marker.tooltip.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.easy_skyblock_island.boundary_marker.tooltip.2").withStyle(ChatFormatting.GRAY));
    }
}
