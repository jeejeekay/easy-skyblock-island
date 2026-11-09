package com.jeejeekay.easyskyblockisland;

import com.jeejeekay.easyskyblockisland.block.SpawnMarkerBlock;
import com.jeejeekay.easyskyblockisland.island.KeepExistingProcessor;
import com.jeejeekay.easyskyblockisland.item.BoundaryMarkerItem;
import com.jeejeekay.easyskyblockisland.item.IslandWandItem;
import com.jeejeekay.easyskyblockisland.world.LayeredVoidChunkGenerator;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class Registration {

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, EasySkyblockIsland.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EasySkyblockIsland.MODID);
    private static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, EasySkyblockIsland.MODID);
    private static final DeferredRegister<StructureProcessorType<?>> PROCESSORS = DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, EasySkyblockIsland.MODID);

    public static final RegistryObject<Block> SPAWN_MARKER = BLOCKS.register("spawn_marker", () -> new SpawnMarkerBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .noCollission()
                    .noOcclusion()
                    .instabreak()
                    .sound(SoundType.WOOL)
                    .pushReaction(PushReaction.DESTROY)));

    public static final RegistryObject<Item> SPAWN_MARKER_ITEM = ITEMS.register("spawn_marker", () -> new BlockItem(SPAWN_MARKER.get(), new Item.Properties()));

    /** Marks selection corners in the void. Never saved into islands. */
    public static final RegistryObject<Block> BOUNDARY_MARKER = BLOCKS.register("boundary_marker", () -> new Block(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .noOcclusion()
                    .instabreak()
                    .sound(SoundType.GLASS)
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)));

    public static final RegistryObject<Item> BOUNDARY_MARKER_ITEM = ITEMS.register("boundary_marker", () -> new BoundaryMarkerItem(BOUNDARY_MARKER.get(), new Item.Properties()));

    public static final RegistryObject<Item> ISLAND_WAND = ITEMS.register("island_wand", () -> new IslandWandItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Codec<LayeredVoidChunkGenerator>> LAYERED_GENERATOR = CHUNK_GENERATORS.register("layered", () -> LayeredVoidChunkGenerator.CODEC);

    public static final RegistryObject<StructureProcessorType<KeepExistingProcessor>> KEEP_EXISTING = PROCESSORS.register("keep_existing", Registration::keepExistingType);

    private Registration() {
    }

    private static StructureProcessorType<KeepExistingProcessor> keepExistingType() {
        return () -> KeepExistingProcessor.CODEC;
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        CHUNK_GENERATORS.register(modBus);
        PROCESSORS.register(modBus);
    }
}
