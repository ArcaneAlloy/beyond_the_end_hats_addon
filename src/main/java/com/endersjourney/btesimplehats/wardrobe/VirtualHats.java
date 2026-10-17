package com.endersjourney.btesimplehats.wardrobe;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;
import net.minecraftforge.items.SlotItemHandler;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.Optional;

/**
 * Gorros "virtuales": el ItemStack que el armario pone en el slot head de
 * Curios lleva la marca NBT {@link #TAG}. Ese stack es solo una
 * representacion del desbloqueo, no un objeto real, asi que:
 *  - solo puede existir dentro de un slot de Curios;
 *  - si aparece en cualquier otro sitio (inventario, cofre, cursor fuera de
 *    la pantalla de Curios, suelo) se borra;
 *  - al morir se destruye (DropRulesEvent) y el armario lo vuelve a poner
 *    al reaparecer.
 * Sin esto se podria sacar el gorro del slot y pedir otro: duplicacion.
 */
public final class VirtualHats {
    public static final String TAG = "bte_simplehats_virtual";
    public static final String HEAD_SLOT = "head";

    public static boolean isVirtual(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().getBoolean(TAG);
    }

    public static ItemStack mark(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(TAG, true);
        return stack;
    }

    /** El grupo de slots "head" de Curios (normal + cosmetico) de una entidad. */
    public static Optional<ICurioStacksHandler> headSlots(LivingEntity entity) {
        return CuriosApi.getCuriosHelper().getCuriosHandler(entity).resolve()
                .flatMap(handler -> handler.getStacksHandler(HEAD_SLOT));
    }

    public static IDynamicStackHandler stacks(ICurioStacksHandler head, boolean cosmetic) {
        return cosmetic ? head.getCosmeticStacks() : head.getStacks();
    }

    /** ¿Hay algun gorro virtual en los slots head (normal o cosmetico)? */
    public static boolean hasVirtual(ICurioStacksHandler head) {
        return countVirtual(head.getStacks()) + countVirtual(head.getCosmeticStacks()) > 0;
    }

    /** Quita todos los gorros virtuales de los slots head. @return cuantos habia. */
    public static int removeVirtual(ICurioStacksHandler head) {
        return removeVirtual(head.getStacks()) + removeVirtual(head.getCosmeticStacks());
    }

    private static int countVirtual(IDynamicStackHandler stacks) {
        int n = 0;
        for (int i = 0; i < stacks.getSlots(); i++) {
            if (isVirtual(stacks.getStackInSlot(i))) n++;
        }
        return n;
    }

    private static int removeVirtual(IDynamicStackHandler stacks) {
        int n = 0;
        for (int i = 0; i < stacks.getSlots(); i++) {
            if (isVirtual(stacks.getStackInSlot(i))) {
                stacks.setStackInSlot(i, ItemStack.EMPTY);
                n++;
            }
        }
        return n;
    }

    /**
     * Deja como mucho UN gorro virtual en los slots head (el primero que
     * encuentre, mirando antes el cosmetico). Defensa extra contra dupes.
     */
    public static int removeExtraVirtual(ICurioStacksHandler head) {
        int removed = 0;
        boolean kept = false;
        for (IDynamicStackHandler stacks : new IDynamicStackHandler[]{head.getCosmeticStacks(), head.getStacks()}) {
            for (int i = 0; i < stacks.getSlots(); i++) {
                if (isVirtual(stacks.getStackInSlot(i))) {
                    if (kept) {
                        stacks.setStackInSlot(i, ItemStack.EMPTY);
                        removed++;
                    } else {
                        kept = true;
                    }
                }
            }
        }
        return removed;
    }

    /**
     * Borra los gorros virtuales que esten fuera de Curios: en el
     * inventario del jugador, en cualquier slot del menu abierto que no sea
     * de Curios (p. ej. un cofre) y en el cursor si el menu abierto no es
     * la pantalla de Curios.
     *
     * Los slots de Curios se reconocen porque son SlotItemHandler sobre un
     * IDynamicStackHandler (CurioSlot / CosmeticCurioSlot), que es API
     * publica: no hace falta tocar clases internas de Curios.
     *
     * @return cuantos stacks se han borrado.
     */
    public static int sweep(Player player) {
        int removed = 0;
        AbstractContainerMenu menu = player.containerMenu;
        boolean curiosMenu = false;
        for (Slot slot : menu.slots) {
            if (isCurioSlot(slot)) {
                curiosMenu = true;
                continue;
            }
            if (isVirtual(slot.getItem())) {
                slot.set(ItemStack.EMPTY);
                removed++;
            }
        }
        if (!curiosMenu && isVirtual(menu.getCarried())) {
            menu.setCarried(ItemStack.EMPTY);
            removed++;
        }

        // El inventario directamente, por si el menu abierto no lo incluye.
        Inventory inv = player.getInventory();
        removed += sweepList(inv.items);
        removed += sweepList(inv.armor);
        removed += sweepList(inv.offhand);
        return removed;
    }

    private static int sweepList(NonNullList<ItemStack> list) {
        int n = 0;
        for (int i = 0; i < list.size(); i++) {
            if (isVirtual(list.get(i))) {
                list.set(i, ItemStack.EMPTY);
                n++;
            }
        }
        return n;
    }

    private static boolean isCurioSlot(Slot slot) {
        return slot instanceof SlotItemHandler handlerSlot && handlerSlot.getItemHandler() instanceof IDynamicStackHandler;
    }

    private VirtualHats() {}
}
