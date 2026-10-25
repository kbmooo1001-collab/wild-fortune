package dev.wildfortune.monster;

import dev.wildfortune.config.WildFortuneConfig;
import dev.wildfortune.gear.AffixChanceTable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;

/** Exact distribution of independent rolls for eligible natural and structure monster spawns. */
public final class MonsterChanceTable {
    public record Row(int rank, BigDecimal probability) {}
    private MonsterChanceTable() {}

    public static List<Row> rows(WildFortuneConfig.Settings settings) {
        BigDecimal[] distribution = {BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        int processed = 0;
        for (String id : WildFortuneConfig.MONSTER_EFFECT_IDS) {
            var rule = settings.monsters().get(id);
            BigDecimal p = settings.monstersEnabled() && rule.enabled() ? BigDecimal.valueOf(rule.chance()) : BigDecimal.ZERO;
            for (int rank = ++processed; rank >= 0; rank--) {
                distribution[rank] = distribution[rank].multiply(BigDecimal.ONE.subtract(p))
                        .add(rank == 0 ? BigDecimal.ZERO : distribution[rank - 1].multiply(p));
            }
        }
        List<Row> rows = new ArrayList<>();
        for (int rank = 0; rank <= 4; rank++) rows.add(new Row(rank, distribution[rank]));
        return rows.stream().sorted(Comparator.comparing(Row::probability).reversed().thenComparingInt(Row::rank)).toList();
    }

    public static Component render() {
        var result = Component.empty().append(Component.translatable("command.wildfortune.monsters.title").withStyle(ChatFormatting.AQUA))
                .append("\n").append(Component.translatable("command.wildfortune.monsters.header").withStyle(ChatFormatting.GRAY));
        for (Row row : rows(WildFortuneConfig.get())) {
            var style = row.rank() == 0 ? net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.GRAY)
                    : MonsterMutations.rankStyle(row.rank());
            var label = Component.translatable("entity.wildfortune.rank." + row.rank() + ".label").withStyle(style);
            result.append("\n").append(Component.translatable("command.wildfortune.chance.row", label,
                    AffixChanceTable.percent(row.probability())).withStyle(style.withFont(FontDescription.DEFAULT)));
        }
        return result.append("\n").append(Component.translatable("command.wildfortune.monsters.note").withStyle(ChatFormatting.GRAY));
    }
}
