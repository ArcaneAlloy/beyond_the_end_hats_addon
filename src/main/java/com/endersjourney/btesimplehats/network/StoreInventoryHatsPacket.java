package com.endersjourney.btesimplehats.network;

import com.endersjourney.btesimplehats.wardrobe.WardrobeService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: boton "Guardar gorros del inventario". */
public record StoreInventoryHatsPacket() {

    public static void encode(StoreInventoryHatsPacket msg, FriendlyByteBuf buf) {
    }

    public static StoreInventoryHatsPacket decode(FriendlyByteBuf buf) {
        return new StoreInventoryHatsPacket();
    }

    public static void handle(StoreInventoryHatsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() != null) {
                WardrobeService.storeInventory(ctx.get().getSender());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
