package com.jeejeekay.easyskyblockisland;

import com.jeejeekay.easyskyblockisland.config.EsiConfig;
import com.jeejeekay.easyskyblockisland.island.IslandTemplates;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(EasySkyblockIsland.MODID)
public class EasySkyblockIsland {

    public static final String MODID = "easy_skyblock_island";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EasySkyblockIsland() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        Registration.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::addCreativeTabItems);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Write the default config and bundled islands on first launch so they can be edited.
            EsiConfig.load();
            IslandTemplates.installBundledIslands();
        });
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(Registration.ISLAND_WAND);
            event.accept(Registration.SPAWN_MARKER_ITEM);
            event.accept(Registration.BOUNDARY_MARKER_ITEM);
        }
    }
}
