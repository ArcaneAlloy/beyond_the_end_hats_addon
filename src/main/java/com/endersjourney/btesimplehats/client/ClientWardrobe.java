package com.endersjourney.btesimplehats.client;

import com.endersjourney.btesimplehats.hat.HatCatalog;
import com.endersjourney.btesimplehats.wardrobe.HatSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Copia en el cliente del armario del jugador local (la manda el servidor con SyncWardrobePacket). */
public final class ClientWardrobe {
    private static Set<ResourceLocation> unlocked = Collections.emptySet();
    @Nullable
    private static HatSelection equipped;

    public static void apply(Set<ResourceLocation> newUnlocked, @Nullable HatSelection newEquipped) {
        unlocked = Collections.unmodifiableSet(new LinkedHashSet<>(newUnlocked));
        equipped = newEquipped;
        if (Minecraft.getInstance().screen instanceof WardrobeScreen screen) {
            screen.onWardrobeChanged();
        }
    }

    public static void reset() {
        unlocked = Collections.emptySet();
        equipped = null;
    }

    public static boolean isUnlocked(ResourceLocation id) {
        return unlocked.contains(id);
    }

    @Nullable
    public static HatSelection equipped() {
        return equipped;
    }

    public static int countUnlocked() {
        int n = 0;
        for (ResourceLocation id : unlocked) {
            if (HatCatalog.get(id) != null) n++;
        }
        return n;
    }

    private ClientWardrobe() {}
}
