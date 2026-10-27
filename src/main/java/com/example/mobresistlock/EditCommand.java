package com.example.mobresistlock;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Comparator;
import java.util.List;

/**
 * /edit resistances true    -> mobs can learn resistances (normal Tensura behaviour)
 * /edit resistances false   -> mobs can no longer learn resistances after spawning
 * /edit resistances         -> shows the current value
 * /edit resistances debug   -> shows what the mod sees on the nearest mob (for troubleshooting)
 */
public final class EditCommand {
    private EditCommand() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("edit")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("resistances")
                        .executes(EditCommand::show)
                        .then(Commands.literal("debug").executes(EditCommand::debug))
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(EditCommand::set))));
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        boolean can = LockState.get(ctx.getSource().getServer()).mobsCanLearn();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Mobs can learn resistances: " + can), false);
        return can ? 1 : 0;
    }

    private static int set(CommandContext<CommandSourceStack> ctx) {
        boolean value = BoolArgumentType.getBool(ctx, "value");
        LockState.get(ctx.getSource().getServer()).setMobsCanLearn(value);
        ctx.getSource().sendSuccess(() -> Component.literal(
                value ? "Mobs can learn resistances again (true)."
                      : "Mobs can no longer learn resistances after spawning (false)."), true);
        return 1;
    }

    private static int debug(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSystemMessage(Component.literal("[mobresistlock] " + TensuraBridge.statusReport()));

        List<Mob> mobs = src.getLevel().getEntitiesOfClass(Mob.class,
                new net.minecraft.world.phys.AABB(src.getPosition(), src.getPosition()).inflate(10));
        if (mobs.isEmpty()) {
            src.sendSystemMessage(Component.literal("[mobresistlock] No mob within 10 blocks."));
            return 0;
        }
        Mob nearest = mobs.stream()
                .min(Comparator.comparingDouble(m -> m.distanceToSqr(src.getPosition())))
                .get();
        src.sendSystemMessage(Component.literal("[mobresistlock] Nearest mob: " + nearest.getType().toShortString()));

        List<String> all = TensuraBridge.describeAllSkills(nearest);
        if (all == null) {
            src.sendSystemMessage(Component.literal("[mobresistlock] Could not read skills (bridge not resolved)."));
            return 0;
        }
        src.sendSystemMessage(Component.literal("[mobresistlock] Skills found: " + all.size()));
        for (String s : all) {
            boolean res = TensuraBridge.looksLikeResistance(s);
            src.sendSystemMessage(Component.literal("  " + (res ? "[R] " : "    ") + s));
        }
        return 1;
    }
}
