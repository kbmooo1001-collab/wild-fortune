package dev.wildfortune;

import dev.wildfortune.gear.GearAffix;
import dev.wildfortune.gear.GearAffixes;
import dev.wildfortune.monster.MonsterMutations;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class LocalizationGameTest {
    @GameTest
    public void namesFollowSelectedLanguage(GameTestHelper test) throws Exception {
        Language original = Language.getInstance();
        Map<String, String> korean = translations("ko_kr");
        Map<String, String> english = translations("en_us");
        require(korean.keySet().equals(english.keySet()), "Korean/English translation keys differ");
        var zombie = EntityTypes.ZOMBIE.create(test.getLevel(), EntitySpawnReason.NATURAL);
        require(zombie != null, "Could not create zombie");
        zombie.addTag(MonsterMutations.ROLLED_TAG);
        zombie.addEffect(new MobEffectInstance(MonsterMutations.EFFECTS.get(0), -1));
        zombie.addEffect(new MobEffectInstance(MonsterMutations.EFFECTS.get(1), -1));
        MonsterMutations.onEntityLoad(zombie, test.getLevel());
        ItemStack plain = new ItemStack(Items.IRON_SWORD);
        GearAffixes.applyAll(plain, java.util.List.of());
        ItemStack combo = new ItemStack(Items.IRON_SWORD);
        GearAffixes.applyAll(combo, java.util.List.of(GearAffix.SHARP, GearAffix.SWIFT));
        // Reuse the same components across switches, as the client does after language reload.
        Component plainName = plain.getHoverName();
        Component comboName = combo.getHoverName();
        try {
            for (String locale : java.util.List.of("ko_kr", "en_us", "ko_kr")) {
                boolean ko = locale.equals("ko_kr");
                Map<String, String> values = new HashMap<>(ko ? korean : english);
                values.put("item.minecraft.iron_sword", ko ? "철 검" : "Iron Sword");
                values.put("entity.minecraft.zombie", ko ? "좀비" : "Zombie");
                Language.inject(new Language() {
                    public String getOrDefault(String key, String fallback) { return values.getOrDefault(key, original.getOrDefault(key, fallback)); }
                    public boolean has(String key) { return values.containsKey(key) || original.has(key); }
                    public boolean isDefaultRightToLeft() { return false; }
                    public FormattedCharSequence getVisualOrder(FormattedText text) { return original.getVisualOrder(text); }
                });
                require(plainName.getString().equals(ko ? "평범한 철 검" : "Plain Iron Sword"), "Neutral name not translated");
                require(comboName.getString().equals(ko ? "우수한 철 검" : "Fine Iron Sword"), "Combo name not translated");
                require(zombie.getCustomName().getString().equals(ko ? "강화된 좀비" : "Empowered Zombie"), "Monster name not translated");
                for (GearAffix effect : GearAffix.values()) {
                    String nameKey = "item.wildfortune.affix." + effect.id + ".name";
                    require(values.containsKey(nameKey), "Missing effect translation");
                    if (ko) require(!values.get(nameKey).replace("%s", "").matches(".*[A-Za-z].*"), "English effect name in Korean");
                }
                var countNames = new java.util.HashSet<String>();
                for (int total = 2; total <= 5; total++) {
                    for (int positive = 0; positive <= total; positive++) {
                        String key = "item.wildfortune.combo." + positive + "_" + (total - positive);
                        require(values.containsKey(key), "Missing count translation: " + key);
                        String translated = Component.translatable(key, "철 검").getString();
                        require(countNames.add(translated), "Different counts share a translated name");
                        require(!translated.contains("·") && !translated.contains("item.wildfortune"), "Old concatenated name remains");
                    }
                }
                require(countNames.size() == 18, "Expected 18 count names per language");
                for (String key : java.util.List.of("item.wildfortune.combo.5_0", "item.wildfortune.combo.0_5")) {
                    require(!values.get(key).contains("★") && !values.get(key).contains("☠"), "Top-tier decoration remains");
                    require(!values.get(key + ".label").contains("★") && !values.get(key + ".label").contains("☠"), "Table decoration remains");
                }
                require(GearAffixes.descriptions(combo).getFirst().getString().equals(ko ? "공격력 +10%" : "Damage +10%"),
                        "Stat description not translated");
                String report = dev.wildfortune.gear.AffixChanceTable.render().getString();
                require(report.contains(ko ? "수식어 1개 │ 23.7646%" : "One affix | 23.7646%"),
                        "Probability table or affix label not translated");
                require(report.contains(ko ? "양면의 │ 17.2089%" : "Dual | 17.2089%"), "Combined final name not translated");
                require(!report.contains("command.wildfortune") && !report.contains(".label"), "Unresolved translation key in table");
            }
        } finally {
            Language.inject(original);
            zombie.discard();
        }
        test.succeed();
    }

    private static Map<String, String> translations(String locale) throws Exception {
        Map<String, String> values = new HashMap<>();
        try (var input = LocalizationGameTest.class.getResourceAsStream("/assets/wildfortune/lang/" + locale + ".json")) {
            require(input != null, "Language resource missing: " + locale);
            Language.loadFromJson(input, values::put);
        }
        return values;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
