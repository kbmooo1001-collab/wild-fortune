package dev.wildfortune.config;

import dev.wildfortune.gear.GearActions;
import dev.wildfortune.gear.GearAffix;
import dev.wildfortune.gear.GearAffixes;
import dev.wildfortune.gear.GearFireworkData;
import dev.wildfortune.mixin.GearArrowAccess;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ExpandedGearGameTest {
    @GameTest
    public void everyDamageableItemHasFiveUsableKinds(GameTestHelper test) {
        int checked = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            var stack = new ItemStack(item);
            if (!GearAffixes.isGear(stack)) continue;
            var allowed = Arrays.stream(GearAffix.values()).filter(e -> GearAffixes.supports(stack, e)).toList();
            require(allowed.size() == 10 && allowed.stream().map(e -> e.kind).distinct().count() == 5,
                    "Item does not have exactly five positive/negative pairs: " + item);
            GearAffixes.applyAll(stack, allowed.stream().filter(e -> e.positive).toList());
            require(GearAffixes.getAll(stack).size() == 5 && GearAffixes.nameKey(GearAffixes.getAll(stack)).equals("item.wildfortune.combo.5_0"),
                    "Item cannot reach legendary: " + item);
            checked++;
        }
        require(checked > 50, "Registry coverage too small");
        test.succeed();
    }

    @GameTest
    public void maximumHealthNeverHealsOnEquipmentSwap(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel();
        player.setItemSlot(EquipmentSlot.CHEST, gear(Items.IRON_CHESTPLATE, GearAffix.VITAL));
        player.doTick();
        close(player.getMaxHealth(), 21, "Vital armor max health");
        close(player.getHealth(), 20, "Equipping vital armor healed player");
        player.setHealth(10);
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        player.doTick();
        close(player.getMaxHealth(), 20, "Health bonus remained after unequipping");
        close(player.getHealth(), 10, "Removing armor healed player");
        player.setItemSlot(EquipmentSlot.CHEST, gear(Items.IRON_CHESTPLATE, GearAffix.FRAIL));
        player.doTick();
        close(player.getMaxHealth(), 19, "Negative health effect ignored");
        player.setItemSlot(EquipmentSlot.HEAD, gear(Items.IRON_HELMET, GearAffix.VITAL));
        player.setItemSlot(EquipmentSlot.OFFHAND, gear(Items.SHIELD, GearAffix.VITAL));
        player.setItemSlot(EquipmentSlot.MAINHAND, gear(Items.FISHING_ROD, GearAffix.VITAL));
        player.doTick();
        close(player.getMaxHealth(), 22, "Health bonuses do not stack by equipped slot");
        player.setHealth(22);
        for (EquipmentSlot slot : EquipmentSlot.values()) player.setItemSlot(slot, ItemStack.EMPTY);
        player.doTick();
        require(player.getHealth() <= 20, "Health remained above max after removing gear");
        test.succeed();
    }

    @GameTest
    public void elytraChangesActualHorizontalTravelWithoutCompounding(GameTestHelper test) {
        double baseline = flight(test, null);
        close(flight(test, GearAffix.SOARING), baseline * 1.05, "Positive flight displacement");
        close(flight(test, GearAffix.DRAGGING), baseline * 0.95, "Negative flight displacement");
        test.succeed();
    }

    private static double flight(GameTestHelper test, GearAffix affix) {
        var player = test.makeMockServerPlayerInLevel();
        var chest = affix == null ? new ItemStack(Items.ELYTRA) : gear(Items.ELYTRA, affix);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        player.setPos(Vec3.atBottomCenterOf(test.absolutePos(new BlockPos(1, 20, 1))));
        player.setOnGround(false);
        player.setXRot(0);
        player.setYRot(-90);
        player.setDeltaMovement(1, -0.1, 0);
        player.startFallFlying();
        double start = player.getX();
        player.travel(Vec3.ZERO);
        double displacement = player.getX() - start;
        require(displacement > 0, "Flight did not move player");
        double stored = player.getDeltaMovement().x;
        var vanilla = GearActions.flightMovement(player, new Vec3(stored, 0, 0));
        close(vanilla.x, stored * (affix == GearAffix.SOARING ? 1.05 : affix == GearAffix.DRAGGING ? 0.95 : 1), "Flight factor compounded");
        player.stopFallFlying();
        return displacement;
    }

    @GameTest
    public void shieldChangesActualDisableCooldown(GameTestHelper test) {
        for (var affix : List.of(GearAffix.RESILIENT, GearAffix.LINGERING)) {
            var player = test.makeMockServerPlayerInLevel();
            var shield = gear(Items.SHIELD, affix);
            player.setItemSlot(EquipmentSlot.OFFHAND, shield);
            shield.get(DataComponents.BLOCKS_ATTACKS).disable(test.getLevel(), player, 5, shield);
            int ticks = 0;
            while (player.getCooldowns().isOnCooldown(shield) && ticks < 200) {
                player.getCooldowns().tick();
                ticks++;
            }
            require(ticks == (affix == GearAffix.RESILIENT ? 90 : 110), "Shield disable duration incorrect: " + ticks);
        }
        test.succeed();
    }

    @GameTest
    public void bowAndCrossbowPrepareAtModifiedSpeed(GameTestHelper test) {
        require(bowCrit(test, GearAffix.READY, 18), "Positive bow not fully drawn in 18 ticks");
        require(!bowCrit(test, GearAffix.HESITANT, 20), "Negative bow incorrectly full in 20 ticks");
        require(bowCrit(test, GearAffix.HESITANT, 22), "Negative bow not fully drawn in 22 ticks");
        var player = test.makeMockServerPlayerInLevel();
        int base = CrossbowItem.getChargeDuration(new ItemStack(Items.CROSSBOW), player);
        require(CrossbowItem.getChargeDuration(gear(Items.CROSSBOW, GearAffix.READY), player) == (int) Math.round(base * 0.9), "Crossbow ready duration");
        require(CrossbowItem.getChargeDuration(gear(Items.CROSSBOW, GearAffix.HESITANT), player) == (int) Math.round(base * 1.1), "Crossbow hesitant duration");
        test.succeed();
    }

    private static boolean bowCrit(GameTestHelper test, GearAffix effect, int ticks) {
        var player = test.makeMockServerPlayerInLevel();
        player.setPos(Vec3.atBottomCenterOf(test.absolutePos(new BlockPos(1, 2, 1))));
        var bow = gear(Items.BOW, effect);
        player.setItemSlot(EquipmentSlot.MAINHAND, bow);
        player.getInventory().setItem(10, new ItemStack(Items.ARROW, 16));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        require(((BowItem) Items.BOW).releaseUsing(bow, test.getLevel(), player, bow.getUseDuration(player) - ticks), "Bow shot failed");
        var arrows = test.getLevel().getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(8), a -> a.getOwner() == player);
        require(arrows.size() == 1, "Bow did not spawn one arrow");
        boolean critical = arrows.getFirst().isCritArrow();
        arrows.getFirst().discard();
        return critical;
    }

    @GameTest
    public void projectilesUseWeaponDamageAndVelocityAndKeepSavedDamage(GameTestHelper test) {
        for (boolean positive : new boolean[] {true, false}) {
            var player = test.makeMockServerPlayerInLevel();
            player.setPos(Vec3.atBottomCenterOf(test.absolutePos(new BlockPos(1, 2, 1))));
            var crossbow = gear(Items.CROSSBOW, positive ? GearAffix.DEADLY : GearAffix.FEEBLE,
                    positive ? GearAffix.PIERCING : GearAffix.FLOATING);
            player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
            crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
            ((CrossbowItem) Items.CROSSBOW).performShooting(test.getLevel(), player, InteractionHand.MAIN_HAND, crossbow, 3, 0, null);
            var arrows = test.getLevel().getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(8), a -> a.getOwner() == player);
            require(arrows.size() == 1, "Crossbow did not spawn arrow");
            var arrow = arrows.getFirst();
            close(((GearArrowAccess) arrow).wildfortune$getBaseDamage(), positive ? 2.2 : 1.8, "Arrow damage multiplier");
            close(arrow.getDeltaMovement().length(), positive ? 3.15 : 2.85, "Actual arrow speed");
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            close(((GearArrowAccess) arrow).wildfortune$getBaseDamage(), positive ? 2.2 : 1.8, "Weapon swap changed in-flight damage");
            arrow.discard();
            var rocket = new FireworkRocketEntity(test.getLevel(), 0, 4, 0, new ItemStack(Items.FIREWORK_ROCKET));
            GearActions.prepareProjectile(rocket, crossbow);
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, test.getLevel().registryAccess());
            rocket.saveWithoutId(output);
            var loaded = EntityTypes.FIREWORK_ROCKET.create(test.getLevel(), EntitySpawnReason.LOAD);
            require(loaded != null, "No restored firework");
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), output.buildResult()));
            close(((GearFireworkData) loaded).wildfortune$getDamageBonus(), positive ? 0.1 : -0.1, "Firework bonus lost on save/load");
            rocket.discard();
            loaded.discard();
        }
        test.succeed();
    }

    @GameTest
    public void miningAndUtilityExperiencePreserveSmallFractions(GameTestHelper test) {
        int base = mineExperience(test, null);
        require(base > 0, "Mining generated no baseline experience");
        close(mineExperience(test, GearAffix.LEARNED), Math.floor(base * 1.1 + 0.00001), "Positive mining XP");
        close(mineExperience(test, GearAffix.FORGETFUL), Math.floor(base * 0.9 + 0.00001), "Negative mining XP");
        for (var affix : List.of(GearAffix.LEARNED, GearAffix.FORGETFUL)) {
            var player = test.makeMockServerPlayerInLevel();
            player.setItemSlot(EquipmentSlot.MAINHAND, gear(Items.FISHING_ROD, affix));
            for (int i = 0; i < 10; i++) player.giveExperiencePoints(1);
            require(player.totalExperience == (affix == GearAffix.LEARNED ? 11 : 9), "Small utility XP fractions lost");
        }
        test.succeed();
    }

    private static int mineExperience(GameTestHelper test, GearAffix effect) {
        var player = test.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.MAINHAND, effect == null ? new ItemStack(Items.IRON_PICKAXE) : gear(Items.IRON_PICKAXE, effect));
        var pos = test.absolutePos(new BlockPos(2, 2, 2));
        int experience = 0;
        var rules = test.getLevel().getGameRules();
        boolean drops = rules.get(net.minecraft.world.level.gamerules.GameRules.BLOCK_DROPS);
        rules.set(net.minecraft.world.level.gamerules.GameRules.BLOCK_DROPS, true, test.getLevel().getServer());
        try {
        for (int i = 0; i < 10; i++) {
            test.getLevel().setBlockAndUpdate(pos, Blocks.SCULK.defaultBlockState());
            require(player.gameMode.destroyBlock(pos), "Could not mine sculk");
            var orbs = test.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(2));
            for (var orb : orbs) { experience += orb.getValue(); orb.discard(); }
        }
        } finally { rules.set(net.minecraft.world.level.gamerules.GameRules.BLOCK_DROPS, drops, test.getLevel().getServer()); }
        return experience;
    }

    @GameTest
    public void strongerNamesHaveDistinctStyles(GameTestHelper test) {
        require(GearAffixes.nameStyle(3, 3).isBold() && GearAffixes.nameStyle(3, 3).getColor().getValue() == 0x55FFFF, "Three positives not highlighted");
        require(GearAffixes.nameStyle(4, 4).isBold() && !GearAffixes.nameStyle(4, 4).isUnderlined(), "Four positives must be bold without underlining");
        require(GearAffixes.nameStyle(3, 0).isBold() && GearAffixes.nameStyle(3, 0).getColor().getValue() == 0xFF8844, "Three negatives not highlighted");
        require(GearAffixes.nameStyle(4, 0).isBold() && !GearAffixes.nameStyle(4, 0).isUnderlined(), "Four negatives must be bold without underlining");
        require(GearAffixes.nameStyle(5, 5).equals(dev.wildfortune.gear.PrismaticNames.style(true)), "Legendary prism style missing");
        require(GearAffixes.nameStyle(5, 0).equals(dev.wildfortune.gear.PrismaticNames.style(false)), "Calamitous prism style missing");
        test.succeed();
    }

    @GameTest
    public void oldConfigIsBackedUpAndExpandedWithoutResettingEdits(GameTestHelper test) throws Exception {
        var previous = WildFortuneConfig.get();
        try {
            var dir = Files.createTempDirectory(WildFortuneConfig.path().toAbsolutePath().getParent(), "wf-schema-test-");
            var path = dir.resolve("wildfortune.json");
            String original = "{\"schemaVersion\":1,\"gear\":{\"effects\":{\"sharp\":{\"chance\":0.42}}}}";
            Files.writeString(path, original);
            require(WildFortuneConfig.load(path), "Old config did not migrate");
            require(Files.readString(dir.resolve("wildfortune.json.v1.bak")).equals(original), "Original config backup differs");
            var parsed = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            require(parsed.get("schemaVersion").getAsInt() == 3 && parsed.getAsJsonObject("gear").getAsJsonObject("effects").size() == 30,
                    "Migrated config missing effects");
            close(WildFortuneConfig.get().gear().get(GearAffix.SHARP).chance(), 0.42, "Migration reset existing chance");
        } finally { WildFortuneConfig.install(previous); }
        test.succeed();
    }

    private static ItemStack gear(Item item, GearAffix... effects) {
        var result = new ItemStack(item);
        GearAffixes.applyAll(result, List.of(effects));
        return result;
    }
    private static void close(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 0.00001, message + ": " + actual + " expected " + expected);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
