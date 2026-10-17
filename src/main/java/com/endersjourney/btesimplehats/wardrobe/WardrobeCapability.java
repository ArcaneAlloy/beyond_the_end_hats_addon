package com.endersjourney.btesimplehats.wardrobe;

import com.endersjourney.btesimplehats.BTESimpleHatsAddon;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Armario de cada jugador. Se guarda en sus datos persistentes (ver get); la capability solo se mantiene para leer
 * los datos que hubiera guardado una versión anterior del addon.
 */
public final class WardrobeCapability {
    public static final Capability<HatWardrobe> CAP = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation KEY = new ResourceLocation(BTESimpleHatsAddon.MODID, "wardrobe");

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(HatWardrobe.class);
    }

    /** Donde se guarda el armario: datos persistentes del jugador (Forge los copia al morir y al volver del End). */
    public static final String DATA_KEY = "bte_simplehats_wardrobe";

    /**
     * Armario del jugador.
     *
     * Antes solo se leía de la capability, y si el jugador no la tenía (no se llegaba a adjuntar) el armario no existía:
     * ni el clic derecho ni "Guardar gorros" desbloqueaban nada ("1 no guardado", 0/208). Ahora se guarda en los datos
     * persistentes del jugador, que siempre existen. Si había datos en la capability antigua, se pasan aquí la
     * primera vez. Cada cambio del HatWardrobe devuelto se guarda al momento.
     */
    public static Optional<HatWardrobe> get(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        HatWardrobe wardrobe = new HatWardrobe();
        if (persisted.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
            wardrobe.load(persisted.getCompound(DATA_KEY));
        } else {
            // Migración: lo que hubiera en la capability antigua
            player.getCapability(CAP).resolve().filter(old -> !old.isEmpty()).ifPresent(old -> {
                wardrobe.load(old.save());
                save(player, wardrobe);
            });
        }
        wardrobe.setOnChange(() -> save(player, wardrobe));
        return Optional.of(wardrobe);
    }

    private static void save(Player player, HatWardrobe wardrobe) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(DATA_KEY, wardrobe.save());
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    public static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final HatWardrobe wardrobe = new HatWardrobe();
        private final LazyOptional<HatWardrobe> holder = LazyOptional.of(() -> wardrobe);

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            return CAP.orEmpty(cap, holder);
        }

        @Override
        public CompoundTag serializeNBT() {
            return wardrobe.save();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            wardrobe.load(tag);
        }

        public void invalidate() {
            holder.invalidate();
        }
    }

    private WardrobeCapability() {}
}
