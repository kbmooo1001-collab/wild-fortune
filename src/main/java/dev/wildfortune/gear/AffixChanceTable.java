package dev.wildfortune.gear;

import dev.wildfortune.config.WildFortuneConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Read-only server-side report. Uses the same item restrictions as crafting. */
public final class AffixChanceTable {
    public record Row(String nameKey, int total, int positives, BigDecimal probability) {}
    public record SummaryRow(String nameKey, int total, int positives, BigDecimal minimum, BigDecimal maximum) {}
    public static final String SINGLE_KEY = "command.wildfortune.chance.single";
    private record Pair(GearAffix positive, GearAffix negative, BigDecimal positiveChance, BigDecimal negativeChance) {}
    private AffixChanceTable() {}

    public static List<Row> rows(ItemStack sample, WildFortuneConfig.Settings settings) {
        List<Pair> pairs = new ArrayList<>();
        for (GearAffix.Kind kind : GearAffix.Kind.values()) {
            GearAffix positive = null;
            GearAffix negative = null;
            for (GearAffix effect : GearAffix.values()) {
                if (effect.kind != kind || !GearAffixes.supports(sample, effect)) continue;
                if (effect.positive) positive = effect;
                else negative = effect;
            }
            if (positive != null || negative != null) pairs.add(new Pair(positive, negative,
                    chance(positive, settings), chance(negative, settings)));
        }
        Map<String, Row> names = new LinkedHashMap<>();
        combine(pairs, 0, EnumSet.noneOf(GearAffix.class), BigDecimal.ONE, names);
        return names.values().stream().sorted(Comparator.comparingInt(Row::total)
                .thenComparing(Comparator.comparingInt(Row::positives).reversed())
                .thenComparing(Row::nameKey)).toList();
    }

    private static void combine(List<Pair> pairs, int index, EnumSet<GearAffix> effects,
            BigDecimal probability, Map<String, Row> names) {
        if (probability.signum() == 0) return;
        if (index == pairs.size()) {
            String key = GearAffixes.nameKey(effects);
            int positives = (int) effects.stream().filter(effect -> effect.positive).count();
            Row row = new Row(key, effects.size(), positives, probability);
            names.merge(key, row, (a, b) -> new Row(a.nameKey(), a.total(), a.positives(), a.probability().add(b.probability())));
            return;
        }
        Pair pair = pairs.get(index);
        BigDecimal p = pair.positiveChance();
        BigDecimal n = pair.negativeChance();
        // A kind disappears both when neither side rolls and when both sides cancel.
        BigDecimal none = BigDecimal.ONE.subtract(p).multiply(BigDecimal.ONE.subtract(n)).add(p.multiply(n));
        combine(pairs, index + 1, effects, probability.multiply(none), names);
        if (pair.positive() != null) {
            effects.add(pair.positive());
            combine(pairs, index + 1, effects, probability.multiply(p).multiply(BigDecimal.ONE.subtract(n)), names);
            effects.remove(pair.positive());
        }
        if (pair.negative() != null) {
            effects.add(pair.negative());
            combine(pairs, index + 1, effects, probability.multiply(n).multiply(BigDecimal.ONE.subtract(p)), names);
            effects.remove(pair.negative());
        }
    }

    private static BigDecimal chance(GearAffix effect, WildFortuneConfig.Settings settings) {
        if (effect == null) return BigDecimal.ZERO;
        var rule = settings.gear().get(effect);
        return settings.gearEnabled() && rule.enabled() ? BigDecimal.valueOf(rule.chance()) : BigDecimal.ZERO;
    }

    public static List<SummaryRow> summary(WildFortuneConfig.Settings settings) {
        List<Map<String, Row>> types = new ArrayList<>();
        Map<String, Row> names = new LinkedHashMap<>();
        for (var item : List.of(Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_CHESTPLATE,
                Items.ELYTRA, Items.SHIELD, Items.BOW, Items.FISHING_ROD)) {
            Map<String, Row> type = new LinkedHashMap<>();
            for (Row row : rows(new ItemStack(item), settings)) {
                Row grouped = row.total() == 1 ? new Row(SINGLE_KEY, 1, 0, row.probability()) : row;
                type.merge(grouped.nameKey(), grouped,
                        (a, b) -> new Row(a.nameKey(), a.total(), a.positives(), a.probability().add(b.probability())));
            }
            names.putAll(type);
            types.add(type);
        }
        List<SummaryRow> result = new ArrayList<>();
        for (Row name : names.values()) {
            BigDecimal min = BigDecimal.ONE;
            BigDecimal max = BigDecimal.ZERO;
            for (Map<String, Row> type : types) {
                BigDecimal chance = type.containsKey(name.nameKey()) ? type.get(name.nameKey()).probability() : BigDecimal.ZERO;
                min = min.min(chance);
                max = max.max(chance);
            }
            if (max.signum() > 0) result.add(new SummaryRow(name.nameKey(), name.total(), name.positives(), min, max));
        }
        return result.stream().sorted(Comparator.comparing(SummaryRow::maximum).reversed()
                .thenComparing(Comparator.comparing(SummaryRow::minimum).reversed())
                .thenComparing(Comparator.comparingInt(SummaryRow::positives).reversed())
                .thenComparing(SummaryRow::nameKey)).toList();
    }

    public static Component render() {
        var rows = summary(WildFortuneConfig.get());
        var result = Component.empty()
                .append(Component.translatable("command.wildfortune.chance.title").withStyle(ChatFormatting.AQUA))
                .append("\n").append(Component.translatable("command.wildfortune.chance.header").withStyle(ChatFormatting.GRAY));
        for (SummaryRow row : rows) {
            String key = row.nameKey();
            String labelKey = key.equals(SINGLE_KEY) ? key : key.endsWith(".name") ? key.substring(0, key.length() - 5) + ".label" : key + ".label";
            var rowStyle = row.total() == 1 ? net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.GRAY)
                    : GearAffixes.nameStyle(row.total(), row.positives());
            Component label = Component.translatable(labelKey).withStyle(rowStyle);
            String probability = percent(row.minimum());
            if (row.minimum().compareTo(row.maximum()) != 0) probability += " ~ " + percent(row.maximum());
            var line = Component.translatable("command.wildfortune.chance.row", label,
                    probability)
                    .withStyle(rowStyle.withFont(net.minecraft.network.chat.FontDescription.DEFAULT));
            result.append("\n").append(line);
        }
        result.append("\n").append(Component.translatable("command.wildfortune.chance.note").withStyle(ChatFormatting.GRAY));
        if (rows.stream().anyMatch(row -> row.minimum().compareTo(row.maximum()) != 0)) {
            result.append("\n").append(Component.translatable("command.wildfortune.chance.range").withStyle(ChatFormatting.YELLOW));
        }
        return result;
    }

    public static String percent(BigDecimal probability) {
        BigDecimal rounded = probability.multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP);
        if (probability.signum() > 0 && rounded.signum() == 0) return "<0.0001%";
        return rounded.stripTrailingZeros().toPlainString() + "%";
    }
}
