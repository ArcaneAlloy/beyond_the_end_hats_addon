package com.endersjourney.btesimplehats.wardrobe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Datos del armario de un jugador: gorros desbloqueados y gorro equipado
 * desde el armario. Se guardan por id de item (simplehats:<nombre>), asi
 * que si un gorro desaparece de SimpleHats en una actualizacion, su id se
 * conserva sin romper nada (simplemente no se muestra).
 */
public class HatWardrobe {
    private final Set<ResourceLocation> unlocked = new LinkedHashSet<>();
    @Nullable
    private HatSelection equipped;
    /** Se llama tras cada cambio para guardarlo en los datos del jugador (ver WardrobeCapability.get). */
    @Nullable
    private Runnable onChange;

    void setOnChange(@Nullable Runnable onChange) {
        this.onChange = onChange;
    }

    private void changed() {
        if (onChange != null) onChange.run();
    }

    /** ¿Tiene algo guardado? (para migrar los datos antiguos de la capability) */
    public boolean isEmpty() {
        return unlocked.isEmpty() && equipped == null;
    }

    public boolean isUnlocked(ResourceLocation id) {
        return unlocked.contains(id);
    }

    /** @return true si es nuevo. */
    public boolean unlock(ResourceLocation id) {
        boolean added = unlocked.add(id);
        if (added) changed();
        return added;
    }

    /** @return true si estaba desbloqueado. */
    public boolean lock(ResourceLocation id) {
        boolean removed = unlocked.remove(id);
        if (removed) changed();
        return removed;
    }

    public void clear() {
        unlocked.clear();
        equipped = null;
        changed();
    }

    public Set<ResourceLocation> unlocked() {
        return Collections.unmodifiableSet(unlocked);
    }

    @Nullable
    public HatSelection equipped() {
        return equipped;
    }

    public void setEquipped(@Nullable HatSelection selection) {
        this.equipped = selection;
        changed();
    }

    public void copyFrom(HatWardrobe other) {
        unlocked.clear();
        unlocked.addAll(other.unlocked);
        equipped = other.equipped;
        changed();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (ResourceLocation id : unlocked) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put("unlocked", list);
        if (equipped != null) {
            tag.put("equipped", equipped.save());
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        unlocked.clear();
        ListTag list = tag.getList("unlocked", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) {
                unlocked.add(id);
            }
        }
        equipped = tag.contains("equipped", Tag.TAG_COMPOUND) ? HatSelection.load(tag.getCompound("equipped")) : null;
    }
}
