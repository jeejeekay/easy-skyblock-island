package com.jeejeekay.easyskyblockisland.client;

import com.jeejeekay.easyskyblockisland.EasySkyblockIsland;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterPresetEditorsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EasySkyblockIsland.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {

    private ClientModEvents() {
    }

    /** The "Customize" button on the Create World screen opens the island picker for our world type. */
    @SubscribeEvent
    public static void onRegisterPresetEditors(RegisterPresetEditorsEvent event) {
        event.register(ClientEvents.ISLANDS_PRESET, (parent, context) -> new IslandPickerScreen(parent));
    }
}
