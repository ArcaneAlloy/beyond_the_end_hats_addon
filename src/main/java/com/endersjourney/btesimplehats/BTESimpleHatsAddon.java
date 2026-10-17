package com.endersjourney.btesimplehats;

import com.endersjourney.btesimplehats.network.ModNetwork;
import com.endersjourney.btesimplehats.wardrobe.WardrobeCapability;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Beyond The End - Simple Hats Addon
 *
 * "Armario de gorros" para SimpleHats. En vez de guardar los gorros como
 * objetos (y acabar con el inventario lleno de ellos), el jugador los
 * DESBLOQUEA de forma permanente con clic derecho y luego elige cual llevar
 * desde una interfaz propia (tecla H por defecto, o el boton pequeno del
 * inventario).
 *
 * Piezas:
 *  - wardrobe/HatWardrobe + WardrobeCapability: datos por jugador
 *    (gorros desbloqueados + gorro equipado), guardados en el NBT del
 *    jugador y copiados al morir / volver del End.
 *  - wardrobe/VirtualHats: el gorro que se pone desde el armario es un
 *    ItemStack "virtual" (marca NBT). Solo puede existir en el slot head
 *    de Curios; en cualquier otro sitio (inventario, cofres, suelo) se
 *    borra. Asi no se pueden duplicar gorros.
 *  - wardrobe/WardrobeService: desbloquear, equipar, quitar, sincronizar.
 *  - network/*: paquetes cliente-servidor.
 *  - client/*: pantalla del armario, tecla y boton del inventario.
 *  - command/HatsCommand: /btehats unlock|lock|list para admins y pruebas.
 */
@Mod(BTESimpleHatsAddon.MODID)
public class BTESimpleHatsAddon {
    public static final String MODID = "bte_simplehats_addon";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public BTESimpleHatsAddon() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, HatsConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, HatsConfig.CLIENT_SPEC);

        modBus.addListener(WardrobeCapability::register);
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
        LOGGER.info("[{}] Cargado.", MODID);
    }
}
