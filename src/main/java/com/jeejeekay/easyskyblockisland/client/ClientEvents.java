package com.jeejeekay.easyskyblockisland.client;

import com.jeejeekay.easyskyblockisland.EasySkyblockIsland;
import com.jeejeekay.easyskyblockisland.Registration;
import com.jeejeekay.easyskyblockisland.config.EsiConfig;
import com.jeejeekay.easyskyblockisland.item.IslandWandItem;
import com.jeejeekay.easyskyblockisland.world.PendingIslandChoices;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = EasySkyblockIsland.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {

    public static final ResourceKey<WorldPreset> ISLANDS_PRESET = ResourceKey.create(Registries.WORLD_PRESET,
            new ResourceLocation(EasySkyblockIsland.MODID, "islands"));

    // Init.Post also fires on resize; only pick the default once per screen so the player's choice sticks.
    private static final Set<Screen> INITIALISED = Collections.newSetFromMap(new WeakHashMap<>());

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreateWorldScreen screen) || !INITIALISED.add(screen)) {
            return;
        }
        PendingIslandChoices.clear();
        if (!EsiConfig.load().makeDefaultWorldType) {
            return;
        }
        WorldCreationUiState state = screen.getUiState();
        state.getSettings().worldgenLoadContext().registryOrThrow(Registries.WORLD_PRESET).getHolder(ISLANDS_PRESET)
                .ifPresent(preset -> state.setWorldType(new WorldCreationUiState.WorldTypeEntry(preset)));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem().is(Registration.ISLAND_WAND.get()) ? player.getMainHandItem() : player.getOffhandItem();
        if (!stack.is(Registration.ISLAND_WAND.get())) {
            return;
        }
        BlockPos first = IslandWandItem.getPos(stack, false);
        BlockPos second = IslandWandItem.getPos(stack, true);
        if (first == null && second == null) {
            return;
        }
        BlockPos a = first != null ? first : second;
        BlockPos b = second != null ? second : first;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        double minX = Math.min(a.getX(), b.getX()) - camera.x;
        double minY = Math.min(a.getY(), b.getY()) - camera.y;
        double minZ = Math.min(a.getZ(), b.getZ()) - camera.z;
        double maxX = Math.max(a.getX(), b.getX()) + 1 - camera.x;
        double maxY = Math.max(a.getY(), b.getY()) + 1 - camera.y;
        double maxZ = Math.max(a.getZ(), b.getZ()) + 1 - camera.z;
        boolean complete = first != null && second != null;
        LevelRenderer.renderLineBox(poseStack, lines, minX, minY, minZ, maxX, maxY, maxZ,
                complete ? 0.35F : 1.0F, complete ? 0.85F : 0.8F, complete ? 1.0F : 0.3F, 1.0F);
        buffers.endBatch(RenderType.lines());
    }
}
