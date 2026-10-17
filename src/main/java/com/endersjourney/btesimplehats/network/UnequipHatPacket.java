package com.endersjourney.btesimplehats.network;

import com.endersjourney.btesimplehats.wardrobe.WardrobeService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: quitar el gorro del armario. */
public record UnequipHatPacket() {

    public static void encode(UnequipHatPacket msg, FriendlyByteBuf buf) {
    }

    public static UnequipHatPacket decode(FriendlyByteBuf buf) {
        return new UnequipHatPacket();
    }

    public static void handle(UnequipHatPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() != null) {
                WardrobeService.unequip(ctx.get().getSender());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
