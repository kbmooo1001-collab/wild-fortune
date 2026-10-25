package dev.wildfortune.config;

import dev.wildfortune.gear.GearAffix;
import dev.wildfortune.gear.GearAffixes;
import dev.wildfortune.monster.MonsterMutations;
import java.nio.file.Files;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RepairItemRecipe;

public final class WildFortuneConfigGameTest {
    @GameTest
    public void firstRunGeneratesAgreedDefaultsAndPreservesUserEdits(GameTestHelper test) throws Exception {
        var previous = WildFortuneConfig.get();
        try {
            var directory = Files.createTempDirectory(WildFortuneConfig.path().toAbsolutePath().getParent(), "wf-first-run-");
            var file = directory.resolve("new-instance/config/wildfortune.json");
            require(!Files.exists(file) && WildFortuneConfig.load(file), "First-run config was not generated");
            String generated = Files.readString(file);
            var settings = WildFortuneConfig.parse(generated);
            require(settings.gear().size() == 30 && settings.monsters().size() == 4, "Generated config incomplete");
            require(settings.gear().values().stream().allMatch(rule -> rule.enabled() && rule.chance() == 0.30), "Fresh gear defaults must be 30%");
            require(settings.monsters().values().stream().allMatch(rule -> rule.enabled() && rule.chance() == 0.35), "Fresh monster defaults must be 35%");
            require(settings.monsters().get("health").amount() == 0.25, "Health bonus changed");
            var edited = com.google.gson.JsonParser.parseString(generated).getAsJsonObject();
            edited.getAsJsonObject("gear").getAsJsonObject("effects").getAsJsonObject("sharp").addProperty("chance", 0.07);
            edited.getAsJsonObject("monsters").getAsJsonObject("effects").getAsJsonObject("speed").addProperty("chance", 0.66);
            String userFile = edited.toString();
            Files.writeString(file, userFile);
            for (int repeat = 0; repeat < 2; repeat++) {
                require(WildFortuneConfig.load(file), "Edited auto-generated config rejected");
                require(WildFortuneConfig.get().gear().get(GearAffix.SHARP).chance() == 0.07
                        && WildFortuneConfig.get().monsters().get("speed").chance() == 0.66, "User-edited chances ignored");
                require(Files.readString(file).equals(userFile), "Existing config overwritten by defaults");
            }
        } finally { WildFortuneConfig.install(previous); }
        test.succeed();
    }
    @GameTest
    public void structureTemplatesKeepEligibilityAfterNbtLoadAndNeverReroll(GameTestHelper test) {
        var previous = WildFortuneConfig.get();
        try {
            for (double chance : new double[] {0, 1}) {
                var document = com.google.gson.JsonParser.parseString(WildFortuneConfig.toJson(previous)).getAsJsonObject();
                var effects = document.getAsJsonObject("monsters").getAsJsonObject("effects");
                document.getAsJsonObject("monsters").addProperty("enabled", true);
                for (String id : WildFortuneConfig.MONSTER_EFFECT_IDS) {
                    effects.getAsJsonObject(id).addProperty("enabled", true);
                    effects.getAsJsonObject(id).addProperty("chance", chance);
                }
                WildFortuneConfig.install(WildFortuneConfig.parse(document.toString()));
                for (boolean typed : new boolean[] {false, true}) {
                    var tag = new net.minecraft.nbt.CompoundTag();
                    tag.putString("id", "minecraft:piglin_brute");
                    var tags = new net.minecraft.nbt.ListTag();
                    tags.add(net.minecraft.nbt.StringTag.valueOf("template_tag"));
                    tag.put("Tags", tags);
                    var input = net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,
                            test.getLevel().registryAccess(), tag);
                    var created = typed
                            ? net.minecraft.world.entity.EntityType.create(EntityTypes.PIGLIN_BRUTE, input, test.getLevel(), EntitySpawnReason.STRUCTURE)
                            : net.minecraft.world.entity.EntityType.create(input, test.getLevel(), new net.minecraft.world.entity.EntitySpawnRequest(EntitySpawnReason.STRUCTURE, false));
                    var mob = (net.minecraft.world.entity.Mob) created.orElseThrow();
                    require(mob.entityTags().contains("template_tag") && mob.entityTags().contains(MonsterMutations.NATURAL_TAG), "Template NBT replaced eligibility marker");
                    mob.finalizeSpawn(test.getLevel(), test.getLevel().getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.STRUCTURE, null);
                    boolean persistence = mob.isPersistenceRequired();
                    require(test.getLevel().addFreshEntity(mob), "Could not add structure brute");
                    int count = chance == 0 ? 0 : 4;
                    require(mob.entityTags().contains(MonsterMutations.ROLLED_TAG) && MonsterMutations.mutationCount(mob) == count, "Structure monster ignored configured chance");
                    require(mob.getHealth() == mob.getMaxHealth(), "Structure monster missing extra current health");
                    if (count == 4) require(mob.getMaxHealth() == 62.5F, "Brute health must increase from 50 to 62.5");
                    require(mob.isPersistenceRequired() == persistence, "Mutation changed structure persistence");
                    var name = mob.getCustomName();
                    mob.removeAllEffects();
                    var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, test.getLevel().registryAccess());
                    mob.saveWithoutId(output);
                    mob.discard();
                    var restored = EntityTypes.PIGLIN_BRUTE.create(test.getLevel(), EntitySpawnReason.LOAD);
                    restored.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, test.getLevel().registryAccess(), output.buildResult()));
                    for (int repeat = 0; repeat < 10; repeat++) MonsterMutations.onEntityLoad(restored, test.getLevel());
                    require(restored.getActiveEffects().isEmpty() && java.util.Objects.equals(name, restored.getCustomName()), "Structure monster rerolled or changed rank after reload");
                    require(MonsterMutations.hasHealthMutation(restored) == (chance == 1), "Saved health mutation changed");
                    restored.discard();
                }
            }
            // Historical entities loaded without our marker cannot be identified as structure spawns.
            var old = EntityTypes.PIGLIN_BRUTE.create(test.getLevel(), EntitySpawnReason.LOAD);
            MonsterMutations.onEntityLoad(old, test.getLevel());
            require(!old.entityTags().contains(MonsterMutations.ROLLED_TAG), "Old unmarked monster retroactively rolled");
            old.discard();
        } finally { WildFortuneConfig.install(previous); }
        test.succeed();
    }

    @GameTest(maxTicks = 200)
    public void naturalAndStructureBrutesUseIdenticalThirtyFivePercentRolls(GameTestHelper test) {
        var previous = WildFortuneConfig.get();
        try {
            var document = com.google.gson.JsonParser.parseString(WildFortuneConfig.toJson(previous)).getAsJsonObject();
            var monsters = document.getAsJsonObject("monsters");
            monsters.addProperty("enabled", true);
            for (String id : WildFortuneConfig.MONSTER_EFFECT_IDS) {
                monsters.getAsJsonObject("effects").getAsJsonObject(id).addProperty("enabled", true);
                monsters.getAsJsonObject("effects").getAsJsonObject(id).addProperty("chance", 0.35);
            }
            WildFortuneConfig.install(WildFortuneConfig.parse(document.toString()));
            int[] counts = new int[4];
            var seeds = net.minecraft.util.RandomSource.create(456789L);
            for (int sample = 0; sample < 1024; sample++) {
                var natural = EntityTypes.PIGLIN_BRUTE.create(test.getLevel(), EntitySpawnReason.NATURAL);
                var structure = EntityTypes.PIGLIN_BRUTE.create(test.getLevel(), EntitySpawnReason.STRUCTURE);
                long seed = seeds.nextLong();
                natural.getRandom().setSeed(seed);
                structure.getRandom().setSeed(seed);
                MonsterMutations.onEntityLoad(natural, test.getLevel());
                MonsterMutations.onEntityLoad(structure, test.getLevel());
                for (int effect = 0; effect < 4; effect++) {
                    boolean a = effect == 3 ? MonsterMutations.hasHealthMutation(natural) : natural.hasEffect(MonsterMutations.EFFECTS.get(effect));
                    boolean b = effect == 3 ? MonsterMutations.hasHealthMutation(structure) : structure.hasEffect(MonsterMutations.EFFECTS.get(effect));
                    require(a == b, "Structure and natural rolls differ");
                    if (b) counts[effect]++;
                }
                natural.discard();
                structure.discard();
            }
            for (int count : counts) require(Math.abs(count - 1024 * 0.35) < 70, "Structure effect frequency inconsistent with 35%");
        } finally { WildFortuneConfig.install(previous); }
        test.succeed();
    }
    @GameTest(maxTicks = 100)
    public void finalizedNaturalMonsterKeepsExtraCurrentHealthAfterTicks(GameTestHelper test) {
        var previous = WildFortuneConfig.get();
        net.minecraft.world.entity.monster.zombie.Zombie mob;
        try {
            WildFortuneConfig.install(WildFortuneConfig.parse("{\"monsters\":{\"effects\":{\"speed\":{\"chance\":0},\"resistance\":{\"chance\":0},\"strength\":{\"chance\":0},\"health\":{\"chance\":1}}}}"));
            mob = EntityTypes.ZOMBIE.create(test.getLevel(), EntitySpawnReason.NATURAL);
            mob.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(test.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1))));
            mob.finalizeSpawn(test.getLevel(), test.getLevel().getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.NATURAL, null);
            mob.setNoAi(true);
            // Keep this test subject loaded even when other tests move all mock players away.
            mob.setPersistenceRequired();
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            require(test.getLevel().addFreshEntity(mob), "Cannot add finalized natural zombie");
            require(mob.getMaxHealth() == 25 && mob.getHealth() == 25, "Initial natural spawn not 25/25");
        } finally { WildFortuneConfig.install(previous); }
        test.runAfterDelay(5, () -> {
            require(mob.getMaxHealth() == 25 && mob.getHealth() == 25, "Current health lost after actual server ticks: " + mob.getHealth());
            // Bypass armor so random spawn equipment cannot change the expected damage.
            mob.hurtServer(test.getLevel(), mob.damageSources().genericKill(), 20);
            require(mob.isAlive() && Math.abs(mob.getHealth() - 5) < 0.01, "Extra 5 HP did not absorb real damage: " + mob.getHealth());
            mob.discard();
            test.succeed();
        });
    }
    @GameTest
    public void healthRuleMigrationAndMonsterChances(GameTestHelper test) throws Exception {
        var defaults = WildFortuneConfig.defaults();
        var rows = dev.wildfortune.monster.MonsterChanceTable.rows(defaults);
        int[] order = {1, 2, 0, 3, 4};
        String[] probabilities = {"0.384475", "0.3105375", "0.17850625", "0.111475", "0.01500625"};
        for (int i = 0; i < order.length; i++) {
            require(rows.get(i).rank() == order[i], "Monster chances not sorted descending");
            require(rows.get(i).probability().compareTo(new java.math.BigDecimal(probabilities[i])) == 0, "Wrong default monster chance");
        }
        for (String json : List.of("{\"monsters\":{\"enabled\":false}}",
                "{\"monsters\":{\"effects\":{\"speed\":{\"chance\":1},\"resistance\":{\"chance\":0},\"strength\":{\"chance\":0},\"health\":{\"chance\":0.4}}}}")) {
            var distribution = dev.wildfortune.monster.MonsterChanceTable.rows(WildFortuneConfig.parse(json));
            require(distribution.stream().map(dev.wildfortune.monster.MonsterChanceTable.Row::probability)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add).compareTo(java.math.BigDecimal.ONE) == 0, "Monster probabilities do not sum to one");
            if (json.contains("0.4")) require(distribution.getFirst().rank() == 1 && distribution.getFirst().probability().doubleValue() == 0.6, "Custom independent chances ignored");
            else require(distribution.getFirst().rank() == 0 && distribution.getFirst().probability().doubleValue() == 1, "Disabled monsters still mutate");
        }
        var previous = WildFortuneConfig.get();
        try {
            var directory = Files.createTempDirectory(WildFortuneConfig.path().toAbsolutePath().getParent(), "wf-health-test-");
            var file = directory.resolve("wildfortune.json");
            String original = "{\"schemaVersion\":2,\"monsters\":{\"effects\":{\"invisibility\":{\"enabled\":false,\"chance\":0.7,\"level\":2}}}}";
            Files.writeString(file, original);
            require(WildFortuneConfig.load(file), "Schema 2 health migration failed");
            require(Files.readString(directory.resolve("wildfortune.json.v2.bak")).equals(original), "Old configuration not backed up");
            var health = WildFortuneConfig.get().monsters().get("health");
            require(!health.enabled() && health.chance() == 0.7 && health.amount() == 0.25, "Legacy chance/enabled lost or health amount incorrect");
            require(!Files.readString(file).contains("invisibility"), "Migrated config retains old key");
            WildFortuneConfig.install(WildFortuneConfig.parse("{\"monsters\":{\"effects\":{\"speed\":{\"chance\":0},\"resistance\":{\"chance\":0},\"strength\":{\"chance\":0},\"health\":{\"chance\":1,\"amount\":0.5}}}}"));
            var mob = EntityTypes.ZOMBIE.create(test.getLevel(), EntitySpawnReason.NATURAL);
            MonsterMutations.onEntityLoad(mob, test.getLevel());
            require(mob.getMaxHealth() == 30 && mob.getHealth() == 30 && MonsterMutations.mutationCount(mob) == 1, "Configured health amount not applied");
            WildFortuneConfig.install(defaults);
            MonsterMutations.onEntityLoad(mob, test.getLevel());
            require(mob.getMaxHealth() == 30, "Reload changed saved health amount");
            mob.discard();
        } finally { WildFortuneConfig.install(previous); }
        var source = test.getLevel().getServer().createCommandSourceStack().withPermission(PermissionSet.NO_PERMISSIONS);
        require(test.getLevel().getServer().getCommands().getDispatcher().execute("wildfortune chance monsters", source) == 1, "Monster chance command requires permissions");
        test.succeed();
    }
    @GameTest
    public void defaultsRoundTripAndPartialSettings(GameTestHelper test) {
        var defaults = WildFortuneConfig.defaults();
        require(WildFortuneConfig.parse(WildFortuneConfig.toJson(defaults)).equals(defaults), "Default serialization changed settings");
        require(WildFortuneConfig.parse("{}").equals(defaults), "Omitted settings did not use defaults");
        var parsed = WildFortuneConfig.parse("""
                {"monsters":{"effects":{"speed":{"chance":0.6,"level":3}}},
                 "gear":{"effects":{"sharp":{"chance":0.5,"amount":0.25},"blunt":{"enabled":false}}}}
                """);
        require(parsed.monsters().get("speed").level() == 3 && parsed.monsters().get("speed").chance() == 0.6, "Monster settings ignored");
        require(parsed.gear().get(GearAffix.SHARP).amount() == 0.25 && !parsed.gear().get(GearAffix.BLUNT).enabled(), "Gear settings ignored");
        require(parsed.gear().get(GearAffix.LIGHT).equals(defaults.gear().get(GearAffix.LIGHT)), "Partial edit changed unrelated setting");
        test.succeed();
    }

    @GameTest
    public void invalidFileKeepsPreviousSettingsAndOriginalFile(GameTestHelper test) throws Exception {
        var previous = WildFortuneConfig.get();
        try {
            var directory = Files.createTempDirectory(WildFortuneConfig.path().toAbsolutePath().getParent(), "wf-config-test-");
            var path = directory.resolve("wildfortune.json");
            require(WildFortuneConfig.load(path), "Default file not created");
            require(Files.readString(path).contains("excludedEntities"), "Generated default file incomplete");
            Files.writeString(path, "{\"gear\":{\"effects\":{\"sharp\":{\"chance\":1}}}}");
            require(WildFortuneConfig.load(path), "Valid edited file rejected");
            var valid = WildFortuneConfig.get();
            for (String broken : List.of("not json", "[]", "{\"gear\":null}",
                    "{\"gear\":{\"effects\":{\"sharp\":{\"chance\":2}}}}",
                    "{\"gear\":{\"effects\":{\"sharp\":{\"amount\":-0.1}}}}",
                    "{\"gear\":{\"effects\":{\"blunt\":{\"amount\":-1}}}}",
                    "{\"monsters\":{\"effects\":{\"speed\":{\"level\":1.5}}}}",
                    "{\"monsters\":{\"effects\":{\"typo\":{}}}}",
                    "{\"gear\":{\"enabled\":\"false\"}}", "{\"schemaVersion\":4}",
                    "{\"monsters\":{\"excludedEntities\":[\"not an id\"]}}")) {
                Files.writeString(path, broken);
                require(!WildFortuneConfig.load(path), "Invalid config accepted: " + broken);
                require(WildFortuneConfig.get().equals(valid), "Invalid file replaced active settings");
                require(Files.readString(path).equals(broken), "Invalid user file overwritten");
            }
        } finally { WildFortuneConfig.install(previous); }
        test.succeed();
    }

    @GameTest
    public void configuredMonsterRulesAndExclusionsAffectOnlyNewRolls(GameTestHelper test) {
        var previous = WildFortuneConfig.get();
        try {
            WildFortuneConfig.install(WildFortuneConfig.parse("""
                {"monsters":{"effects":{"speed":{"chance":1,"level":3},"resistance":{"chance":0},
                    "strength":{"enabled":false},"invisibility":{"chance":0}}}}
                """));
            var mob = EntityTypes.ZOMBIE.create(test.getLevel(), EntitySpawnReason.NATURAL);
            require(mob != null, "No zombie");
            MonsterMutations.onEntityLoad(mob, test.getLevel());
            require(mob.getActiveEffects().size() == 1 && mob.getEffect(MonsterMutations.EFFECTS.getFirst()).getAmplifier() == 2,
                    "Configured chance/level/disabled effects ignored");
            WildFortuneConfig.install(WildFortuneConfig.parse("{\"monsters\":{\"enabled\":false}}"));
            MonsterMutations.onEntityLoad(mob, test.getLevel());
            require(mob.getEffect(MonsterMutations.EFFECTS.getFirst()).getAmplifier() == 2, "Reload changed existing monster");
            mob.discard();
            var disabled = EntityTypes.ZOMBIE.create(test.getLevel(), EntitySpawnReason.NATURAL);
            require(disabled != null && !disabled.entityTags().contains(MonsterMutations.NATURAL_TAG), "Disabled monsters still roll");
            disabled.discard();
            WildFortuneConfig.install(WildFortuneConfig.parse("{\"monsters\":{\"excludedEntities\":[\"minecraft:zombie\"]}}"));
            var excluded = EntityTypes.ZOMBIE.create(test.getLevel(), EntitySpawnReason.NATURAL);
            require(excluded != null && !MonsterMutations.isEligible(excluded), "Extra exclusion ignored");
            MonsterMutations.onEntityLoad(excluded, test.getLevel());
            require(!excluded.entityTags().contains(MonsterMutations.ROLLED_TAG), "Excluded monster processed");
            excluded.discard();
        } finally { WildFortuneConfig.install(previous); }
        test.succeed();
    }

    @GameTest
    public void configuredGearRollsStillRestrictCancelAndNeverRetry(GameTestHelper test) {
        var previous = WildFortuneConfig.get();
        try {
            var tree = com.google.gson.JsonParser.parseString(WildFortuneConfig.toJson(WildFortuneConfig.defaults())).getAsJsonObject();
            var rules = tree.getAsJsonObject("gear").getAsJsonObject("effects");
            for (GearAffix effect : GearAffix.values()) rules.getAsJsonObject(effect.id).addProperty("chance", 0);
            for (String id : List.of("sharp", "blunt", "swift", "efficient")) rules.getAsJsonObject(id).addProperty("chance", 1);
            WildFortuneConfig.install(WildFortuneConfig.parse(tree.toString()));
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            GearAffixes.roll(sword, RandomSource.create(10));
            require(GearAffixes.getAll(sword).equals(List.of(GearAffix.SWIFT)), "Configured rolls missed cancellation or item restrictions");
            var pickaxe = new ItemStack(Items.IRON_PICKAXE);
            GearAffixes.roll(pickaxe, RandomSource.create(10));
            require(GearAffixes.getAll(pickaxe).equals(List.of(GearAffix.EFFICIENT)), "Tool accepts configured melee-only effect");
            WildFortuneConfig.install(WildFortuneConfig.parse("{\"gear\":{\"enabled\":false}}"));
            var plain = new ItemStack(Items.IRON_SWORD);
            GearAffixes.roll(plain, RandomSource.create(0));
            require(GearAffixes.isProcessed(plain) && GearAffixes.getAll(plain).isEmpty(), "Disabled gear did not finalize empty result");
            WildFortuneConfig.install(WildFortuneConfig.parse(tree.toString()));
            GearAffixes.roll(plain, RandomSource.create(1));
            require(GearAffixes.getAll(plain).isEmpty(), "Re-enabling gear rerolled a completed item");
        } finally { WildFortuneConfig.install(previous); }
        test.succeed();
    }

    @GameTest
    public void savedStrengthSurvivesReloadSaveRepairAndUpgrade(GameTestHelper test) {
        var previous = WildFortuneConfig.get();
        try {
            WildFortuneConfig.install(WildFortuneConfig.parse("""
                {"gear":{"effects":{"sharp":{"amount":0.25},"durable":{"amount":0.35},"light":{"amount":0.07}}}}
                """));
            ItemStack first = new ItemStack(Items.DIAMOND_SWORD);
            GearAffixes.applyAll(first, List.of(GearAffix.SHARP, GearAffix.DURABLE, GearAffix.LIGHT));
            close(GearAffixes.net(first, GearAffix.Kind.ATTACK), 0.25, "Configured damage not saved");
            int max = (int) Math.round(new ItemStack(Items.DIAMOND_SWORD).getMaxDamage() * 1.35);
            require(first.getMaxDamage() == max, "Configured durability not applied");
            // Simulate a client with different local settings, or a later server config reload.
            WildFortuneConfig.install(WildFortuneConfig.defaults());
            close(GearAffixes.net(first, GearAffix.Kind.ATTACK), 0.25, "Local config changed existing gear");
            AttributeInstance damage = new AttributeInstance(Attributes.ATTACK_DAMAGE, changed -> {});
            damage.setBaseValue(1);
            first.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                if (attribute.equals(Attributes.ATTACK_DAMAGE)) damage.addTransientModifier(modifier);
            });
            close(damage.getValue(), 8.75, "Saved strength not used for real attributes");
            var descriptions = GearAffixes.descriptions(first);
            require(((TranslatableContents) descriptions.get(2).getContents()).getArgs()[0].equals("+7%"), "Float rounding leaked into tooltip");
            var ops = test.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
            var loaded = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, first).getOrThrow()).getOrThrow();
            close(GearAffixes.net(loaded, GearAffix.Kind.MOVEMENT), 0.07, "Snapshot lost after save/load");
            loaded.setDamageValue(1000);
            ItemStack second = new ItemStack(Items.DIAMOND_SWORD);
            second.setDamageValue(1000);
            var repaired = new RepairItemRecipe().assemble(CraftingInput.of(2, 1, List.of(loaded, second)));
            close(GearAffixes.net(repaired, GearAffix.Kind.ATTACK), 0.25, "Repair replaced original strength");
            require(repaired.getMaxDamage() == max, "Repair replaced original durability");
            var upgraded = new ItemStack(Items.NETHERITE_SWORD);
            GearAffixes.inherit(upgraded, loaded);
            close(GearAffixes.net(upgraded, GearAffix.Kind.ATTACK), 0.25, "Upgrade replaced original strength");
            require(upgraded.getMaxDamage() == (int) Math.round(new ItemStack(Items.NETHERITE_SWORD).getMaxDamage() * 1.35),
                    "Upgrade used old material or new config durability");
            ItemStack legacy = first.copy();
            net.minecraft.world.item.component.CustomData.update(DataComponents.CUSTOM_DATA, legacy, tag -> tag.remove(GearAffixes.VALUES_KEY));
            close(GearAffixes.net(legacy, GearAffix.Kind.ATTACK), 0.10, "Pre-0.6 gear must use its original default strength");
        } finally { WildFortuneConfig.install(previous); }
        test.succeed();
    }

    @GameTest
    public void reloadCommandRequiresPermissionAndHandlesBadFile(GameTestHelper test) throws Exception {
        var previous = WildFortuneConfig.get();
        var path = WildFortuneConfig.path();
        String original = Files.readString(path);
        try {
            var dispatcher = test.getLevel().getServer().getCommands().getDispatcher();
            var source = test.getLevel().getServer().createCommandSourceStack();
            var node = dispatcher.getRoot().getChild("wildfortune");
            require(node != null && !node.getChild("reload").canUse(source.withPermission(PermissionSet.NO_PERMISSIONS)), "Anyone can reload server settings");
            Files.writeString(path, "{\"gear\":{\"effects\":{\"sharp\":{\"chance\":1}}}}");
            require(dispatcher.execute("wildfortune reload", source) == 1, "Reload command failed");
            require(WildFortuneConfig.get().gear().get(GearAffix.SHARP).chance() == 1, "Reload command did not install file");
            var valid = WildFortuneConfig.get();
            Files.writeString(path, "{broken");
            require(dispatcher.execute("wildfortune reload", source) == 0, "Invalid reload incorrectly succeeded");
            require(WildFortuneConfig.get().equals(valid), "Bad command reload changed settings");
        } finally {
            Files.writeString(path, original);
            WildFortuneConfig.install(previous);
        }
        test.succeed();
    }

    private static void close(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 0.000001, message + ": " + actual + " expected " + expected);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
