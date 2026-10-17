package com.endersjourney.btesimplehats.client;

import com.endersjourney.btesimplehats.HatsConfig;
import com.endersjourney.btesimplehats.hat.HatCatalog;
import com.endersjourney.btesimplehats.network.EquipHatPacket;
import com.endersjourney.btesimplehats.network.ModNetwork;
import com.endersjourney.btesimplehats.network.StoreInventoryHatsPacket;
import com.endersjourney.btesimplehats.network.UnequipHatPacket;
import com.endersjourney.btesimplehats.wardrobe.HatSelection;
import com.endersjourney.btesimplehats.wardrobe.VirtualHats;
import com.mojang.blaze3d.vertex.PoseStack;
import fonnymunkey.simplehats.common.item.HatItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Armario de gorros.
 *
 * <pre>
 * +-----------------------------------------------------------+
 * | Armario de gorros                               47/311    |
 * | +----------+  [buscar.............] [Todos     ]          |
 * | |          |  [][][][][][][][][][][]  |                   |
 * | | jugador  |  [][][][][][][][][][][]  | scroll            |
 * | | (prueba) |  ...  7 filas x 11       |                   |
 * | +----------+  Nombre del gorro (color de rareza)          |
 * | [Equipar  ]   < Variante 2/4 >                            |
 * | [Quitar   ]   [colores para teñibles]                     |
 * | [Slot: ...]                                               |
 * | [Guardar  ]                                               |
 * +-----------------------------------------------------------+
 * </pre>
 *
 * El muñeco de la izquierda se "prueba" el gorro seleccionado: durante el
 * render se mete el stack en el slot cosmetico del jugador LOCAL (solo en
 * el cliente) y se restaura justo despues. No toca el servidor.
 */
public class WardrobeScreen extends Screen {
    private static final int W = 320;
    private static final int H = 214;
    private static final int CELL = 18;
    private static final int COLS = 11;
    private static final int ROWS = 7;
    private static final int GRID_X = 104;
    private static final int GRID_Y = 38;
    private static final int SCROLL_X = GRID_X + COLS * CELL + 2;
    private static final int SCROLL_W = 8;
    private static final int PREVIEW_X = 8;
    private static final int PREVIEW_Y = 20;
    private static final int PREVIEW_W = 88;
    private static final int PREVIEW_H = 98;
    private static final int DETAIL_Y = GRID_Y + ROWS * CELL + 4;
    private static final int VARIANT_Y = DETAIL_Y + 12;
    private static final int SWATCH_Y = DETAIL_Y + 27;
    private static final int SWATCH = 11;
    private static final int TEXT = 0x404040;
    private static final long DOUBLE_CLICK_MS = 300;

    private enum Filter { ALL, UNLOCKED, LOCKED }

    // Se recuerdan entre aperturas del armario (solo durante la sesion).
    private static String lastSearch = "";
    private static Filter lastFilter = Filter.ALL;

    private int left;
    private int top;
    private EditBox search;
    private Button filterButton;
    private Button equipButton;
    private Button unequipButton;
    private Button slotButton;
    private Button storeButton;
    private Button prevVariant;
    private Button nextVariant;

    private List<HatItem> filtered = new ArrayList<>();
    private int scroll;
    private boolean draggingScroll;

    @Nullable
    private HatItem selected;
    private int selVariant;
    private int selColor = -1;
    private boolean targetCosmetic = true;

    @Nullable
    private HatItem lastClicked;
    private long lastClickTime;

    public WardrobeScreen() {
        super(Component.translatable("bte_simplehats_addon.screen.title"));
        HatSelection eq = ClientWardrobe.equipped();
        if (eq != null) {
            HatItem hat = HatCatalog.get(eq.hat());
            if (hat != null) {
                selected = hat;
                selVariant = eq.variant();
                selColor = eq.color();
                targetCosmetic = eq.cosmetic();
            }
        }
        if (!HatsConfig.allowHeadSlot()) {
            targetCosmetic = true;
        }
    }

    // ------------------------------------------------------------------
    // Init / estado
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;

        search = new EditBox(font, left + GRID_X + 1, top + 21, 136, 12, Component.translatable("bte_simplehats_addon.screen.search"));
        search.setMaxLength(50);
        search.setValue(lastSearch);
        search.setResponder(value -> {
            lastSearch = value;
            search.setSuggestion(value.isEmpty() ? Component.translatable("bte_simplehats_addon.screen.search").getString() : "");
            scroll = 0;
            refilter();
        });
        search.setSuggestion(lastSearch.isEmpty() ? Component.translatable("bte_simplehats_addon.screen.search").getString() : "");
        addRenderableWidget(search);

        filterButton = addRenderableWidget(new Button(left + GRID_X + 142, top + 19, 66, 16, filterLabel(), b -> cycleFilter()));

        int bx = left + PREVIEW_X;
        int by = top + PREVIEW_Y + PREVIEW_H + 4;
        equipButton = addRenderableWidget(new Button(bx, by, PREVIEW_W, 20,
                Component.translatable("bte_simplehats_addon.screen.equip"), b -> equipSelected()));
        unequipButton = addRenderableWidget(new Button(bx, by + 22, PREVIEW_W, 20,
                Component.translatable("bte_simplehats_addon.screen.unequip"), b -> ModNetwork.sendToServer(new UnequipHatPacket())));
        slotButton = addRenderableWidget(new Button(bx, by + 44, PREVIEW_W, 20, slotLabel(), b -> {
            targetCosmetic = !targetCosmetic;
            b.setMessage(slotLabel());
        }));
        storeButton = addRenderableWidget(new Button(bx, by + 66, PREVIEW_W, 20,
                Component.translatable("bte_simplehats_addon.screen.store"), b -> ModNetwork.sendToServer(new StoreInventoryHatsPacket()),
                (b, poseStack, mx, my) -> renderTooltip(poseStack, Component.translatable("bte_simplehats_addon.screen.store.tooltip"), mx, my)));

        prevVariant = addRenderableWidget(new Button(left + GRID_X, top + VARIANT_Y, 12, 12, Component.literal("<"), b -> changeVariant(-1)));
        nextVariant = addRenderableWidget(new Button(left + GRID_X + 96, top + VARIANT_Y, 12, 12, Component.literal(">"), b -> changeVariant(1)));

        refilter();
        updateButtons();
    }

    /** Lo llama ClientWardrobe cuando llega un sync del servidor. */
    public void onWardrobeChanged() {
        refilter();
        updateButtons();
    }

    private void refilter() {
        String query = lastSearch.trim().toLowerCase(Locale.ROOT);
        boolean showLocked = HatsConfig.showLockedHats();
        List<HatItem> out = new ArrayList<>();
        for (HatItem hat : HatCatalog.all()) {
            boolean unlocked = ClientWardrobe.isUnlocked(HatCatalog.id(hat));
            if (!unlocked && (!showLocked || lastFilter == Filter.UNLOCKED)) continue;
            if (unlocked && lastFilter == Filter.LOCKED) continue;
            if (!query.isEmpty()) {
                // Los bloqueados no se buscan por nombre: no queremos destriparlos.
                if (!unlocked) continue;
                String name = hat.getDescription().getString().toLowerCase(Locale.ROOT);
                if (!name.contains(query) && !HatCatalog.id(hat).getPath().contains(query)) continue;
            }
            out.add(hat);
        }
        out.sort(Comparator.comparingInt((HatItem h) -> h.getHatEntry().getHatRarity().ordinal())
                .thenComparing(h -> h.getDescription().getString()));
        filtered = out;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private void updateButtons() {
        if (equipButton == null) return;
        boolean unlocked = selected != null && ClientWardrobe.isUnlocked(HatCatalog.id(selected));
        equipButton.active = unlocked && !isSelectionEquipped();
        unequipButton.active = ClientWardrobe.equipped() != null;
        slotButton.active = HatsConfig.allowHeadSlot();
        if (!slotButton.active && !targetCosmetic) {
            targetCosmetic = true;
            slotButton.setMessage(slotLabel());
        }
        storeButton.active = inventoryHasHats();
        boolean variants = unlocked && HatCatalog.variantRange(selected) > 0;
        prevVariant.visible = variants;
        nextVariant.visible = variants;
    }

    @Override
    public void tick() {
        search.tick();
        updateButtons();
    }

    private Component filterLabel() {
        return Component.translatable("bte_simplehats_addon.screen.filter." + lastFilter.name().toLowerCase(Locale.ROOT));
    }

    private Component slotLabel() {
        return Component.translatable(targetCosmetic ? "bte_simplehats_addon.screen.slot.cosmetic" : "bte_simplehats_addon.screen.slot.head");
    }

    private void cycleFilter() {
        Filter next = Filter.values()[(lastFilter.ordinal() + 1) % Filter.values().length];
        if (next == Filter.LOCKED && !HatsConfig.showLockedHats()) {
            next = Filter.ALL;
        }
        lastFilter = next;
        filterButton.setMessage(filterLabel());
        scroll = 0;
        refilter();
    }

    private void select(HatItem hat) {
        selected = hat;
        HatSelection eq = ClientWardrobe.equipped();
        if (eq != null && eq.hat().equals(HatCatalog.id(hat))) {
            selVariant = eq.variant();
            selColor = eq.color();
        } else {
            selVariant = 0;
            selColor = -1;
        }
        updateButtons();
    }

    private void changeVariant(int delta) {
        if (selected == null) return;
        int count = HatCatalog.variantRange(selected) + 1;
        selVariant = Math.floorMod(selVariant + delta, count);
        updateButtons();
    }

    private void equipSelected() {
        if (selected == null || !ClientWardrobe.isUnlocked(HatCatalog.id(selected))) return;
        int color = HatCatalog.isDyeable(selected) ? selColor : -1;
        ModNetwork.sendToServer(new EquipHatPacket(new HatSelection(HatCatalog.id(selected), selVariant, color, targetCosmetic)));
    }

    private boolean isSelectionEquipped() {
        HatSelection eq = ClientWardrobe.equipped();
        if (eq == null || selected == null) return false;
        int color = HatCatalog.isDyeable(selected) ? selColor : -1;
        return eq.hat().equals(HatCatalog.id(selected)) && eq.variant() == selVariant
                && eq.color() == color && eq.cosmetic() == targetCosmetic;
    }

    private boolean inventoryHasHats() {
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        if (player == null) return false;
        Inventory inv = player.getInventory();
        for (ItemStack s : inv.items) {
            if (s.getItem() instanceof HatItem && !VirtualHats.isVirtual(s)) return true;
        }
        for (ItemStack s : inv.offhand) {
            if (s.getItem() instanceof HatItem && !VirtualHats.isVirtual(s)) return true;
        }
        return false;
    }

    private int totalRows() {
        return (filtered.size() + COLS - 1) / COLS;
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - ROWS);
    }

    @Nullable
    private ItemStack previewStack() {
        if (selected == null || !ClientWardrobe.isUnlocked(HatCatalog.id(selected))) return null;
        int color = HatCatalog.isDyeable(selected) ? selColor : -1;
        return HatCatalog.buildStack(selected, selVariant, color, minecraft == null ? null : minecraft.player);
    }

    // ------------------------------------------------------------------
    // Render
    // ------------------------------------------------------------------

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        drawPanel(poseStack, left, top, left + W, top + H);

        font.draw(poseStack, title, left + 8, top + 7, TEXT);
        String count = ClientWardrobe.countUnlocked() + "/" + HatCatalog.total();
        font.draw(poseStack, count, left + W - 8 - font.width(count), top + 7, TEXT);

        renderPreview(poseStack, mouseX, mouseY);
        HatItem hovered = renderGrid(poseStack, mouseX, mouseY);
        renderScrollbar(poseStack);
        int hoveredSwatch = renderDetail(poseStack, mouseX, mouseY);

        super.render(poseStack, mouseX, mouseY, partialTick);

        if (hovered != null) {
            renderComponentTooltip(poseStack, hatTooltip(hovered), mouseX, mouseY);
        } else if (hoveredSwatch >= 0) {
            Component name = hoveredSwatch == 0
                    ? Component.translatable("bte_simplehats_addon.screen.color.default")
                    : Component.translatable("color.minecraft." + DyeColor.values()[hoveredSwatch - 1].getName());
            renderTooltip(poseStack, name, mouseX, mouseY);
        }
    }

    private void renderPreview(PoseStack poseStack, int mouseX, int mouseY) {
        int x0 = left + PREVIEW_X;
        int y0 = top + PREVIEW_Y;
        drawSlotFrame(poseStack, x0, y0, PREVIEW_W, PREVIEW_H);
        fill(poseStack, x0 + 1, y0 + 1, x0 + PREVIEW_W - 1, y0 + PREVIEW_H - 1, 0xFF000000);

        LocalPlayer player = minecraft == null ? null : minecraft.player;
        if (player == null) return;

        int cx = x0 + PREVIEW_W / 2;
        int cy = y0 + PREVIEW_H - 10;
        ItemStack preview = previewStack();

        IDynamicStackHandler cosmetic = null;
        ItemStack previous = ItemStack.EMPTY;
        if (preview != null) {
            Optional<ICurioStacksHandler> head = VirtualHats.headSlots(player);
            if (head.isPresent() && head.get().getCosmeticStacks().getSlots() > 0) {
                cosmetic = head.get().getCosmeticStacks();
                previous = cosmetic.getStackInSlot(0);
                cosmetic.setStackInSlot(0, preview);
            }
        }
        try {
            InventoryScreen.renderEntityInInventory(cx, cy, 36, (float) cx - mouseX, (float) (cy - 60) - mouseY, player);
        } finally {
            if (cosmetic != null) {
                cosmetic.setStackInSlot(0, previous);
            }
        }
    }

    @Nullable
    private HatItem renderGrid(PoseStack poseStack, int mouseX, int mouseY) {
        HatItem hovered = null;
        HatSelection eq = ClientWardrobe.equipped();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int x = left + GRID_X + col * CELL;
                int y = top + GRID_Y + row * CELL;
                boolean hover = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
                drawSlot(poseStack, x, y, hover);

                int index = (scroll + row) * COLS + col;
                if (index >= filtered.size()) continue;
                HatItem hat = filtered.get(index);
                ResourceLocation id = HatCatalog.id(hat);
                boolean unlocked = ClientWardrobe.isUnlocked(id);

                ItemStack stack = new ItemStack(hat);
                ClientRender.renderItem(stack, x + 1, y + 1, 1.0f, !unlocked);

                if (eq != null && eq.hat().equals(id)) {
                    outline(poseStack, x, y, CELL, CELL, 0xFF3FCF3F);
                }
                if (hat == selected) {
                    outline(poseStack, x, y, CELL, CELL, 0xFFFFD700);
                }
                if (hover) hovered = hat;
            }
        }
        return hovered;
    }

    private void renderScrollbar(PoseStack poseStack) {
        int x = left + SCROLL_X;
        int y = top + GRID_Y;
        int h = ROWS * CELL;
        fill(poseStack, x, y, x + SCROLL_W, y + h, 0xFF373737);
        int max = maxScroll();
        int thumbH = max == 0 ? h : Math.max(12, h * ROWS / totalRows());
        int thumbY = max == 0 ? y : y + (h - thumbH) * scroll / max;
        int color = max == 0 ? 0xFF8B8B8B : 0xFFC6C6C6;
        fill(poseStack, x + 1, thumbY + 1, x + SCROLL_W - 1, thumbY + thumbH - 1, color);
    }

    /** @return el indice de color bajo el raton (0 = original, 1..16 = DyeColor), o -1. */
    private int renderDetail(PoseStack poseStack, int mouseX, int mouseY) {
        int x = left + GRID_X;
        int y = top + DETAIL_Y;
        if (selected == null) {
            font.draw(poseStack, Component.translatable("bte_simplehats_addon.screen.hint").withStyle(ChatFormatting.DARK_GRAY), x, y, TEXT);
            return -1;
        }
        boolean unlocked = ClientWardrobe.isUnlocked(HatCatalog.id(selected));
        if (!unlocked) {
            font.draw(poseStack, Component.literal("???"), x, y, TEXT);
            font.draw(poseStack, Component.translatable("bte_simplehats_addon.screen.locked_hint"), x, y + 12, 0x606060);
            return -1;
        }

        String name = font.plainSubstrByWidth(selected.getDescription().getString(), COLS * CELL + SCROLL_W);
        font.draw(poseStack, Component.literal(name).withStyle(selected.getHatEntry().getHatRarity().color), x, y, 0xFFFFFF);

        int range = HatCatalog.variantRange(selected);
        if (range > 0) {
            Component label = Component.translatable("bte_simplehats_addon.screen.variant", selVariant + 1, range + 1);
            int labelX = x + 12 + (96 - 12 - font.width(label)) / 2;
            font.draw(poseStack, label, labelX, top + VARIANT_Y + 2, TEXT);
        }

        int hoveredSwatch = -1;
        if (HatCatalog.isDyeable(selected)) {
            DyeColor[] dyes = DyeColor.values();
            for (int i = 0; i <= dyes.length; i++) {
                int sx = x + i * SWATCH;
                int sy = top + SWATCH_Y;
                int rgb = i == 0 ? HatCatalog.defaultColor(selected) : dyeRgb(dyes[i - 1]);
                boolean chosen = (i == 0 && selColor < 0) || (i > 0 && selColor == dyeRgb(dyes[i - 1]));
                fill(poseStack, sx, sy, sx + SWATCH - 1, sy + SWATCH - 1, chosen ? 0xFFFFD700 : 0xFF373737);
                fill(poseStack, sx + 1, sy + 1, sx + SWATCH - 2, sy + SWATCH - 2, 0xFF000000 | rgb);
                if (i == 0) {
                    // Marca del color original: puntito blanco en la esquina.
                    fill(poseStack, sx + 1, sy + 1, sx + 3, sy + 3, 0xFFFFFFFF);
                }
                if (mouseX >= sx && mouseX < sx + SWATCH - 1 && mouseY >= sy && mouseY < sy + SWATCH - 1) {
                    hoveredSwatch = i;
                }
            }
        } else if (range == 0) {
            font.draw(poseStack, Component.translatable("bte_simplehats_addon.screen.double_click").withStyle(ChatFormatting.DARK_GRAY),
                    x, top + VARIANT_Y + 2, TEXT);
        }
        return hoveredSwatch;
    }

    private List<Component> hatTooltip(HatItem hat) {
        List<Component> lines = new ArrayList<>();
        ResourceLocation id = HatCatalog.id(hat);
        if (!ClientWardrobe.isUnlocked(id)) {
            lines.add(Component.literal("???").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("bte_simplehats_addon.screen.locked_tooltip").withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
        lines.add(hat.getDescription().copy().withStyle(hat.getHatEntry().getHatRarity().color));
        int range = HatCatalog.variantRange(hat);
        if (range > 0) {
            lines.add(Component.translatable("bte_simplehats_addon.screen.tooltip.variants", range + 1).withStyle(ChatFormatting.GRAY));
        }
        if (HatCatalog.isDyeable(hat)) {
            lines.add(Component.translatable("bte_simplehats_addon.screen.tooltip.dyeable").withStyle(ChatFormatting.GRAY));
        }
        HatSelection eq = ClientWardrobe.equipped();
        if (eq != null && eq.hat().equals(id)) {
            lines.add(Component.translatable("bte_simplehats_addon.screen.tooltip.equipped").withStyle(ChatFormatting.GREEN));
        } else {
            lines.add(Component.translatable("bte_simplehats_addon.screen.double_click").withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    private static int dyeRgb(DyeColor dye) {
        float[] c = dye.getTextureDiffuseColors();
        return ((int) (c[0] * 255.0f) << 16) | ((int) (c[1] * 255.0f) << 8) | (int) (c[2] * 255.0f);
    }

    // --- Dibujo estilo vanilla con fill(), sin texturas propias ---

    private void drawPanel(PoseStack poseStack, int x0, int y0, int x1, int y1) {
        fill(poseStack, x0, y0, x1, y1, 0xFF000000);
        fill(poseStack, x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFFFFFFFF);
        fill(poseStack, x0 + 2, y0 + 2, x1 - 1, y1 - 1, 0xFF555555);
        fill(poseStack, x0 + 2, y0 + 2, x1 - 2, y1 - 2, 0xFFC6C6C6);
    }

    private void drawSlotFrame(PoseStack poseStack, int x, int y, int w, int h) {
        fill(poseStack, x, y, x + w, y + h, 0xFF373737);
        fill(poseStack, x + 1, y + 1, x + w, y + h, 0xFFFFFFFF);
    }

    private void drawSlot(PoseStack poseStack, int x, int y, boolean hover) {
        drawSlotFrame(poseStack, x, y, CELL, CELL);
        fill(poseStack, x + 1, y + 1, x + CELL - 1, y + CELL - 1, hover ? 0xFFA8A8A8 : 0xFF8B8B8B);
    }

    private void outline(PoseStack poseStack, int x, int y, int w, int h, int color) {
        fill(poseStack, x, y, x + w, y + 1, color);
        fill(poseStack, x, y + h - 1, x + w, y + h, color);
        fill(poseStack, x, y, x + 1, y + h, color);
        fill(poseStack, x + w - 1, y, x + w, y + h, color);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }

        // Cuadricula
        int gx = (int) mouseX - (left + GRID_X);
        int gy = (int) mouseY - (top + GRID_Y);
        if (gx >= 0 && gy >= 0 && gx < COLS * CELL && gy < ROWS * CELL) {
            int index = (scroll + gy / CELL) * COLS + gx / CELL;
            if (index < filtered.size()) {
                HatItem hat = filtered.get(index);
                long now = System.currentTimeMillis();
                boolean doubleClick = hat == lastClicked && now - lastClickTime < DOUBLE_CLICK_MS;
                lastClicked = hat;
                lastClickTime = now;
                if (hat != selected) {
                    select(hat);
                }
                if (doubleClick) {
                    equipSelected();
                }
                return true;
            }
        }

        // Barra de scroll
        int sx = (int) mouseX - (left + SCROLL_X);
        if (sx >= 0 && sx < SCROLL_W && gy >= 0 && gy < ROWS * CELL) {
            draggingScroll = true;
            scrollToMouse(mouseY);
            return true;
        }

        // Colores
        if (selected != null && HatCatalog.isDyeable(selected) && ClientWardrobe.isUnlocked(HatCatalog.id(selected))) {
            int cx = (int) mouseX - (left + GRID_X);
            int cy = (int) mouseY - (top + SWATCH_Y);
            DyeColor[] dyes = DyeColor.values();
            if (cx >= 0 && cy >= 0 && cy < SWATCH - 1 && cx < (dyes.length + 1) * SWATCH && cx % SWATCH < SWATCH - 1) {
                int i = cx / SWATCH;
                selColor = i == 0 ? -1 : dyeRgb(dyes[i - 1]);
                updateButtons();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScroll) {
            scrollToMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta)));
        return true;
    }

    private void scrollToMouse(double mouseY) {
        int max = maxScroll();
        if (max == 0) return;
        double rel = (mouseY - (top + GRID_Y)) / (ROWS * CELL);
        scroll = Math.max(0, Math.min(max, (int) Math.round(rel * max)));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (search.isFocused()) {
            // Mientras se escribe, ninguna tecla (ni la del armario) cierra la pantalla.
            return search.keyPressed(keyCode, scanCode, modifiers) || search.canConsumeInput();
        }
        if (ClientSetup.OPEN_WARDROBE.matches(keyCode, scanCode)
                || (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode))) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
