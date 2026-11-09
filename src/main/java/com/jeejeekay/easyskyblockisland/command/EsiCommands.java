package com.jeejeekay.easyskyblockisland.command;

import com.jeejeekay.easyskyblockisland.Registration;
import com.jeejeekay.easyskyblockisland.config.EsiConfig;
import com.jeejeekay.easyskyblockisland.island.IslandPlacer;
import com.jeejeekay.easyskyblockisland.island.IslandSavedData;
import com.jeejeekay.easyskyblockisland.island.IslandTemplates;
import com.jeejeekay.easyskyblockisland.item.IslandWandItem;
import com.jeejeekay.easyskyblockisland.world.LayeredVoidChunkGenerator;
import com.jeejeekay.easyskyblockisland.world.WorldSetup;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.IOException;

/**
 * {@code /esi save <name>} saves the Island Wand selection as an island,
 * {@code /esi list} lists islands, {@code /esi place <name> [pos]} places one,
 * {@code /esi status} shows what the mod is doing in the current dimension.
 */
public final class EsiCommands {

    private static final SuggestionProvider<CommandSourceStack> ISLAND_NAMES =
            (context, builder) -> SharedSuggestionProvider.suggest(IslandTemplates.listNames(), builder);

    private EsiCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("esi")
                .then(Commands.literal("save")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(ISLAND_NAMES)
                                .executes(context -> save(context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("place")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(ISLAND_NAMES)
                                .executes(context -> place(context.getSource(), StringArgumentType.getString(context, "name"),
                                        BlockPos.containing(context.getSource().getPosition())))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> place(context.getSource(), StringArgumentType.getString(context, "name"),
                                                BlockPosArgument.getLoadedBlockPos(context, "pos"))))))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource()))));
    }

    private static int save(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!IslandTemplates.isValidName(name)) {
            source.sendFailure(Component.translatable("easy_skyblock_island.command.bad_name"));
            return 0;
        }
        ItemStack wand = player.getMainHandItem().is(Registration.ISLAND_WAND.get()) ? player.getMainHandItem() : player.getOffhandItem();
        BoundingBox area = wand.is(Registration.ISLAND_WAND.get()) ? IslandWandItem.getArea(wand) : null;
        if (area == null) {
            source.sendFailure(Component.translatable("easy_skyblock_island.command.no_wand"));
            return 0;
        }
        BlockPos min = new BlockPos(area.minX(), area.minY(), area.minZ());
        Vec3i size = new Vec3i(area.getXSpan(), area.getYSpan(), area.getZSpan());

        StructureTemplate template = new StructureTemplate();
        // Air (and, when saving, boundary markers) is left out so a placed island never carves into water or terrain around it.
        template.fillFromWorld(player.level(), min, size, false, Blocks.AIR);
        template.setAuthor(player.getName().getString());
        try {
            IslandTemplates.save(name, template);
        } catch (IOException e) {
            source.sendFailure(Component.translatable("easy_skyblock_island.command.save_failed", name, e.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("easy_skyblock_island.command.saved", name, size.getX(), size.getY(), size.getZ(),
                IslandTemplates.fileFor(name).toString()), true);
        if (!IslandPlacer.anchor(template).hasMarker()) {
            source.sendSuccess(() -> Component.translatable("easy_skyblock_island.command.no_marker").withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int list(CommandSourceStack source) {
        var names = IslandTemplates.listNames();
        if (names.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("easy_skyblock_island.command.list_empty"), false);
        } else {
            source.sendSuccess(() -> Component.translatable("easy_skyblock_island.command.list", String.join(", ", names)), false);
        }
        return names.size();
    }

    private static int place(CommandSourceStack source, String name, BlockPos pos) {
        StructureTemplate template = IslandTemplates.load(source.getServer(), name);
        if (template == null) {
            source.sendFailure(Component.translatable("easy_skyblock_island.command.missing", name));
            return 0;
        }
        ServerLevel level = source.getLevel();
        IslandPlacer.Placed placed = IslandPlacer.place(level, template, pos);
        source.sendSuccess(() -> Component.translatable("easy_skyblock_island.command.placed", name, placed.spawn().toShortString()), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        EsiConfig snapshot = WorldSetup.snapshot();
        if (snapshot == null) {
            source.sendSuccess(() -> Component.translatable("easy_skyblock_island.command.not_islands_world"), false);
            return 0;
        }
        ServerLevel level = source.getLevel();
        String dimension = level.dimension().location().toString();
        String generator = level.getChunkSource().getGenerator() instanceof LayeredVoidChunkGenerator ? "layered" : "normal";
        EsiConfig.Dimension settings = snapshot.dimensions.get(dimension);
        String island;
        IslandPlacer.Placed placed = IslandSavedData.get(source.getServer()).island(dimension);
        if (placed != null) {
            island = "placed at " + placed.spawn().toShortString();
        } else if (settings != null && settings.enabled && !settings.island.template.isBlank()) {
            island = "'" + settings.island.template + "' (" + settings.island.placement + ", not placed yet)";
        } else {
            island = "none";
        }
        source.sendSuccess(() -> Component.translatable("easy_skyblock_island.command.status", dimension, generator, island), false);
        return 1;
    }
}
