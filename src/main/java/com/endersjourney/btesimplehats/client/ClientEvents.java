package com.endersjourney.btesimplehats.client;

import com.endersjourney.btesimplehats.BTESimpleHatsAddon;
import com.endersjourney.btesimplehats.HatsConfig;
import com.endersjourney.btesimplehats.wardrobe.VirtualHats;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BTESimpleHatsAddon.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    /** Pantalla de Curios (la del screenshot, con los slots a la izquierda). Por nombre para no depender de clases internas. */
    private static final String CURIOS_SCREEN = "top.theillusivec4.curios.client.gui.CuriosScreen";

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (ClientSetup.OPEN_WARDROBE.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new WardrobeScreen());
            }
        }
    }

    @SubscribeEvent
    public static void screenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!HatsConfig.showInventoryButton()) {
            return;
        }
        if (screen instanceof InventoryScreen || CURIOS_SCREEN.equals(screen.getClass().getName())) {
            event.addListener(new WardrobeButton((AbstractContainerScreen<?>) screen));
        }
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        if (VirtualHats.isVirtual(event.getItemStack())) {
            event.getToolTip().add(Component.translatable("bte_simplehats_addon.tooltip.virtual")
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        }
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientWardrobe.reset();
    }

    private ClientEvents() {}
}
