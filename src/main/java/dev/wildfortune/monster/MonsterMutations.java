package dev.wildfortune.monster;

import java.util.List;
import dev.wildfortune.config.WildFortuneConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import dev.wildfortune.gear.GearAffixes;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.Component;

public final class MonsterMutations {
    public static final String NATURAL_TAG = "wildfortune:natural_spawn";
    public static final String ROLLED_TAG = "wildfortune:mutations_rolled_v1";
    public static final String NAMED_TAG = "wildfortune:mutation_name_v1";
    public static final String HEALTH_MIGRATED_TAG = "wildfortune:health_mutation_v1";
    public static final Identifier HEALTH_MODIFIER = Identifier.fromNamespaceAndPath("wildfortune", "monster_health");
    public static final float CHANCE_PER_EFFECT = (float) WildFortuneConfig.DEFAULT_MONSTER_CHANCE;
    public static final List<Holder<MobEffect>> EFFECTS = List.of(
            MobEffects.SPEED, MobEffects.RESISTANCE, MobEffects.STRENGTH);

    private MonsterMutations() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register(MonsterMutations::onEntityLoad);
    }

    // Keep the historical tag ID so saved natural monsters remain compatible.
    // LOAD is excluded: previously generated structures are not retroactively rerolled.
    public static void recordSpawnReason(Entity entity, EntitySpawnReason reason) {
        if ((reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.STRUCTURE)
                && entity instanceof Mob mob
                && mob.level() instanceof ServerLevel
                && WildFortuneConfig.get().monstersEnabled()
                && isEligible(mob)) {
            mob.addTag(NATURAL_TAG);
        }
    }

    public static boolean isEligible(Mob mob) {
        return mob.getType().getCategory() == MobCategory.MONSTER
                && !WildFortuneConfig.get().excludedEntities().contains(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
    }

    // ENTITY_LOAD runs after spawn initialization, and also on chunk loads.
    // Both tags and vanilla active effects are persisted by Minecraft itself.
    public static void onEntityLoad(Entity entity, ServerLevel level) {
        if (!(entity instanceof Mob mob) || !isEligible(mob)
                || !mob.entityTags().contains(NATURAL_TAG)) {
            return;
        }

        if (mob.entityTags().contains(ROLLED_TAG)) {
            if (!mob.entityTags().contains(HEALTH_MIGRATED_TAG)) {
                if (mob.hasEffect(MobEffects.INVISIBILITY)) {
                    mob.removeEffect(MobEffects.INVISIBILITY);
                    addHealth(mob, WildFortuneConfig.get().monsters().get("health").amount(), false);
                }
                mob.addTag(HEALTH_MIGRATED_TAG);
            }
            assignMutationName(mob);
            return;
        }

        // Mark even an all-failed roll, so effect-free monsters cannot reroll.
        mob.addTag(ROLLED_TAG);
        mob.addTag(HEALTH_MIGRATED_TAG);
        var settings = WildFortuneConfig.get();
        if (!settings.monstersEnabled()) return;
        for (int i = 0; i < WildFortuneConfig.MONSTER_EFFECT_IDS.size(); i++) {
            var rule = settings.monsters().get(WildFortuneConfig.MONSTER_EFFECT_IDS.get(i));
            if (rule.enabled() && mob.getRandom().nextFloat() < rule.chance()) {
                if (i == 3) addHealth(mob, rule.amount(), true);
                else mob.addEffect(new MobEffectInstance(
                        EFFECTS.get(i), MobEffectInstance.INFINITE_DURATION, rule.level() - 1, false, true, true));
            }
        }
        assignMutationName(mob);
    }

    private static void assignMutationName(Mob mob) {
        if (mob.entityTags().contains(NAMED_TAG)) {
            Component old = mob.getCustomName();
            Component redesigned = redesignName(old);
            if (redesigned != old) mob.setCustomName(redesigned);
            return;
        }
        mob.addTag(NAMED_TAG);
        // Keep player/other-mod names and name-tag persistence rules intact.
        if (mob.hasCustomName()) return;
        int count = mutationCount(mob);
        if (count == 0) return;
        mob.setCustomName(Component.translatable("entity.wildfortune.rank." + count,
                mob.getType().getDescription()).withStyle(rankStyle(count)));
        mob.setCustomNameVisible(true);
    }

    public static boolean hasHealthMutation(Mob mob) {
        var attribute = mob.getAttribute(Attributes.MAX_HEALTH);
        return attribute != null && attribute.getModifier(HEALTH_MODIFIER) != null;
    }

    public static int mutationCount(Mob mob) {
        return (int) EFFECTS.stream().filter(mob::hasEffect).count() + (hasHealthMutation(mob) ? 1 : 0);
    }

    private static void addHealth(Mob mob, double amount, boolean freshSpawn) {
        var attribute = mob.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null || hasHealthMutation(mob)) return;
        float ratio = mob.getHealth() / mob.getMaxHealth();
        attribute.addPermanentModifier(new AttributeModifier(HEALTH_MODIFIER, amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        mob.setHealth(mob.getMaxHealth() * (freshSpawn ? 1 : ratio));
    }

    public static Style rankStyle(int count) {
        return switch (count) {
            case 1 -> GearAffixes.nameStyle(1, 0);
            case 2 -> GearAffixes.nameStyle(3, 0);
            case 3 -> GearAffixes.nameStyle(4, 0);
            case 4 -> GearAffixes.nameStyle(5, 0);
            default -> Style.EMPTY;
        };
    }

    /** Upgrade only our translated rank labels; preserve the saved rank and player-authored names. */
    public static Component redesignName(Component name) {
        if (name == null || !(name.getContents() instanceof TranslatableContents text)) return name;
        for (int rank = 1; rank <= 4; rank++) {
            if (!text.getKey().equals("entity.wildfortune.rank." + rank)) continue;
            Style style = rankStyle(rank);
            return style.equals(name.getStyle()) ? name : name.copy().setStyle(style);
        }
        return name;
    }
}
