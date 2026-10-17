package com.endersjourney.btesimplehats.network;

import com.endersjourney.btesimplehats.wardrobe.HatSelection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

/** Servidor -> cliente: estado completo del armario del jugador. */
public record SyncWardrobePacket(Set<ResourceLocation> unlocked, @Nullable HatSelection equipped) {

    public static void encode(SyncWardrobePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.unlocked.size());
        for (ResourceLocation id : msg.unlocked) {
            buf.writeResourceLocation(id);
        }
        buf.writeBoolean(msg.equipped != null);
        if (msg.equipped != null) {
            msg.equipped.write(buf);
        }
    }

    public static SyncWardrobePacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Set<ResourceLocation> unlocked = new LinkedHashSet<>();
        for (int i = 0; i < n; i++) {
            unlocked.add(buf.readResourceLocation());
        }
        HatSelection equipped = buf.readBoolean() ? HatSelection.read(buf) : null;
        return new SyncWardrobePacket(unlocked, equipped);
    }

    public static void handle(SyncWardrobePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.endersjourney.btesimplehats.client.ClientWardrobe.apply(msg.unlocked, msg.equipped)));
        ctx.get().setPacketHandled(true);
    }
}
