package dev.wildfortune;

import dev.wildfortune.monster.MonsterMutations;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class MonsterMutationsGameTest {
    @GameTest
    public void legacyInvisibilityBecomesHealthOnceAndSurvivesSave(GameTestHelper test) {
        Zombie mob = EntityTypes.ZOMBIE.create(test.getLevel(), EntitySpawnReason.LOAD);
        require(mob != null, "No zombie");
        mob.addTag(MonsterMutations.NATURAL_TAG);
        mob.addTag(MonsterMutations.ROLLED_TAG);
        mob.addTag(MonsterMutations.NAMED_TAG);
        mob.setCustomName(Component.translatable("entity.wildfortune.rank.1", mob.getType().getDescription())
                .withStyle(MonsterMutations.rankStyle(1)));
        mob.setCustomNameVisible(true);
        mob.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, -1));
        mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(40);
        mob.setHealth(20);
        for (int i = 0; i < 10; i++) MonsterMutations.onEntityLoad(mob, test.getLevel());
        require(!mob.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY), "Legacy invisibility remains");
        require(mob.getMaxHealth() == 50 && mob.getHealth() == 25, "Migration must scale original health by 25% and preserve damage ratio");
        assertMutationName(mob, 1);
        mob.setHealth(13);
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, test.getLevel().registryAccess());
        mob.saveWithoutId(output);
        mob.discard();
        Zombie restored = EntityTypes.ZOMBIE.create(test.getLevel(), EntitySpawnReason.LOAD);
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), output.buildResult()));
        for (int i = 0; i < 10; i++) MonsterMutations.onEntityLoad(restored, test.getLevel());
        require(restored.getMaxHealth() == 50 && restored.getHealth() == 13, "Reload stacked bonus or healed damage");
        require(!restored.isPersistenceRequired(), "Health migration prevents despawning");
        restored.discard();
        test.succeed();
    }
    @GameTest(maxTicks = 200)
    public void independentRollsAndStacking(GameTestHelper test) {
        int samples = 4096;
        int[] counts = new int[4];
        Set<Integer> combinations = new HashSet<>();
        RandomSource seeds = RandomSource.create(1234567L);
        for (int i = 0; i < samples; i++) {
            Zombie mob = spawnZombie(test, EntitySpawnReason.NATURAL, seeds.nextLong());
            require(mob.entityTags().contains(MonsterMutations.ROLLED_TAG), "Natural monster was not processed");
            int mask = 0;
            for (int effect = 0; effect < 3; effect++) {
                MobEffectInstance instance = mob.getEffect(MonsterMutations.EFFECTS.get(effect));
                if (instance != null) {
                    counts[effect]++;
                    mask |= 1 << effect;
                    require(instance.getAmplifier() == 0 && instance.isInfiniteDuration(), "Wrong effect level/duration");
                }
            }
            if (MonsterMutations.hasHealthMutation(mob)) {
                counts[3]++; mask |= 8;
                require(Math.abs(mob.getMaxHealth() - 25) < 0.001 && mob.getHealth() == mob.getMaxHealth(), "Health mutation must spawn with 25/25 HP");
            }
            require(!mob.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY), "New mutation adds invisibility");
            combinations.add(mask);
            assertMutationName(mob, Integer.bitCount(mask));
            require(!mob.isPersistenceRequired(), "Automatic mutation name must not prevent natural despawning");
            mob.discard();
        }
        for (int count : counts) {
            require(Math.abs(count - samples * 0.35) < 150, "Effect frequency outside expected range: " + count);
        }
        require(combinations.size() == 16, "Not all 16 combinations were observed: " + combinations);
        WildFortune.LOGGER.info("Mutation sample: {} natural zombies; counts={} / {} / {} / {}; all 16 combinations observed",
                samples, counts[0], counts[1], counts[2], counts[3]);
        test.succeed();
    }

    @GameTest(maxTicks = 200)
    public void creepersGetFourIndependentStackingHealthMutations(GameTestHelper test) {
        int samples = 1024;
        int[] counts = new int[4];
        Set<Integer> combinations = new HashSet<>();
        RandomSource seeds = RandomSource.create(7654321L);
        Creeper fullMutation = null;
        for (int sample = 0; sample < samples; sample++) {
            Creeper creeper = spawnCreeper(test, seeds.nextLong());
            require(creeper.entityTags().contains(MonsterMutations.CREEPER_HEALTH_TAG), "Creeper health roll marker missing");
            require(creeper.getActiveEffects().isEmpty(), "Creeper received a random potion effect");
            int mask = 0;
            double bonus = 0;
            for (int i = 0; i < counts.length; i++) {
                if (MonsterMutations.hasCreeperHealthMutation(creeper, i)) {
                    counts[i]++;
                    mask |= 1 << i;
                    bonus += MonsterMutations.CREEPER_HEALTH_AMOUNTS.get(i);
                }
            }
            require(Math.abs(creeper.getMaxHealth() - 20 * (1 + bonus)) < 0.001, "Creeper health modifiers did not add together");
            require(MonsterMutations.mutationCount(creeper) == Integer.bitCount(mask), "Creeper rank counted wrong mutations");
            if (mask != 0) assertMutationName(creeper, Integer.bitCount(mask));
            else require(!creeper.hasCustomName(), "Effect-free creeper received a mutation name");
            combinations.add(mask);
            if (mask == 15 && fullMutation == null) fullMutation = creeper;
            else creeper.discard();
        }
        for (int count : counts) {
            require(Math.abs(count - samples * 0.35) < 60, "Creeper health tier frequency outside expected range: " + count);
        }
        require(combinations.size() == 16, "Not all 16 creeper health combinations were observed: " + combinations);
        require(fullMutation != null && Math.abs(fullMutation.getMaxHealth() - 30) < 0.001,
                "Four creeper health mutations must total +50% maximum health");

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, test.getLevel().registryAccess());
        fullMutation.saveWithoutId(output);
        fullMutation.discard();
        Creeper restored = (Creeper) EntityTypes.CREEPER.create(test.getLevel(), EntitySpawnReason.LOAD);
        require(restored != null, "Could not restore creeper");
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), output.buildResult()));
        for (int i = 0; i < 10; i++) MonsterMutations.onEntityLoad(restored, test.getLevel());
        require(Math.abs(restored.getMaxHealth() - 30) < 0.001 && restored.getHealth() == 30,
                "Saved creeper health mutations changed or were reapplied");
        require(MonsterMutations.mutationCount(restored) == 4 && restored.getActiveEffects().isEmpty(),
                "Saved creeper mutations or potion effects changed");
        restored.discard();
        WildFortune.LOGGER.info("Creeper mutation sample: {} creepers; tiers={} / {} / {} / {}; all 16 combinations observed",
                samples, counts[0], counts[1], counts[2], counts[3]);
        test.succeed();
    }

    @GameTest
    public void nonNaturalAndNonStructureReasonsAreExcluded(GameTestHelper test) {
        for (EntitySpawnReason reason : EntitySpawnReason.values()) {
            if (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.STRUCTURE) continue;
            Zombie mob = spawnZombie(test, reason, 0);
            require(!mob.entityTags().contains(MonsterMutations.NATURAL_TAG), "Incorrect natural tag: " + reason);
            require(!mob.entityTags().contains(MonsterMutations.ROLLED_TAG), "Incorrect roll: " + reason);
            require(mob.getActiveEffects().isEmpty(), "Effect added to excluded spawn reason: " + reason);
            mob.discard();
        }
        test.succeed();
    }

    @GameTest
    public void animalsVillagersAndBossesAreExcluded(GameTestHelper test) {
        for (EntityType<?> type : new EntityType<?>[] {
                EntityTypes.COW, EntityTypes.VILLAGER, EntityTypes.WITHER, EntityTypes.ENDER_DRAGON}) {
            Mob mob = (Mob) type.create(test.getLevel(), EntitySpawnReason.NATURAL);
            require(mob != null, "Could not create excluded entity");
            require(!mob.entityTags().contains(MonsterMutations.NATURAL_TAG), "Excluded mob marked natural: " + type);
            MonsterMutations.onEntityLoad(mob, test.getLevel());
            require(!mob.entityTags().contains(MonsterMutations.ROLLED_TAG), "Excluded mob rolled: " + type);
            mob.discard();
        }
        test.succeed();
    }

    @GameTest
    public void effectsAndEmptyRollSurviveSaveLoad(GameTestHelper test) {
        for (int targetCount : new int[] {0, 4}) {
            Zombie original = findZombieWithEffects(test, targetCount);
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, test.getLevel().registryAccess());
            original.saveWithoutId(output);
            original.discard();

            Zombie restored = EntityTypes.ZOMBIE.create(test.getLevel(), EntitySpawnReason.LOAD);
            require(restored != null, "Could not restore zombie");
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), output.buildResult()));
            require(test.getLevel().addFreshEntity(restored), "Could not add restored zombie");
            require(restored.entityTags().contains(MonsterMutations.ROLLED_TAG), "Saved roll marker lost");
            require(MonsterMutations.mutationCount(restored) == targetCount, "Saved effects changed or empty roll rerolled");
            require(restored.getHealth() == restored.getMaxHealth(), "Save/load lost extra current health");
            assertMutationName(restored, targetCount);
            for (MobEffectInstance effect : restored.getActiveEffects()) {
                require(effect.getAmplifier() == 0 && effect.isInfiniteDuration(), "Saved effect properties changed");
            }
            restored.discard();
        }
        test.succeed();
    }

    @GameTest
    public void duplicateLoadsNeverReapplyRemovedEffects(GameTestHelper test) {
        Zombie mob = findZombieWithEffects(test, 4);
        Component name = mob.getCustomName();
        mob.removeAllEffects();
        for (int i = 0; i < 100; i++) MonsterMutations.onEntityLoad(mob, test.getLevel());
        require(mob.getActiveEffects().isEmpty(), "Removed effects were reapplied on repeated loads");
        require(name.equals(mob.getCustomName()), "Spawn rank name changed on duplicate load");
        mob.discard();
        test.succeed();
    }

    @GameTest
    public void oldMonstersGainNamesWithoutRerolling(GameTestHelper test) {
        Zombie mob = findZombieWithEffects(test, 4);
        mob.entityTags().remove(MonsterMutations.NAMED_TAG);
        mob.setCustomName(null);
        mob.setCustomNameVisible(false);
        MonsterMutations.onEntityLoad(mob, test.getLevel());
        assertMutationName(mob, 4);
        require(MonsterMutations.mutationCount(mob) == 4, "Migration changed effects");
        mob.discard();
        test.succeed();
    }

    @GameTest
    public void existingCustomNamesArePreserved(GameTestHelper test) {
        Zombie mob = EntityTypes.ZOMBIE.create(test.getLevel(), EntitySpawnReason.NATURAL);
        require(mob != null, "Could not create named zombie");
        Component originalName = Component.literal("Player's companion");
        mob.setCustomName(originalName);
        mob.setCustomNameVisible(false);
        mob.setPersistenceRequired();
        MonsterMutations.onEntityLoad(mob, test.getLevel());
        require(originalName.equals(mob.getCustomName()), "Existing name overwritten");
        require(!mob.isCustomNameVisible() && mob.isPersistenceRequired(), "Player name settings changed");
        mob.discard();
        test.succeed();
    }

    @GameTest
    public void existingRanksRestyleWithoutRerollingOrChangingRank(GameTestHelper test) {
        Zombie mob = findZombieWithEffects(test, 4);
        mob.setCustomName(Component.translatable("entity.wildfortune.rank.2", mob.getType().getDescription())
                .withStyle(net.minecraft.ChatFormatting.GOLD).withStyle(net.minecraft.ChatFormatting.UNDERLINE));
        mob.removeAllEffects();
        MonsterMutations.onEntityLoad(mob, test.getLevel());
        require(((TranslatableContents) mob.getCustomName().getContents()).getKey().equals("entity.wildfortune.rank.2"), "Migration changed saved rank");
        require(mob.getCustomName().getStyle().equals(MonsterMutations.rankStyle(2)), "Old monster style not migrated");
        require(!mob.getCustomName().getStyle().isUnderlined() && mob.getActiveEffects().isEmpty(), "Migration added effects or kept underline");
        require(!mob.isPersistenceRequired(), "Style migration changed despawn rules");
        Component current = mob.getCustomName();
        MonsterMutations.onEntityLoad(mob, test.getLevel());
        require(current == mob.getCustomName(), "Style migration is not idempotent");
        Component playerName = Component.literal("Companion").withStyle(net.minecraft.ChatFormatting.AQUA);
        require(MonsterMutations.redesignName(playerName) == playerName, "Client fallback restyled a player-authored name");
        mob.discard();
        test.succeed();
    }

    private static void assertMutationName(Mob mob, int count) {
        if (count == 0) {
            require(!mob.hasCustomName(), "Effect-free mob received a mutation name");
            return;
        }
        Component name = mob.getCustomName();
        require(name != null && name.getContents() instanceof TranslatableContents, "Missing mutation name");
        require(((TranslatableContents) name.getContents()).getKey().equals("entity.wildfortune.rank." + count),
                "Name does not match effect count");
        require(mob.isCustomNameVisible(), "Mutation name is not always visible");
        require(name.getStyle().equals(MonsterMutations.rankStyle(count)), "Monster rank style inconsistent with gear");
        require(!name.getStyle().isUnderlined(), "Monster name has underline");
    }

    private static Zombie spawnZombie(GameTestHelper test, EntitySpawnReason reason, long seed) {
        ServerLevel level = test.getLevel();
        Zombie mob = EntityTypes.ZOMBIE.create(level, reason);
        require(mob != null, "Could not create zombie");
        mob.setPos(Vec3.atBottomCenterOf(test.absolutePos(new BlockPos(1, 2, 1))));
        mob.getRandom().setSeed(seed);
        require(level.addFreshEntity(mob), "Could not add zombie");
        return mob;
    }

    private static Creeper spawnCreeper(GameTestHelper test, long seed) {
        ServerLevel level = test.getLevel();
        Creeper creeper = (Creeper) EntityTypes.CREEPER.create(level, EntitySpawnReason.NATURAL);
        require(creeper != null, "Could not create creeper");
        creeper.setPos(Vec3.atBottomCenterOf(test.absolutePos(new BlockPos(1, 2, 1))));
        creeper.getRandom().setSeed(seed);
        require(level.addFreshEntity(creeper), "Could not add creeper");
        return creeper;
    }

    private static Zombie findZombieWithEffects(GameTestHelper test, int count) {
        RandomSource seeds = RandomSource.create(987654321L);
        for (int i = 0; i < 4096; i++) {
            Zombie mob = spawnZombie(test, EntitySpawnReason.NATURAL, seeds.nextLong());
            if (MonsterMutations.mutationCount(mob) == count) return mob;
            mob.discard();
        }
        throw new AssertionError("No zombie found with " + count + " effects");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
