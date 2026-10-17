package com.endersjourney.btesimplehats.wardrobe;

import com.endersjourney.btesimplehats.BTESimpleHatsAddon;
import com.endersjourney.btesimplehats.HatsConfig;
import com.endersjourney.btesimplehats.command.HatsCommand;
import fonnymunkey.simplehats.common.item.HatItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.event.DropRulesEvent;
import top.theillusivec4.curios.api.type.capability.ICurio;

@Mod.EventBusSubscriber(modid = BTESimpleHatsAddon.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WardrobeEvents {

    // ------------------------------------------------------------------
    // Capability: adjuntar, copiar al clonar
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            WardrobeCapability.Provider provider = new WardrobeCapability.Provider();
            event.addCapability(WardrobeCapability.KEY, provider);
            event.addListener(provider::invalidate);
        }
    }

    /**
     * Al morir o volver del End se crea un Player nuevo. El armario va en los datos persistentes del jugador, que
     * Forge ya copia; solo hace falta copiarlo a mano si el jugador nuevo no los tiene.
     */
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player fresh = event.getEntity();
        if (fresh.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(WardrobeCapability.DATA_KEY)) {
            return;
        }
        original.reviveCaps();
        WardrobeCapability.get(original).ifPresent(old ->
                WardrobeCapability.get(fresh).ifPresent(w -> w.copyFrom(old)));
        original.invalidateCaps();
    }

    // ------------------------------------------------------------------
    // Sincronizacion y restaurar el gorro equipado
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            WardrobeService.restoreEquipped(sp);
            WardrobeService.sync(sp);
        }
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            WardrobeService.restoreEquipped(sp);
            WardrobeService.sync(sp);
        }
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            WardrobeService.sync(sp);
        }
    }

    // ------------------------------------------------------------------
    // Desbloqueo con clic derecho
    // ------------------------------------------------------------------

    /**
     * HIGHEST + cancelar: asi ni Curios (canEquipFromUse de HatItem) ni
     * SimpleHats llegan a equipar el objeto. Se cancela en los dos lados
     * para que el cliente no prediga el equipado; el desbloqueo real solo
     * ocurre en el servidor.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void rightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof HatItem)) {
            return;
        }
        Player player = event.getEntity();

        // Un gorro virtual en la mano no deberia existir: se borra.
        if (VirtualHats.isVirtual(stack)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            if (!event.getLevel().isClientSide()) {
                stack.setCount(0);
            }
            return;
        }

        if (!HatsConfig.unlockOnRightClick()) {
            return;
        }
        if (player.isShiftKeyDown() && HatsConfig.sneakBypassesUnlock()) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (player instanceof ServerPlayer sp) {
            WardrobeService.unlockOne(sp, stack, true);
        }
    }

    /** Evita poner un gorro virtual en un Hat Stand (u otra entidad) con un cliente modificado. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void entityInteract(PlayerInteractEvent.EntityInteract event) {
        if (VirtualHats.isVirtual(event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void entityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (VirtualHats.isVirtual(event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    // ------------------------------------------------------------------
    // Que el gorro virtual nunca salga de Curios
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER
                || !(event.player instanceof ServerPlayer sp) || sp.isDeadOrDying()) {
            return;
        }
        int removed = VirtualHats.sweep(sp);
        if (removed > 0 || sp.tickCount % 20 == 0) {
            WardrobeService.validateEquipped(sp);
        }
    }

    /** Tirado al suelo (Q, arrastrar fuera de la ventana...): no llega a aparecer. */
    @SubscribeEvent
    public static void itemEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ItemEntity item
                && VirtualHats.isVirtual(item.getItem())) {
            event.setCanceled(true);
        }
    }

    /** Al morir el gorro virtual se destruye (no cae); el armario lo vuelve a poner al reaparecer. */
    @SubscribeEvent
    public static void curioDropRules(DropRulesEvent event) {
        event.addOverride(VirtualHats::isVirtual, ICurio.DropRule.DESTROY);
    }

    // ------------------------------------------------------------------
    // Comandos
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        HatsCommand.register(event.getDispatcher());
    }

    private WardrobeEvents() {}
}
