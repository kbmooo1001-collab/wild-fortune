package dev.wildfortune.config;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.wildfortune.gear.AffixChanceTable;
import dev.wildfortune.gear.GearAffix;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class AffixChanceCommandGameTest {
    @GameTest
    public void publicCommandsWorkWithoutReloadPermission(GameTestHelper test) throws Exception {
        var dispatcher = test.getLevel().getServer().getCommands().getDispatcher();
        var output = new Capture();
        var source = test.getLevel().getServer().createCommandSourceStack().withSource(output).withPermission(PermissionSet.NO_PERMISSIONS);
        for (int attempt = 0; attempt < 2; attempt++) {
            output.messages.clear();
            require(dispatcher.execute("wildfortune chance", source) == 1, "Public command failed");
            require(output.messages.size() == 1, "Expected one private report");
            var report = output.messages.getFirst();
            require(report.getSiblings().stream().anyMatch(c -> key(c).equals("command.wildfortune.chance.header")), "Missing table header");
        }
        require(dispatcher.execute("wildfortune chance", source) == 1, "Public help failed");
        var previous = WildFortuneConfig.get();
        try {
            dispatcher.execute("wildfortune reload", source);
            throw new AssertionError("Unprivileged user could execute reload");
        } catch (CommandSyntaxException expected) {
            require(WildFortuneConfig.get().equals(previous), "Rejected reload changed config");
        }
        test.succeed();
    }

    @GameTest
    public void typeRestrictionsAndCancellationProbabilitiesAreAccurate(GameTestHelper test) {
        var settings = WildFortuneConfig.defaults();
        Item[] items = {Items.IRON_PICKAXE, Items.IRON_SWORD, Items.IRON_CHESTPLATE, Items.BOW, Items.SHIELD, Items.ELYTRA};
        int[] expectedCounts = {29, 29, 29, 29, 29, 29};
        int[] kinds = {5, 5, 5, 5, 5, 5};
        for (int i = 0; i < items.length; i++) {
            var rows = AffixChanceTable.rows(new ItemStack(items[i]), settings);
            require(rows.size() == expectedCounts[i], "Wrong effects in table: " + items[i]);
            for (var row : rows) {
                require(row.probability().signum() > 0, "Impossible outcome displayed");
            }
            close(probability(rows, "item.wildfortune.affix.plain.name"), Math.pow(0.58, kinds[i]), "Plain probability ignores cancellations");
            require(rows.stream().map(AffixChanceTable.Row::probability).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                    .compareTo(java.math.BigDecimal.ONE) == 0, "Final outcomes must sum to exactly one");
        }
        var weapon = AffixChanceTable.rows(new ItemStack(Items.IRON_SWORD), settings);
        require(weapon.stream().noneMatch(row -> row.nameKey().contains("efficient") || row.nameKey().contains("sturdy")),
                "Weapon table includes mining/armor effects");
        close(probability(weapon, "item.wildfortune.affix.sharp.name"), 0.21 * Math.pow(0.58, 4), "Single prefix is exclusive of every other effect");
        close(probability(weapon, "item.wildfortune.combo.1_1"), 20 * Math.pow(0.21, 2) * Math.pow(0.58, 3), "Same count combinations not merged");
        require(AffixChanceTable.percent(new java.math.BigDecimal("0.21")).equals("21%"), "Floating point artifacts in percentage");
        require(AffixChanceTable.percent(new java.math.BigDecimal("0.000000001")).equals("<0.0001%"), "Rare outcome incorrectly shown as zero");
        test.succeed();
    }

    @GameTest
    public void reportUsesCurrentSettingsAndDisabledEffects(GameTestHelper test) {
        var previous = WildFortuneConfig.get();
        try {
            var tree = com.google.gson.JsonParser.parseString(WildFortuneConfig.toJson(WildFortuneConfig.defaults())).getAsJsonObject();
            var rules = tree.getAsJsonObject("gear").getAsJsonObject("effects");
            for (GearAffix effect : GearAffix.values()) rules.getAsJsonObject(effect.id).addProperty("chance", 0);
            rules.getAsJsonObject("sharp").addProperty("chance", 0.6);
            rules.getAsJsonObject("blunt").addProperty("chance", 0.2);
            rules.getAsJsonObject("light").addProperty("enabled", false);
            WildFortuneConfig.install(WildFortuneConfig.parse(tree.toString()));
            var rows = AffixChanceTable.rows(new ItemStack(Items.IRON_SWORD), WildFortuneConfig.get());
            require(rows.size() == 3, "Disabled or impossible names included");
            close(probability(rows, "item.wildfortune.affix.sharp.name"), 0.48, "Asymmetric positive chance");
            close(probability(rows, "item.wildfortune.affix.blunt.name"), 0.08, "Asymmetric negative chance");
            close(probability(rows, "item.wildfortune.affix.plain.name"), 0.44, "Asymmetric plain chance");
            var report = AffixChanceTable.render();
            require(report.getSiblings().stream().filter(c -> key(c).equals("command.wildfortune.chance.row"))
                    .map(c -> (TranslatableContents) c.getContents()).anyMatch(text -> text.getArgs().length == 2 && text.getArgs()[1].equals("0% ~ 56%")),
                    "Rendered report uses stale/default settings");
            WildFortuneConfig.install(WildFortuneConfig.parse("{\"gear\":{\"enabled\":false}}"));
            rows = AffixChanceTable.rows(new ItemStack(Items.IRON_SWORD), WildFortuneConfig.get());
            require(rows.size() == 1, "Global disable should only show plain");
            close(probability(rows, "item.wildfortune.affix.plain.name"), 1, "Disabled gear should always be plain");
            rules.getAsJsonObject("sharp").addProperty("chance", 1);
            rules.getAsJsonObject("blunt").addProperty("chance", 1);
            WildFortuneConfig.install(WildFortuneConfig.parse(tree.toString()));
            rows = AffixChanceTable.rows(new ItemStack(Items.IRON_SWORD), WildFortuneConfig.get());
            require(rows.size() == 1, "Certain opposite pair should cancel into plain only");
            close(probability(rows, "item.wildfortune.affix.plain.name"), 1, "Certain cancellation incorrect");
        } finally { WildFortuneConfig.install(previous); }
        test.succeed();
    }

    @GameTest
    public void unifiedReportCombinesSinglesAndSortsByDescendingChance(GameTestHelper test) throws Exception {
        var output = new Capture();
        var source = test.getLevel().getServer().createCommandSourceStack().withSource(output).withPermission(PermissionSet.NO_PERMISSIONS);
        var dispatcher = test.getLevel().getServer().getCommands().getDispatcher();
        var children = dispatcher.getRoot().getChild("wildfortune").getChild("chance").getChildren();
        require(children.size() == 1 && children.iterator().next().getName().equals("monsters"), "Unexpected gear type subcommands");
        require(dispatcher.execute("wildfortune chance", source) == 1, "Unified report failed");
        var rows = AffixChanceTable.summary(WildFortuneConfig.defaults());
        require(rows.size() == 20, "Expected 18 combos, one combined single row and plain");
        require(rows.getFirst().nameKey().equals(AffixChanceTable.SINGLE_KEY), "Largest probability must appear first");
        require(rows.get(1).nameKey().equals("item.wildfortune.combo.1_1"), "Dual should follow singles under defaults");
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            if (i > 0) require(rows.get(i - 1).maximum().compareTo(row.maximum()) >= 0, "Probability order is not descending");
            require(row.minimum().compareTo(row.maximum()) == 0, "Default five-kind equipment probabilities differ");
        }
        var single = rows.stream().filter(row -> row.nameKey().equals(AffixChanceTable.SINGLE_KEY)).findFirst().orElseThrow();
        close(single.minimum().doubleValue(), 10 * 0.21 * Math.pow(0.58, 4), "Single outcomes not combined");
        require(rows.stream().map(AffixChanceTable.SummaryRow::minimum).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                .compareTo(java.math.BigDecimal.ONE) == 0, "Unified distribution does not sum to one");
        test.succeed();
    }

    @GameTest
    public void finalNamesMatchEveryRawCraftCombination(GameTestHelper test) {
        var defaults = WildFortuneConfig.defaults();
        var asymmetric = WildFortuneConfig.parse("""
            {"gear":{"effects":{"sharp":{"chance":0.6},"blunt":{"chance":0.2},
              "swift":{"chance":0.3},"heavy":{"chance":0.7},"light":{"enabled":false}}}}
            """);
        for (var settings : List.of(defaults, asymmetric)) {
            for (Item item : List.of(Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_CHESTPLATE, Items.BOW, Items.SHIELD, Items.ELYTRA)) {
                var sample = new ItemStack(item);
                var allowed = java.util.Arrays.stream(GearAffix.values()).filter(e -> dev.wildfortune.gear.GearAffixes.supports(sample, e)).toList();
                var expected = new java.util.HashMap<String, java.math.BigDecimal>();
                for (int mask = 0; mask < 1 << allowed.size(); mask++) {
                    var effects = new ArrayList<GearAffix>();
                    var weight = java.math.BigDecimal.ONE;
                    for (int i = 0; i < allowed.size(); i++) {
                        GearAffix effect = allowed.get(i);
                        var rule = settings.gear().get(effect);
                        var chance = java.math.BigDecimal.valueOf(rule.enabled() ? rule.chance() : 0);
                        boolean selected = (mask & 1 << i) != 0;
                        weight = weight.multiply(selected ? chance : java.math.BigDecimal.ONE.subtract(chance));
                        if (selected) effects.add(effect);
                    }
                    if (weight.signum() == 0) continue;
                    var crafted = new ItemStack(item);
                    dev.wildfortune.gear.GearAffixes.applyAll(crafted, effects);
                    expected.merge(key(crafted.getHoverName()), weight, java.math.BigDecimal::add);
                }
                var actual = AffixChanceTable.rows(sample, settings);
                require(actual.size() == expected.size(), "Craft outcome name count differs");
                for (var row : actual) require(expected.get(row.nameKey()) != null
                        && expected.get(row.nameKey()).compareTo(row.probability()) == 0, "Wrong final name probability: " + row.nameKey());
            }
        }
        test.succeed();
    }

    private static double probability(List<AffixChanceTable.Row> rows, String key) {
        return rows.stream().filter(row -> row.nameKey().equals(key)).findFirst().orElseThrow().probability().doubleValue();
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents text ? text.getKey() : "";
    }
    private static final class Capture implements CommandSource {
        final List<Component> messages = new ArrayList<>();
        public void sendSystemMessage(Component message) { messages.add(message); }
        public boolean acceptsSuccess() { return true; }
        public boolean acceptsFailure() { return true; }
        public boolean shouldInformAdmins() { return false; }
    }
    private static void close(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 0.00000001, message + ": " + actual + " expected " + expected);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
