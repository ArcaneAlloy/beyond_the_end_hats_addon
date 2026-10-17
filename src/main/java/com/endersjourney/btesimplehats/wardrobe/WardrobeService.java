package com.endersjourney.btesimplehats.wardrobe;

import com.endersjourney.btesimplehats.HatsConfig;
import com.endersjourney.btesimplehats.hat.HatCatalog;
import com.endersjourney.btesimplehats.network.ModNetwork;
import com.endersjourney.btesimplehats.network.SyncWardrobePacket;
import fonnymunkey.simplehats.common.item.HatItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.Optional;

/** Logica de servidor del armario: desbloquear, equipar, quitar y sincronizar. */
public final class WardrobeService {

    public enum UnlockResult { NEW, DUPLICATE_CONSUMED, DUPLICATE_KEPT }

    // ------------------------------------------------------------------
    // Desbloqueo
    // ------------------------------------------------------------------

    /**
     * Desbloquea UNA unidad del gorro que hay en {@code stack} y la consume
     * (o no, si es repetido y la config dice KEEP). Manda mensaje, sonido y
     * sincroniza si {@code feedback} es true.
     */
    public static UnlockResult unlockOne(ServerPlayer player, ItemStack stack, boolean feedback) {
        HatItem hat = (HatItem) stack.getItem();
        HatWardrobe wardrobe = WardrobeCapability.get(player).orElse(null);
        if (wardrobe == null) {
            return UnlockResult.DUPLICATE_KEPT;
        }
        ResourceLocation id = HatCatalog.id(hat);
        Component name = new ItemStack(hat).getHoverName().copy().withStyle(hat.getHatEntry().getHatRarity().color);

        if (wardrobe.unlock(id)) {
            consume(player, stack);
            if (feedback) {
                player.displayClientMessage(Component.translatable("bte_simplehats_addon.unlock.new", name,
                        countUnlocked(wardrobe), HatCatalog.total()).withStyle(ChatFormatting.GREEN), false);
                celebrate(player);
                sync(player);
            }
            return UnlockResult.NEW;
        }

        HatsConfig.DuplicateReward rule = HatsConfig.duplicateReward();
        ItemStack scraps = rule == HatsConfig.DuplicateReward.SCRAPS ? HatCatalog.scrapsFor(hat) : ItemStack.EMPTY;
        if (rule == HatsConfig.DuplicateReward.KEEP || (rule == HatsConfig.DuplicateReward.SCRAPS && scraps.isEmpty())) {
            if (feedback) {
                player.displayClientMessage(Component.translatable("bte_simplehats_addon.unlock.already", name)
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return UnlockResult.DUPLICATE_KEPT;
        }

        consume(player, stack);
        if (!scraps.isEmpty()) {
            Component scrapName = scraps.getHoverName();
            give(player, scraps);
            if (feedback) {
                player.displayClientMessage(Component.translatable("bte_simplehats_addon.unlock.duplicate_scraps", name, scrapName)
                        .withStyle(ChatFormatting.YELLOW), true);
            }
        } else if (feedback) {
            player.displayClientMessage(Component.translatable("bte_simplehats_addon.unlock.duplicate_nothing", name)
                    .withStyle(ChatFormatting.GRAY), true);
        }
        if (feedback) {
            player.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 0.8f);
        }
        return UnlockResult.DUPLICATE_CONSUMED;
    }

    /**
     * Boton "Guardar gorros del inventario": pasa por el armario todos los
     * gorros (reales, no virtuales) del inventario principal y la mano
     * secundaria. Es lo que resuelve el problema de acumulacion que ya
     * tengan los jugadores de antes de instalar el addon.
     */
    public static void storeInventory(ServerPlayer player) {
        int fresh = 0;
        int dupes = 0;
        int kept = 0;
        Inventory inv = player.getInventory();
        int size = inv.items.size() + inv.offhand.size();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = slot < inv.items.size() ? inv.items.get(slot) : inv.offhand.get(slot - inv.items.size());
            if (!(stack.getItem() instanceof HatItem) || VirtualHats.isVirtual(stack)) {
                continue;
            }
            // Un HatItem tiene stack size 1, pero por si otro mod lo cambia.
            // Bucle acotado por el tamaño inicial: en creativo no se consume.
            int count = stack.getCount();
            for (int n = 0; n < count && !stack.isEmpty(); n++) {
                UnlockResult r = unlockOne(player, stack, false);
                if (r == UnlockResult.NEW) fresh++;
                else if (r == UnlockResult.DUPLICATE_CONSUMED) dupes++;
                else { kept++; break; }
            }
        }

        if (fresh + dupes + kept == 0) {
            player.displayClientMessage(Component.translatable("bte_simplehats_addon.store.none").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        HatWardrobe wardrobe = WardrobeCapability.get(player).orElse(null);
        int unlocked = wardrobe == null ? 0 : countUnlocked(wardrobe);
        player.displayClientMessage(Component.translatable("bte_simplehats_addon.store.done",
                fresh, dupes, kept, unlocked, HatCatalog.total()).withStyle(ChatFormatting.GREEN), false);
        if (fresh > 0) {
            celebrate(player);
        }
        sync(player);
    }

    // ------------------------------------------------------------------
    // Equipar / quitar
    // ------------------------------------------------------------------

    public static void equip(ServerPlayer player, HatSelection selection) {
        HatWardrobe wardrobe = WardrobeCapability.get(player).orElse(null);
        HatItem hat = HatCatalog.get(selection.hat());
        if (wardrobe == null || hat == null || !wardrobe.isUnlocked(selection.hat())) {
            return;
        }
        if (!selection.cosmetic() && !HatsConfig.allowHeadSlot()) {
            selection = selection.withCosmetic(true);
        }
        int variant = Math.max(0, Math.min(selection.variant(), HatCatalog.variantRange(hat)));
        int color = HatCatalog.isDyeable(hat) ? selection.color() : -1;
        selection = new HatSelection(selection.hat(), variant, color, selection.cosmetic());

        Optional<ICurioStacksHandler> head = VirtualHats.headSlots(player);
        if (head.isEmpty()) {
            player.displayClientMessage(Component.translatable("bte_simplehats_addon.equip.no_slot").withStyle(ChatFormatting.RED), true);
            return;
        }
        IDynamicStackHandler target = VirtualHats.stacks(head.get(), selection.cosmetic());
        if (target.getSlots() == 0) {
            player.displayClientMessage(Component.translatable("bte_simplehats_addon.equip.no_slot").withStyle(ChatFormatting.RED), true);
            return;
        }

        VirtualHats.removeVirtual(head.get());
        ItemStack current = target.getStackInSlot(0);
        if (!current.isEmpty()) {
            // Hay un objeto real en ese slot (otro gorro, otro cosmetico...): se devuelve al jugador.
            target.setStackInSlot(0, ItemStack.EMPTY);
            give(player, current);
        }
        target.setStackInSlot(0, VirtualHats.mark(HatCatalog.buildStack(hat, variant, color, player)));
        wardrobe.setEquipped(selection);
        sync(player);
    }

    public static void unequip(ServerPlayer player) {
        HatWardrobe wardrobe = WardrobeCapability.get(player).orElse(null);
        if (wardrobe == null) {
            return;
        }
        VirtualHats.headSlots(player).ifPresent(VirtualHats::removeVirtual);
        wardrobe.setEquipped(null);
        sync(player);
    }

    /**
     * Al reaparecer (o entrar): si el armario dice que llevaba un gorro y el
     * slot esta vacio, se vuelve a poner. Si el slot esta ocupado por un
     * objeto real, se respeta y se olvida la seleccion.
     */
    public static void restoreEquipped(ServerPlayer player) {
        HatWardrobe wardrobe = WardrobeCapability.get(player).orElse(null);
        if (wardrobe == null || wardrobe.equipped() == null) {
            return;
        }
        HatSelection sel = wardrobe.equipped();
        HatItem hat = HatCatalog.get(sel.hat());
        Optional<ICurioStacksHandler> head = VirtualHats.headSlots(player);
        if (head.isEmpty()) {
            return;
        }
        if (VirtualHats.hasVirtual(head.get())) {
            VirtualHats.removeExtraVirtual(head.get());
            return;
        }
        IDynamicStackHandler target = VirtualHats.stacks(head.get(), sel.cosmetic());
        if (hat == null || !wardrobe.isUnlocked(sel.hat()) || target.getSlots() == 0 || !target.getStackInSlot(0).isEmpty()) {
            wardrobe.setEquipped(null);
            sync(player);
            return;
        }
        target.setStackInSlot(0, VirtualHats.mark(HatCatalog.buildStack(hat, sel.variant(), sel.color(), player)));
    }

    /**
     * Comprobacion periodica: si el jugador ha sacado a mano el gorro
     * virtual del slot (y por tanto se ha borrado), el armario deja de
     * considerarlo equipado. Tambien limpia duplicados.
     */
    public static void validateEquipped(ServerPlayer player) {
        HatWardrobe wardrobe = WardrobeCapability.get(player).orElse(null);
        if (wardrobe == null) {
            return;
        }
        Optional<ICurioStacksHandler> head = VirtualHats.headSlots(player);
        if (head.isEmpty()) {
            return;
        }
        VirtualHats.removeExtraVirtual(head.get());
        if (wardrobe.equipped() != null && !VirtualHats.hasVirtual(head.get())) {
            wardrobe.setEquipped(null);
            sync(player);
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    public static void sync(ServerPlayer player) {
        WardrobeCapability.get(player).ifPresent(w ->
                ModNetwork.sendTo(player, new SyncWardrobePacket(w.unlocked(), w.equipped())));
    }

    /** Desbloqueados que existen de verdad en esta instancia (ignora ids de gorros eliminados). */
    public static int countUnlocked(HatWardrobe wardrobe) {
        int n = 0;
        for (ResourceLocation id : wardrobe.unlocked()) {
            if (HatCatalog.get(id) != null) n++;
        }
        return n;
    }

    private static void consume(ServerPlayer player, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void celebrate(ServerPlayer player) {
        player.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        if (player.level instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 2.0, player.getZ(),
                    12, 0.35, 0.25, 0.35, 0.0);
        }
    }

    private WardrobeService() {}
}
