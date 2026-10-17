package com.endersjourney.btesimplehats.network;

import com.endersjourney.btesimplehats.BTESimpleHatsAddon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class ModNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BTESimpleHatsAddon.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private static boolean registered;

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        int id = 0;
        CHANNEL.registerMessage(id++, SyncWardrobePacket.class, SyncWardrobePacket::encode, SyncWardrobePacket::decode,
                SyncWardrobePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, EquipHatPacket.class, EquipHatPacket::encode, EquipHatPacket::decode,
                EquipHatPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, UnequipHatPacket.class, UnequipHatPacket::encode, UnequipHatPacket::decode,
                UnequipHatPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, StoreInventoryHatsPacket.class, StoreInventoryHatsPacket::encode, StoreInventoryHatsPacket::decode,
                StoreInventoryHatsPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendTo(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    private ModNetwork() {}
}
