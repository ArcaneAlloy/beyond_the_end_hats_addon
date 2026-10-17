package com.endersjourney.btesimplehats.command;

import com.endersjourney.btesimplehats.hat.HatCatalog;
import com.endersjourney.btesimplehats.wardrobe.HatSelection;
import com.endersjourney.btesimplehats.wardrobe.HatWardrobe;
import com.endersjourney.btesimplehats.wardrobe.WardrobeCapability;
import com.endersjourney.btesimplehats.wardrobe.WardrobeService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

/**
 * /btehats unlock <jugadores> all|<gorro>
 * /btehats lock   <jugadores> all|<gorro>
 * /btehats list   <jugador>
 * Nivel de permiso 2 (operadores / trucos activados).
 */
public final class HatsCommand {

    private static final SuggestionProvider<CommandSourceStack> HATS =
            (ctx, builder) -> SharedSuggestionProvider.suggestResource(HatCatalog.ids(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("btehats")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.literal("all").executes(ctx -> change(ctx, true, null)))
                                .then(Commands.argument("hat", ResourceLocationArgument.id()).suggests(HATS)
                                        .executes(ctx -> change(ctx, true, ResourceLocationArgument.getId(ctx, "hat"))))))
                .then(Commands.literal("lock")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.literal("all").executes(ctx -> change(ctx, false, null)))
                                .then(Commands.argument("hat", ResourceLocationArgument.id()).suggests(HATS)
                                        .executes(ctx -> change(ctx, false, ResourceLocationArgument.getId(ctx, "hat"))))))
                .then(Commands.literal("list")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(HatsCommand::list))));
    }

    /** @param hat null = todos */
    private static int change(CommandContext<CommandSourceStack> ctx, boolean unlock, ResourceLocation hat) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        if (hat != null && HatCatalog.get(hat) == null) {
            ctx.getSource().sendFailure(Component.translatable("bte_simplehats_addon.command.unknown_hat", hat.toString()));
            return 0;
        }
        List<ResourceLocation> ids = hat == null ? HatCatalog.ids() : List.of(hat);
        int changed = 0;
        for (ServerPlayer player : targets) {
            HatWardrobe wardrobe = WardrobeCapability.get(player).orElse(null);
            if (wardrobe == null) {
                continue;
            }
            for (ResourceLocation id : ids) {
                if (unlock ? wardrobe.unlock(id) : wardrobe.lock(id)) {
                    changed++;
                }
            }
            HatSelection equipped = wardrobe.equipped();
            if (!unlock && equipped != null && !wardrobe.isUnlocked(equipped.hat())) {
                WardrobeService.unequip(player);
            } else {
                WardrobeService.sync(player);
            }
        }
        final int total = changed;
        ctx.getSource().sendSuccess(Component.translatable(
                unlock ? "bte_simplehats_addon.command.unlocked" : "bte_simplehats_addon.command.locked",
                total, targets.size()), true);
        return Math.max(1, total);
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "target");
        HatWardrobe wardrobe = WardrobeCapability.get(player).orElse(null);
        if (wardrobe == null) {
            return 0;
        }
        int count = WardrobeService.countUnlocked(wardrobe);
        ctx.getSource().sendSuccess(Component.translatable("bte_simplehats_addon.command.list",
                player.getDisplayName(), count, HatCatalog.total()), false);
        StringBuilder sb = new StringBuilder();
        for (ResourceLocation id : wardrobe.unlocked()) {
            if (HatCatalog.get(id) == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(id.getPath());
        }
        if (sb.length() > 0) {
            ctx.getSource().sendSuccess(Component.literal(sb.toString()), false);
        }
        return count;
    }

    private HatsCommand() {}
}
