package com.endersjourney.btesimplehats.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fonnymunkey.simplehats.common.init.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Boton pequeno (10x10) en la esquina superior derecha del recuadro del
 * jugador del inventario (vanilla y Curios). Sigue a la pantalla si el
 * libro de recetas la desplaza.
 */
public class WardrobeButton extends Button {
    private static final int SIZE = 10;
    private static final int OFFSET_X = 64;
    private static final int OFFSET_Y = 9;

    private final AbstractContainerScreen<?> screen;
    private ItemStack icon;

    public WardrobeButton(AbstractContainerScreen<?> screen) {
        super(screen.getGuiLeft() + OFFSET_X, screen.getGuiTop() + OFFSET_Y, SIZE, SIZE,
                Component.translatable("bte_simplehats_addon.screen.title"),
                b -> Minecraft.getInstance().setScreen(new WardrobeScreen()),
                (b, poseStack, mouseX, mouseY) -> screen.renderTooltip(poseStack,
                        Component.translatable("bte_simplehats_addon.button.tooltip"), mouseX, mouseY));
        this.screen = screen;
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.x = screen.getGuiLeft() + OFFSET_X;
        this.y = screen.getGuiTop() + OFFSET_Y;
        boolean hover = isHoveredOrFocused();
        fill(poseStack, x, y, x + SIZE, y + SIZE, hover ? 0xFFFFFFFF : 0xFF555555);
        fill(poseStack, x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, hover ? 0xFF8B8B8B : 0xFF373737);
        if (icon == null) {
            icon = new ItemStack(ModRegistry.HATICON.get());
        }
        ClientRender.renderItem(icon, x + 1, y + 1, 0.5f, false);
        // Button#renderButton es quien llama a renderToolTip en 1.19.2; como lo sobrescribimos, lo hacemos aqui.
        if (hover) {
            renderToolTip(poseStack, mouseX, mouseY);
        }
    }
}
