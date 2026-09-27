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

    public EntityTypeConfigScreen(Screen parent) {
        super(Component.translatable("screen.stepped_player_animations.entities.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int listWidth = Math.min(360, width - 32);
        EntityTypeList list = new EntityTypeList(
                minecraft,
                listWidth,
                Math.max(40, height - 104),
                48,
                24
        );
        list.setX((width - listWidth) / 2);
        addRenderableWidget(list);

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> onClose()
        ).bounds(width / 2 - 100, height - 36, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFFFF);
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.stepped_player_animations.entities.description"),
                width / 2,
                30,
                0xFFAAAAAA
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private static final class EntityTypeList extends ContainerObjectSelectionList<EntityTypeEntry> {
        private EntityTypeList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
            centerListVertically = false;

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
            sortedTypes.forEach(entityType -> addEntry(new EntityTypeEntry(minecraft, entityType, width - 28)));
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
        private final Checkbox checkbox;

        private EntityTypeEntry(Minecraft minecraft, EntityType<?> entityType, int maxWidth) {
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            Component tooltipText = entityId == null
                    ? entityType.getDescription()
                    : Component.literal(entityId.toString());
            checkbox = Checkbox.builder(entityType.getDescription(), minecraft.font)
                    .selected(SteppedAnimationConfig.isEntityTypeEnabled(entityType))
                    .onValueChange((ignored, selected) ->
                            SteppedAnimationConfig.setEntityTypeEnabled(entityType, selected)
                    )
                    .tooltip(Tooltip.create(tooltipText))
                    .maxWidth(maxWidth)
                    .build();
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
