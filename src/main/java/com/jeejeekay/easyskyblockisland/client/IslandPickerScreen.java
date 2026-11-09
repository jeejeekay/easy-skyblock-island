package com.jeejeekay.easyskyblockisland.client;

import com.jeejeekay.easyskyblockisland.config.EsiConfig;
import com.jeejeekay.easyskyblockisland.island.IslandTemplates;
import com.jeejeekay.easyskyblockisland.world.PendingIslandChoices;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lets the player pick which saved island each dimension gets in the world being created, with a
 * spinning 3D preview of the selected island (drag it to turn it). Defaults come from the global
 * config; the picks are stored in the new world's own settings.
 */
public class IslandPickerScreen extends Screen {

    private static final String NONE = "";
    private static final int ROW_WIDTH = 200;
    private static final int GAP = 12;

    private final Screen parent;
    private final Map<String, Optional<IslandPreview>> previews = new HashMap<>();

    private String previewDimension;
    private String previewIsland = NONE;
    private float yaw = 30.0F;
    private float pitch = 25.0F;
    private long lastDrag;
    private long lastFrame = Util.getMillis();
    private final List<Map.Entry<CycleButton<String>, String>> rows = new ArrayList<>();

    private int boxX;
    private int boxY;
    private int boxSize;

    public IslandPickerScreen(Screen parent) {
        super(Component.translatable("easy_skyblock_island.picker.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        EsiConfig config = EsiConfig.load();
        this.rows.clear();
        List<String> islands = new ArrayList<>();
        islands.add(NONE);
        islands.addAll(IslandTemplates.listNames());

        this.boxSize = Math.max(80, Math.min(this.width - ROW_WIDTH - GAP * 3, this.height - 100));
        int left = (this.width - ROW_WIDTH - GAP - this.boxSize) / 2;
        this.boxX = left + ROW_WIDTH + GAP;
        this.boxY = 48;

        int y = this.boxY;
        for (Map.Entry<String, EsiConfig.Dimension> entry : config.dimensions.entrySet()) {
            String dimension = entry.getKey();
            EsiConfig.Dimension settings = entry.getValue();
            if (!settings.enabled) {
                continue;
            }
            String current = PendingIslandChoices.get(dimension, settings.island.template);
            if (this.previewDimension == null) {
                this.previewDimension = dimension;
                this.previewIsland = current;
            }
            List<String> values = new ArrayList<>(islands);
            if (!values.contains(current)) {
                values.add(current);
            }
            CycleButton<String> row = this.addRenderableWidget(CycleButton.<String>builder(name -> name.isEmpty()
                            ? Component.translatable("easy_skyblock_island.picker.none")
                            : Component.literal(name))
                    .withValues(values)
                    .withInitialValue(current)
                    .create(left, y, ROW_WIDTH, 20, Component.translatable("easy_skyblock_island.picker.island", dimensionName(dimension)),
                            (button, value) -> {
                                PendingIslandChoices.set(dimension, value);
                                this.showPreview(dimension, value);
                            }));
            this.rows.add(Map.entry(row, dimension));
            y += 24;
        }

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds((this.width - 200) / 2, this.height - 28, 200, 20)
                .build());
    }

    private void showPreview(String dimension, String island) {
        this.previewDimension = dimension;
        this.previewIsland = island;
    }

    @Nullable
    private IslandPreview preview(String island) {
        if (island == null || island.isEmpty()) {
            return null;
        }
        return this.previews.computeIfAbsent(island, name -> Optional.ofNullable(IslandPreview.load(name))).orElse(null);
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

    private boolean inPreview(double mouseX, double mouseY) {
        return mouseX >= this.boxX && mouseX < this.boxX + this.boxSize && mouseY >= this.boxY && mouseY < this.boxY + this.boxSize;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.inPreview(mouseX, mouseY)) {
            this.yaw += (float) dragX * 1.5F;
            this.pitch = Mth.clamp(this.pitch + (float) dragY * 1.5F, -10.0F, 90.0F);
            this.lastDrag = Util.getMillis();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("easy_skyblock_island.picker.hint"), this.width / 2, 26, 0xA0A0A0);
        if (this.previewDimension == null) {
            graphics.drawCenteredString(this.font, Component.translatable("easy_skyblock_island.picker.empty"), this.width / 2, 60, 0xFFFFFF);
        }

        // Hovering a row previews that dimension's island.
        for (Map.Entry<CycleButton<String>, String> row : this.rows) {
            if (row.getKey().isHovered()) {
                this.showPreview(row.getValue(), row.getKey().getValue());
            }
        }

        // Preview panel
        graphics.fill(this.boxX - 1, this.boxY - 1, this.boxX + this.boxSize + 1, this.boxY + this.boxSize + 1, 0xFF555555);
        graphics.fill(this.boxX, this.boxY, this.boxX + this.boxSize, this.boxY + this.boxSize, 0xFF101418);
        IslandPreview preview = this.preview(this.previewIsland);
        int textX = this.boxX + this.boxSize / 2;
        if (preview != null) {
            long now = Util.getMillis();
            if (now - this.lastDrag > 2000) {
                this.yaw += (now - this.lastFrame) * 0.02F; // gentle spin (20 degrees a second) when not being dragged
            }
            this.lastFrame = now;
            preview.render(graphics, this.boxX, this.boxY, this.boxSize, this.boxSize, this.yaw, this.pitch);
            Component info = Component.translatable("easy_skyblock_island.picker.size",
                    preview.size().getX(), preview.size().getY(), preview.size().getZ(), preview.totalBlocks());
            graphics.drawCenteredString(this.font, info, textX, this.boxY + this.boxSize + 4, 0xA0A0A0);
        } else {
            Component message = this.previewIsland.isEmpty()
                    ? Component.translatable("easy_skyblock_island.picker.none")
                    : Component.translatable("easy_skyblock_island.picker.missing", this.previewIsland);
            graphics.drawCenteredString(this.font, message, textX, this.boxY + this.boxSize / 2 - 4, 0x808080);
        }
        if (this.previewDimension != null) {
            graphics.drawCenteredString(this.font, Component.translatable("easy_skyblock_island.picker.island", dimensionName(this.previewDimension)),
                    textX, this.boxY - 11, 0xFFFFFF);
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
