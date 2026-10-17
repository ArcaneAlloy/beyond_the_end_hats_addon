package com.endersjourney.btesimplehats.hat;

import fonnymunkey.simplehats.common.init.ModRegistry;
import fonnymunkey.simplehats.common.item.HatItem;
import fonnymunkey.simplehats.common.item.HatItemDyeable;
import fonnymunkey.simplehats.util.HatEntry;
import fonnymunkey.simplehats.util.UUIDHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;

/**
 * Todo lo que el addon necesita saber de los gorros de SimpleHats.
 *
 * En SimpleHats 0.3.2 cada gorro es un Item distinto (simplehats:<nombre>,
 * clase HatItem, o HatItemDyeable si es teñible), generado a partir de las
 * entradas JSON. Ademas:
 *  - Variantes: CustomModelData 0..variantRange (la receta de variantes las
 *    recorre en la mesa de crafteo). Tener el gorro = tener todas sus
 *    variantes, asi que el armario desbloquea el gorro entero.
 *  - Teñibles: display.color, como el cuero.
 *  - "special" (The Simple Hat): su CustomModelData sale del UUID del
 *    jugador (UUIDHandler), igual que hace SimpleHats al equiparlo.
 */
public final class HatCatalog {
    private static final String CUSTOM_MODEL_DATA = "CustomModelData";
    private static List<HatItem> cache;

    /** Todos los gorros registrados, ordenados por rareza y luego por id. */
    public static List<HatItem> all() {
        if (cache == null) {
            cache = ForgeRegistries.ITEMS.getValues().stream()
                    .filter(i -> i instanceof HatItem)
                    .map(i -> (HatItem) i)
                    .sorted(Comparator.comparingInt((HatItem h) -> h.getHatEntry().getHatRarity().ordinal())
                            .thenComparing(h -> id(h).getPath()))
                    .toList();
        }
        return cache;
    }

    public static List<ResourceLocation> ids() {
        return all().stream().map(HatCatalog::id).toList();
    }

    public static int total() {
        return all().size();
    }

    public static ResourceLocation id(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }

    @Nullable
    public static HatItem get(ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item instanceof HatItem hat ? hat : null;
    }

    public static boolean isSpecial(HatItem hat) {
        return hat == ModRegistry.HATSPECIAL.get();
    }

    /** Numero de variantes extra (0 = sin variantes). Las variantes validas son 0..range. */
    public static int variantRange(HatItem hat) {
        return isSpecial(hat) ? 0 : Math.max(0, hat.getHatEntry().getHatVariantRange());
    }

    public static boolean isDyeable(HatItem hat) {
        return hat instanceof HatItemDyeable;
    }

    public static int defaultColor(HatItem hat) {
        return hat.getHatEntry().getHatDyeSettings().getColorCode();
    }

    /** Crea el ItemStack del gorro con la variante/color elegidos (sin la marca de virtual). */
    public static ItemStack buildStack(HatItem hat, int variant, int color, @Nullable Player player) {
        ItemStack stack = new ItemStack(hat);
        int range = variantRange(hat);
        if (variant > 0 && variant <= range) {
            stack.getOrCreateTag().putInt(CUSTOM_MODEL_DATA, variant);
        }
        if (isSpecial(hat) && player != null) {
            int special = UUIDHandler.getUUIDMap().getOrDefault(player.getStringUUID(), 0);
            if (special > 0) {
                stack.getOrCreateTag().putInt(CUSTOM_MODEL_DATA, special);
            }
        }
        if (color >= 0 && hat instanceof DyeableLeatherItem dyeable) {
            dyeable.setColor(stack, color & 0xFFFFFF);
        }
        return stack;
    }

    /**
     * Recompensa por gorro repetido: lo mismo que da SimpleHats al cortar
     * el gorro con tijeras (HatScrapRecipe). El "special" no da scraps.
     */
    public static ItemStack scrapsFor(HatItem hat) {
        if (isSpecial(hat)) {
            return ItemStack.EMPTY;
        }
        HatEntry entry = hat.getHatEntry();
        Item scraps = switch (entry.getHatSeason()) {
            case EASTER -> ModRegistry.HATSCRAPS_EASTER.get();
            case SUMMER -> ModRegistry.HATSCRAPS_SUMMER.get();
            case HALLOWEEN -> ModRegistry.HATSCRAPS_HALLOWEEN.get();
            case FESTIVE -> ModRegistry.HATSCRAPS_FESTIVE.get();
            default -> switch (entry.getHatRarity()) {
                case COMMON -> ModRegistry.HATSCRAPS_COMMON.get();
                case UNCOMMON -> ModRegistry.HATSCRAPS_UNCOMMON.get();
                default -> ModRegistry.HATSCRAPS_RARE.get();
            };
        };
        return new ItemStack(scraps);
    }

    private HatCatalog() {}
}
