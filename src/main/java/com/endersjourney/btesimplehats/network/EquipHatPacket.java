package com.endersjourney.btesimplehats.network;

import com.endersjourney.btesimplehats.wardrobe.HatSelection;
import com.endersjourney.btesimplehats.wardrobe.WardrobeService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: "ponme este gorro". El servidor valida que este desbloqueado. */
public record EquipHatPacket(HatSelection selection) {

    public static void encode(EquipHatPacket msg, FriendlyByteBuf buf) {
        msg.selection.write(buf);
    }

    public static EquipHatPacket decode(FriendlyByteBuf buf) {
        return new EquipHatPacket(HatSelection.read(buf));
    }

    public static void handle(EquipHatPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                WardrobeService.equip(player, msg.selection);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
