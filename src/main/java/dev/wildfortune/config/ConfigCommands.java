package dev.wildfortune.config;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import dev.wildfortune.gear.AffixChanceTable;

public final class ConfigCommands {
    private ConfigCommands() {}
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var chance = Commands.literal("chance").executes(context -> show(context.getSource()))
                    .then(Commands.literal("monsters").executes(context -> {
                        var report = dev.wildfortune.monster.MonsterChanceTable.render();
                        context.getSource().sendSuccess(() -> report, false);
                        return 1;
                    }));
            dispatcher.register(Commands.literal("wildfortune")
                        .executes(context -> help(context.getSource()))
                        .then(chance)
                        .then(Commands.literal("reload")
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .executes(context -> {
                            if (WildFortuneConfig.load(WildFortuneConfig.path())) {
                                context.getSource().sendSuccess(() -> Component.translatable("command.wildfortune.reload.success"), true);
                                return 1;
                            }
                            context.getSource().sendFailure(Component.translatable("command.wildfortune.reload.failed"));
                            return 0;
                        })));
        });
    }

    private static int show(CommandSourceStack source) {
        Component report = AffixChanceTable.render();
        source.sendSuccess(() -> report, false); // Reply only to the requester; never broadcast to admins.
        return 1;
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("command.wildfortune.chance.help"), false);
        return 1;
    }
}
