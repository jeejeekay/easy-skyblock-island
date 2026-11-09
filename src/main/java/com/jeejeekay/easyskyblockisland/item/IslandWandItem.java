package com.jeejeekay.easyskyblockisland.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.List;

/** Marks the two corners of an island so {@code /esi save <name>} can save it. */
public class IslandWandItem extends Item {

    private static final String POS1 = "Pos1";
    private static final String POS2 = "Pos2";

    public IslandWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        if (!context.getLevel().isClientSide) {
            boolean second = player != null && player.isShiftKeyDown();
            stack.getOrCreateTag().put(second ? POS2 : POS1, NbtUtils.writeBlockPos(pos));
            if (player != null) {
                player.displayClientMessage(Component.translatable(second ? "easy_skyblock_island.wand.pos2" : "easy_skyblock_island.wand.pos1", pos.toShortString()), true);
                BoundingBox area = getArea(stack);
                if (area != null) {
                    player.sendSystemMessage(Component.translatable("easy_skyblock_island.wand.size", area.getXSpan(), area.getYSpan(), area.getZSpan()));
                }
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.hasTag()) {
            if (!level.isClientSide) {
                stack.removeTagKey(POS1);
                stack.removeTagKey(POS2);
                player.displayClientMessage(Component.translatable("easy_skyblock_island.wand.cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Nullable
    public static BlockPos getPos(ItemStack stack, boolean second) {
        CompoundTag tag = stack.getTag();
        String key = second ? POS2 : POS1;
        if (tag == null || !tag.contains(key)) {
            return null;
        }
        return NbtUtils.readBlockPos(tag.getCompound(key));
    }

    /** The selected box, or null until both corners are set. */
    @Nullable
    public static BoundingBox getArea(ItemStack stack) {
        BlockPos first = getPos(stack, false);
        BlockPos second = getPos(stack, true);
        if (first == null || second == null) {
            return null;
        }
        return BoundingBox.fromCorners(first, second);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        for (int i = 1; i <= 4; i++) {
            tooltip.add(Component.translatable("item.easy_skyblock_island.island_wand.tooltip." + i).withStyle(ChatFormatting.GRAY));
        }
    }
}
