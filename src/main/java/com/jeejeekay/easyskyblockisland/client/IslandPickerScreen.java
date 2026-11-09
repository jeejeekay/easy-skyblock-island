package com.jeejeekay.easyskyblockisland.client;

import com.jeejeekay.easyskyblockisland.config.EsiConfig;
import com.jeejeekay.easyskyblockisland.island.IslandTemplates;
import com.jeejeekay.easyskyblockisland.world.PendingIslandChoices;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lets the player pick which saved island each dimension gets in the world being created.
 * Defaults come from the global config; the picks are stored in the new world's own settings.
 */
public class IslandPickerScreen extends Screen {

    private static final String NONE = "";

    private final Screen parent;

    public IslandPickerScreen(Screen parent) {
        super(Component.translatable("easy_skyblock_island.picker.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        EsiConfig config = EsiConfig.load();
        List<String> islands = new ArrayList<>();
        islands.add(NONE);
        islands.addAll(IslandTemplates.listNames());

        int y = 48;
        int width = 260;
        int x = (this.width - width) / 2;
        for (Map.Entry<String, EsiConfig.Dimension> entry : config.dimensions.entrySet()) {
            String dimension = entry.getKey();
            EsiConfig.Dimension settings = entry.getValue();
            if (!settings.enabled) {
                continue;
            }
            String current = PendingIslandChoices.get(dimension, settings.island.template);
            List<String> values = new ArrayList<>(islands);
            if (!values.contains(current)) {
                values.add(current);
            }
            this.addRenderableWidget(CycleButton.<String>builder(name -> name.isEmpty()
                            ? Component.translatable("easy_skyblock_island.picker.none")
                            : Component.literal(name))
                    .withValues(values)
                    .withInitialValue(current)
                    .create(x, y, width, 20, Component.translatable("easy_skyblock_island.picker.island", dimensionName(dimension)),
                            (button, value) -> PendingIslandChoices.set(dimension, value)));
            y += 24;
        }

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds((this.width - 200) / 2, this.height - 28, 200, 20)
                .build());
    }

    private static String dimensionName(String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) {
            return dimension;
        }
        String path = id.getPath().startsWith("the_") ? id.getPath().substring(4) : id.getPath();
        String[] words = path.split("[_/]");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return id.getNamespace().equals("minecraft") ? name.toString() : name + " (" + id.getNamespace() + ")";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("easy_skyblock_island.picker.hint"), this.width / 2, 30, 0xA0A0A0);
        if (this.children().size() <= 1) {
            graphics.drawCenteredString(this.font, Component.translatable("easy_skyblock_island.picker.empty"), this.width / 2, 60, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
