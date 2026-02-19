package com.mistborn.command;

import com.mistborn.capability.AllomanticData;
import com.mistborn.capability.ModAttachments;
import com.mistborn.network.ModNetwork;
import com.mistborn.power.AllomanticMetal;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

/**
 * Registers the {@code /power <player> allomancy <metalname>} debug command.
 * Grants the named player Misting access to the specified metal.
 * Requires operator level 2.
 */
public class PowerCommand {

    private static final SuggestionProvider<CommandSourceStack> METAL_SUGGESTIONS =
            (ctx, builder) -> {
                String remaining = builder.getRemaining().toLowerCase();
                for (AllomanticMetal m : AllomanticMetal.values()) {
                    if (m.name().toLowerCase().startsWith(remaining)) {
                        builder.suggest(m.name().toLowerCase());
                    }
                }
                return builder.buildFuture();
            };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("power")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("allomancy")
                                        .then(Commands.argument("metal", StringArgumentType.word())
                                                .suggests(METAL_SUGGESTIONS)
                                                .executes(PowerCommand::execute)))));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(ctx, "player");
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Player not found."));
            return 0;
        }

        String metalName = StringArgumentType.getString(ctx, "metal");
        AllomanticMetal metal = AllomanticMetal.fromName(metalName);

        if (metal == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Unknown metal: '" + metalName + "'. Valid metals: "
                            + Arrays.stream(AllomanticMetal.values())
                              .map(m -> m.name().toLowerCase())
                              .reduce((a, b) -> a + ", " + b)
                              .orElse("none")));
            return 0;
        }

        AllomanticData data = target.getData(ModAttachments.ALLOMANTIC_DATA.get());

        if (data.isUnlocked(metal)) {
            ctx.getSource().sendFailure(Component.literal(
                    target.getName().getString() + " already has " + metal.getDisplayName() + " unlocked."));
            return 0;
        }

        data.unlockMetal(metal);
        ModNetwork.sync(target, data);

        ctx.getSource().sendSuccess(() -> Component.literal(
                "Granted " + target.getName().getString()
                        + " Misting powers for " + metal.getDisplayName() + "."), true);

        target.sendSystemMessage(Component.literal(
                "You have been granted the power of " + metal.getDisplayName() + " Allomancy!"));

        return 1;
    }

    private PowerCommand() {}
}
