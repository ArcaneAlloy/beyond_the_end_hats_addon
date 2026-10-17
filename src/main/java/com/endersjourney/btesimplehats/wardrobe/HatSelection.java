package com.endersjourney.btesimplehats.wardrobe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Un gorro concreto tal y como el jugador lo quiere llevar: que gorro,
 * que variante (CustomModelData), que color (-1 = el original) y en que
 * slot de Curios (cosmetico o head normal).
 */
public record HatSelection(ResourceLocation hat, int variant, int color, boolean cosmetic) {

    public HatSelection withCosmetic(boolean value) {
        return new HatSelection(hat, variant, color, value);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("hat", hat.toString());
        tag.putInt("variant", variant);
        tag.putInt("color", color);
        tag.putBoolean("cosmetic", cosmetic);
        return tag;
    }

    @Nullable
    public static HatSelection load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("hat"));
        if (id == null) {
            return null;
        }
        int color = tag.contains("color") ? tag.getInt("color") : -1;
        boolean cosmetic = !tag.contains("cosmetic") || tag.getBoolean("cosmetic");
        return new HatSelection(id, tag.getInt("variant"), color, cosmetic);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(hat);
        buf.writeVarInt(variant);
        buf.writeInt(color);
        buf.writeBoolean(cosmetic);
    }

    public static HatSelection read(FriendlyByteBuf buf) {
        return new HatSelection(buf.readResourceLocation(), buf.readVarInt(), buf.readInt(), buf.readBoolean());
    }
}
