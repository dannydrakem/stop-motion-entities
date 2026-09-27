package dev.steppedplayeranimations.config;

import dev.steppedplayeranimations.mixin.vanilla.EntityRenderDispatcherAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MinecartRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EntityTypeConfigScreen extends Screen {
    private final Screen parent;
    private List<EntityType<?>> entityTypes = List.of();
    private EntityTypeList entityTypeList;
    private Checkbox enableAllCheckbox;
    private Checkbox disableAllCheckbox;
    private boolean synchronizingControls;

    public EntityTypeConfigScreen(Screen parent) {
        super(Component.translatable("screen.stepped_player_animations.entities.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int listWidth = Math.min(360, width - 32);
        int blockLeft = (width - listWidth) / 2;
        entityTypes = supportedEntityTypes(minecraft);

        int masterWidth = Math.max(80, listWidth / 2 - 12);
        enableAllCheckbox = addRenderableWidget(Checkbox.builder(
                Component.translatable("option.stepped_player_animations.entities.enable_all"),
                font
        ).pos(blockLeft + 8, 50)
                .selected(allEntitiesEnabled())
                .onValueChange((ignored, selected) -> {
                    if (!synchronizingControls) {
                        SteppedAnimationConfig.setEntityTypesEnabled(entityTypes, selected);
                        synchronizeControls();
                    }
                })
                .maxWidth(masterWidth)
                .build());

        disableAllCheckbox = addRenderableWidget(Checkbox.builder(
                Component.translatable("option.stepped_player_animations.entities.disable_all"),
                font
        ).pos(blockLeft + listWidth / 2 + 4, 50)
                .selected(allEntitiesDisabled())
                .onValueChange((ignored, selected) -> {
                    if (!synchronizingControls) {
                        SteppedAnimationConfig.setEntityTypesEnabled(entityTypes, !selected);
                        synchronizeControls();
                    }
                })
                .maxWidth(masterWidth)
                .build());

        entityTypeList = new EntityTypeList(
                minecraft,
                this::synchronizeControls,
                entityTypes,
                listWidth,
                Math.max(40, height - 132),
                76,
                24
        );
        entityTypeList.setX(blockLeft);
        addRenderableWidget(entityTypeList);

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> onClose()
        ).bounds(width / 2 - 100, height - 36, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int blockWidth = Math.min(360, width - 32);
        int blockLeft = (width - blockWidth) / 2;
        graphics.fill(blockLeft, 38, blockLeft + blockWidth, 72, 0x66000000);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.stepped_player_animations.entities.description"),
                width / 2,
                22,
                0xFFAAAAAA
        );
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.stepped_player_animations.entities.all_block"),
                width / 2,
                40,
                0xFFFFFFFF
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void synchronizeControls() {
        if (enableAllCheckbox == null || disableAllCheckbox == null) {
            return;
        }

        synchronizingControls = true;
        try {
            setCheckboxValue(enableAllCheckbox, allEntitiesEnabled());
            setCheckboxValue(disableAllCheckbox, allEntitiesDisabled());
            if (entityTypeList != null) {
                entityTypeList.synchronizeCheckboxes();
            }
        } finally {
            synchronizingControls = false;
        }
    }

    private boolean allEntitiesEnabled() {
        return entityTypes.stream().allMatch(SteppedAnimationConfig::isEntityTypeEnabled);
    }

    private boolean allEntitiesDisabled() {
        return entityTypes.stream().noneMatch(SteppedAnimationConfig::isEntityTypeEnabled);
    }

    private static void setCheckboxValue(Checkbox checkbox, boolean selected) {
        if (checkbox.selected() != selected) {
            checkbox.onPress();
        }
    }

    private static List<EntityType<?>> supportedEntityTypes(Minecraft minecraft) {
        Set<EntityType<?>> supportedTypes = new LinkedHashSet<>();
        supportedTypes.add(EntityType.PLAYER);
        Map<EntityType<?>, EntityRenderer<?>> renderers =
                ((EntityRenderDispatcherAccessor) minecraft.getEntityRenderDispatcher())
                        .steppedPlayerAnimations$getRenderers();
        renderers.forEach((entityType, renderer) -> {
            if (renderer instanceof LivingEntityRenderer<?, ?>
                    || renderer instanceof BoatRenderer
                    || renderer instanceof MinecartRenderer<?>) {
                supportedTypes.add(entityType);
            }
        });

        List<EntityType<?>> sortedTypes = new ArrayList<>(supportedTypes);
        sortedTypes.sort(Comparator.comparing(
                entityType -> entityType.getDescription().getString(),
                String.CASE_INSENSITIVE_ORDER
        ));
        return List.copyOf(sortedTypes);
    }

    private static final class EntityTypeList extends ContainerObjectSelectionList<EntityTypeEntry> {
        private EntityTypeList(
                Minecraft minecraft,
                Runnable onEntryChanged,
                List<EntityType<?>> entityTypes,
                int width,
                int height,
                int y,
                int itemHeight
        ) {
            super(minecraft, width, height, y, itemHeight);
            centerListVertically = false;
            entityTypes.forEach(entityType -> addEntry(
                    new EntityTypeEntry(minecraft, entityType, width - 28, onEntryChanged)
            ));
        }

        private void synchronizeCheckboxes() {
            children().forEach(EntityTypeEntry::synchronizeCheckbox);
        }

        @Override
        public int getRowWidth() {
            return width - 12;
        }

        @Override
        protected int getScrollbarPosition() {
            return getRight() - 6;
        }
    }

    private static final class EntityTypeEntry extends ContainerObjectSelectionList.Entry<EntityTypeEntry> {
        private final EntityType<?> entityType;
        private final Checkbox checkbox;
        private boolean synchronizing;

        private EntityTypeEntry(
                Minecraft minecraft,
                EntityType<?> entityType,
                int maxWidth,
                Runnable onChanged
        ) {
            this.entityType = entityType;
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            Component tooltipText = entityId == null
                    ? entityType.getDescription()
                    : Component.literal(entityId.toString());
            checkbox = Checkbox.builder(entityType.getDescription(), minecraft.font)
                    .selected(SteppedAnimationConfig.isEntityTypeEnabled(entityType))
                    .onValueChange((ignored, selected) -> {
                        if (!synchronizing) {
                            SteppedAnimationConfig.setEntityTypeEnabled(entityType, selected);
                            onChanged.run();
                        }
                    })
                    .tooltip(Tooltip.create(tooltipText))
                    .maxWidth(maxWidth)
                    .build();
        }

        private void synchronizeCheckbox() {
            boolean selected = SteppedAnimationConfig.isEntityTypeEnabled(entityType);
            if (checkbox.selected() != selected) {
                synchronizing = true;
                try {
                    checkbox.onPress();
                } finally {
                    synchronizing = false;
                }
            }
        }

        @Override
        public void render(
                GuiGraphics graphics,
                int index,
                int top,
                int left,
                int width,
                int height,
                int mouseX,
                int mouseY,
                boolean hovered,
                float partialTick
        ) {
            checkbox.setX(left + 4);
            checkbox.setY(top + Math.max(0, (height - checkbox.getHeight()) / 2));
            checkbox.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(checkbox);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(checkbox);
        }
    }
}
