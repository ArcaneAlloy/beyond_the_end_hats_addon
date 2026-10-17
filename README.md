# Beyond The End - Simple Hats Addon (`bte_simplehats_addon`)

Armario de gorros para SimpleHats 0.3.2 (Forge 1.19.2).

## Compilar

1. Copia a `libs/` el jar de **Curios** que usa el modpack (cualquier `curios*.jar`, 1.19.2 5.1.x).
   `libs/simplehats-forge-1.19.2-0.3.2.jar` ya viene incluido.
2. `.\gradlew build` → `build/libs/bte_simplehats_addon-1.0.0.jar`

Dependencias obligatorias en `mods.toml`: `simplehats` y `curios`.

## Funcionamiento

- **Clic derecho** con un gorro → se desbloquea para siempre y se consume.
  Repetido → según `duplicateReward` (por defecto 1 hat scrap de su rareza/temporada).
  **Agachado + clic derecho** → comportamiento original de SimpleHats.
- **Tecla H** (configurable) o el botón pequeño del inventario → abre el armario.
  - Clic selecciona (el muñeco se lo prueba), doble clic o "Equipar" lo pone.
  - Variantes con `<` `>`; colores para los teñibles.
  - "Slot: Cosmético / Cabeza" elige el slot de Curios.
  - "Guardar gorros" desbloquea de golpe todos los gorros del inventario.
- El gorro equipado desde el armario es **virtual** (NBT `bte_simplehats_virtual`):
  solo puede estar en el slot head de Curios; fuera de ahí se borra, al morir
  no cae y se vuelve a poner al reaparecer.

## Comandos (nivel 2)

```
/btehats unlock <jugadores> all|<gorro>
/btehats lock   <jugadores> all|<gorro>
/btehats list   <jugador>
```

## Config

- `serverconfig/bte_simplehats_addon-server.toml`: `unlockOnRightClick`, `sneakBypassesUnlock`,
  `duplicateReward` (SCRAPS / NOTHING / KEEP), `allowHeadSlot`.
- `config/bte_simplehats_addon-client.toml`: `showLockedHats`, `showInventoryButton`.
