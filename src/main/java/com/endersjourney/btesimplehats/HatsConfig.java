package com.endersjourney.btesimplehats;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Config del addon.
 *
 * SERVER (serverconfig/bte_simplehats_addon-server.toml, por mundo): reglas
 * de juego. Forge las sincroniza al cliente al entrar, asi que el cliente
 * puede leerlas tambien (p. ej. para saber si cancelar el clic derecho).
 *
 * CLIENT (config/bte_simplehats_addon-client.toml): solo presentacion.
 */
public final class HatsConfig {
    public enum DuplicateReward {
        /** Consume el gorro repetido y da 1 hat scrap de su rareza/temporada (como cortarlo con tijeras). */
        SCRAPS,
        /** Consume el gorro repetido sin dar nada. */
        NOTHING,
        /** No consume el gorro repetido: se queda en el inventario. */
        KEEP
    }

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ForgeConfigSpec.Builder sb = new ForgeConfigSpec.Builder();
        SERVER = new Server(sb);
        SERVER_SPEC = sb.build();

        ForgeConfigSpec.Builder cb = new ForgeConfigSpec.Builder();
        CLIENT = new Client(cb);
        CLIENT_SPEC = cb.build();
    }

    public static final class Server {
        public final ForgeConfigSpec.BooleanValue unlockOnRightClick;
        public final ForgeConfigSpec.BooleanValue sneakBypassesUnlock;
        public final ForgeConfigSpec.EnumValue<DuplicateReward> duplicateReward;
        public final ForgeConfigSpec.BooleanValue allowHeadSlot;

        Server(ForgeConfigSpec.Builder b) {
            b.push("wardrobe");
            unlockOnRightClick = b
                    .comment("Clic derecho con un gorro de SimpleHats lo desbloquea en el armario (y consume el objeto).",
                            "Si es false, los gorros se comportan como en SimpleHats sin addon y solo se pueden",
                            "guardar con el boton 'Guardar gorros del inventario' del armario.")
                    .define("unlockOnRightClick", true);
            sneakBypassesUnlock = b
                    .comment("Agachado + clic derecho usa el comportamiento original de SimpleHats (equipar el objeto) en vez de desbloquear.")
                    .define("sneakBypassesUnlock", true);
            duplicateReward = b
                    .comment("Que pasa al desbloquear un gorro que ya tenias:",
                            "SCRAPS = se consume y das 1 hat scrap de su rareza/temporada",
                            "NOTHING = se consume sin recompensa",
                            "KEEP = no se consume, se queda en el inventario")
                    .defineEnum("duplicateReward", DuplicateReward.SCRAPS);
            allowHeadSlot = b
                    .comment("Permite equipar desde el armario en el slot 'head' normal de Curios ademas del cosmetico.")
                    .define("allowHeadSlot", true);
            b.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue showLockedHats;
        public final ForgeConfigSpec.BooleanValue showInventoryButton;

        Client(ForgeConfigSpec.Builder b) {
            b.push("wardrobe");
            showLockedHats = b
                    .comment("Muestra en el armario los gorros aun no desbloqueados (como silueta y '???').")
                    .define("showLockedHats", true);
            showInventoryButton = b
                    .comment("Muestra el boton pequeno del armario en el inventario.")
                    .define("showInventoryButton", true);
            b.pop();
        }
    }

    // --- Lectura segura: si la config de servidor aun no esta cargada
    // (p. ej. en el menu principal) se usan los valores por defecto. ---

    public static boolean unlockOnRightClick() {
        try { return SERVER.unlockOnRightClick.get(); } catch (IllegalStateException e) { return true; }
    }

    public static boolean sneakBypassesUnlock() {
        try { return SERVER.sneakBypassesUnlock.get(); } catch (IllegalStateException e) { return true; }
    }

    public static DuplicateReward duplicateReward() {
        try { return SERVER.duplicateReward.get(); } catch (IllegalStateException e) { return DuplicateReward.SCRAPS; }
    }

    public static boolean allowHeadSlot() {
        try { return SERVER.allowHeadSlot.get(); } catch (IllegalStateException e) { return true; }
    }

    public static boolean showLockedHats() {
        try { return CLIENT.showLockedHats.get(); } catch (IllegalStateException e) { return true; }
    }

    public static boolean showInventoryButton() {
        try { return CLIENT.showInventoryButton.get(); } catch (IllegalStateException e) { return true; }
    }

    private HatsConfig() {}
}
