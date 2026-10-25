package dev.wildfortune.gear;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerPlayer;

public final class GearExperience {
    private record Mining(ServerPlayer player, double bonus) {}
    private static final ThreadLocal<Mining> MINING = new ThreadLocal<>();
    private static final Map<ServerPlayer, double[]> REMAINDERS = new WeakHashMap<>();
    private GearExperience() {}
    public static boolean duringMining(ServerPlayer player, BooleanSupplier action) {
        Mining previous = MINING.get();
        var held = player.getMainHandItem();
        double bonus = GearAffixes.category(held) == GearAffixes.Category.TOOL
                ? GearAffixes.net(held, GearAffix.Kind.EXPERIENCE) : 0;
        MINING.set(new Mining(player, bonus));
        try { return action.getAsBoolean(); }
        finally { if (previous == null) MINING.remove(); else MINING.set(previous); }
    }
    public static int miningExperience(int vanilla) {
        Mining context = MINING.get();
        return context == null ? vanilla : scale(context.player(), vanilla, context.bonus(), 0);
    }
    public static int utilityExperience(ServerPlayer player, int vanilla) {
        var held = player.getMainHandItem();
        return GearAffixes.category(held) == GearAffixes.Category.UTILITY
                ? scale(player, vanilla, GearAffixes.net(held, GearAffix.Kind.EXPERIENCE), 1) : vanilla;
    }
    private static int scale(ServerPlayer player, int vanilla, double bonus, int channel) {
        if (vanilla <= 0 || bonus == 0) return vanilla;
        double[] remainder = REMAINDERS.computeIfAbsent(player, ignored -> new double[2]);
        double exact = vanilla * (1 + bonus) + remainder[channel];
        int result = (int) Math.min(Integer.MAX_VALUE, Math.floor(exact + 0.000000001));
        remainder[channel] = Math.max(0, exact - result);
        return result;
    }
}
