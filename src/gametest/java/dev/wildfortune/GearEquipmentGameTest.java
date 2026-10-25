package dev.wildfortune;

import dev.wildfortune.gear.GearAffix;
import dev.wildfortune.gear.GearAffixes;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Blocks;

public final class GearEquipmentGameTest {
    @GameTest
    public void hotbarSwitchHandSwapAndRemovalUpdateRealPlayer(GameTestHelper test) {
        ServerPlayer player = test.makeMockServerPlayerInLevel();
        ItemStack positive = gear(Items.IRON_SWORD, GearAffix.SHARP, GearAffix.SWIFT, GearAffix.LONG, GearAffix.LIGHT);
        ItemStack negative = gear(Items.IRON_SWORD, GearAffix.BLUNT, GearAffix.HEAVY, GearAffix.SHORT, GearAffix.BURDENSOME);
        player.getInventory().setItem(0, positive);
        player.getInventory().setItem(1, negative);
        double speed = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        for (int repeat = 0; repeat < 6; repeat++) {
            player.getInventory().setSelectedSlot(0);
            update(player);
            stats(player, 6.6, 1.76, 3.5, speed * 1.05);
            player.getInventory().setSelectedSlot(1);
            update(player);
            stats(player, 5.4, 1.44, 2.5, speed * 0.95);
        }
        player.getInventory().setItem(1, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.OFFHAND, positive);
        update(player);
        stats(player, 1, 4, 3, speed);
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        update(player);
        stats(player, 1, 4, 3, speed);
        test.succeed();
    }

    @GameTest
    public void fullEquipmentStacksPerSlotAndCompletelyUnequips(GameTestHelper test) {
        ServerPlayer player = test.makeMockServerPlayerInLevel();
        double speed = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        Item[] armor = {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < armor.length; i++) {
            player.setItemSlot(slots[i], gear(armor[i], GearAffix.STURDY, GearAffix.LIGHT, GearAffix.STEADFAST));
        }
        player.setItemSlot(EquipmentSlot.MAINHAND, gear(Items.IRON_SWORD, GearAffix.LIGHT));
        player.setItemSlot(EquipmentSlot.OFFHAND, gear(Items.SHIELD, GearAffix.LIGHT, GearAffix.STEADFAST));
        update(player);
        close(player.getAttributeValue(Attributes.MOVEMENT_SPEED), speed * 1.30, "Six equipped movement bonuses");
        close(player.getAttributeValue(Attributes.ARMOR), 16.5, "Four armor bonuses");
        knockback(player, 0.5);
        player.setItemSlot(EquipmentSlot.CHEST, gear(Items.IRON_CHESTPLATE, GearAffix.FLIMSY, GearAffix.BURDENSOME, GearAffix.UNSTABLE));
        update(player);
        close(player.getAttributeValue(Attributes.MOVEMENT_SPEED), speed * 1.20, "Positive to negative armor replacement");
        close(player.getAttributeValue(Attributes.ARMOR), 15.3, "Armor replacement retained old modifier");
        knockback(player, 0.7);
        for (EquipmentSlot slot : EquipmentSlot.values()) player.setItemSlot(slot, ItemStack.EMPTY);
        update(player);
        close(player.getAttributeValue(Attributes.MOVEMENT_SPEED), speed, "Movement remained after unequipping");
        close(player.getAttributeValue(Attributes.ARMOR), 0, "Armor remained after unequipping");
        knockback(player, 1);
        test.succeed();
    }

    @GameTest
    public void toolsBowsAndShieldOnlyWorkInIntendedSlots(GameTestHelper test) {
        ServerPlayer player = test.makeMockServerPlayerInLevel();
        player.setOnGround(true);
        ItemStack plain = new ItemStack(Items.IRON_PICKAXE);
        player.setItemSlot(EquipmentSlot.MAINHAND, plain);
        update(player);
        player.setOnGround(true);
        float baseMining = player.getDestroySpeed(Blocks.STONE.defaultBlockState());
        player.setItemSlot(EquipmentSlot.MAINHAND, gear(Items.IRON_PICKAXE, GearAffix.EFFICIENT, GearAffix.LONG));
        update(player);
        player.setOnGround(true);
        close(player.getDestroySpeed(Blocks.STONE.defaultBlockState()), baseMining * 1.10, "Equipped efficient pickaxe");
        close(player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), 5, "Tool work reach");
        close(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), 3, "Tool changed melee reach");
        player.setItemSlot(EquipmentSlot.OFFHAND, player.getMainHandItem());
        player.setItemSlot(EquipmentSlot.MAINHAND, plain);
        update(player);
        player.setOnGround(true);
        close(player.getDestroySpeed(Blocks.STONE.defaultBlockState()), baseMining, "Offhand mining effect leaked");
        close(player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), 4.5, "Offhand tool reach leaked");
        double speed = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        for (Item ranged : List.of(Items.BOW, Items.CROSSBOW)) {
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.MAINHAND, gear(ranged, GearAffix.LIGHT));
            update(player);
            stats(player, 1, 4, 3, speed * 1.05);
        }
        player.setItemSlot(EquipmentSlot.MAINHAND, gear(Items.SHIELD, GearAffix.LIGHT, GearAffix.UNSTABLE));
        update(player);
        close(player.getAttributeValue(Attributes.MOVEMENT_SPEED), speed, "Mainhand shield applied movement");
        knockback(player, 1);
        player.setItemSlot(EquipmentSlot.OFFHAND, player.getMainHandItem());
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        update(player);
        close(player.getAttributeValue(Attributes.MOVEMENT_SPEED), speed * 1.05, "Offhand shield missing movement");
        knockback(player, 1.1);
        test.succeed();
    }

    @GameTest
    public void actualMeleeHitsUseModifiedDamage(GameTestHelper test) {
        ServerPlayer player = test.makeMockServerPlayerInLevel();
        for (GearAffix affix : List.of(GearAffix.SHARP, GearAffix.BLUNT)) {
            player.setItemSlot(EquipmentSlot.MAINHAND, gear(Items.IRON_SWORD, affix));
            for (int tick = 0; tick < 25; tick++) update(player);
            player.setOnGround(true);
            var target = EntityTypes.COW.create(test.getLevel(), EntitySpawnReason.COMMAND);
            require(target != null, "Could not create melee target");
            target.setNoAi(true);
            target.setPos(player.getX() + 1, player.getY(), player.getZ());
            test.getLevel().addFreshEntity(target);
            float health = target.getHealth();
            player.attack(target);
            close(health - target.getHealth(), affix == GearAffix.SHARP ? 6.6 : 5.4, "Actual melee hit damage");
            target.discard();
        }
        test.succeed();
    }

    @GameTest
    public void durabilityChangesBreakThresholdAcrossMaterials(GameTestHelper test) {
        for (Item item : List.of(Items.WOODEN_SWORD, Items.STONE_PICKAXE, Items.IRON_AXE, Items.GOLDEN_SHOVEL,
                Items.DIAMOND_HOE, Items.NETHERITE_SWORD, Items.BOW, Items.CROSSBOW, Items.SHIELD,
                Items.SHEARS, Items.IRON_CHESTPLATE, Items.ELYTRA)) {
            int base = new ItemStack(item).getMaxDamage();
            for (GearAffix effect : List.of(GearAffix.DURABLE, GearAffix.FRAGILE)) {
                ItemStack stack = gear(item, effect);
                int expected = (int) Math.round(base * (effect == GearAffix.DURABLE ? 1.2 : 0.8));
                require(stack.getMaxDamage() == expected, "Wrong max durability for " + item);
                stack.setDamageValue(expected - 2);
                stack.hurtAndBreak(1, test.getLevel(), null, broken -> {});
                require(!stack.isEmpty() && stack.getDamageValue() == expected - 1, "Gear broke early");
                stack.hurtAndBreak(1, test.getLevel(), null, broken -> {});
                require(stack.isEmpty(), "Gear did not break at modified durability");
            }
        }
        test.succeed();
    }

    @GameTest
    public void tooltipStatsAndNameColorsMatchAllThirtyEffects(GameTestHelper test) {
        String[] expected = {"+10%", "+10%", "+10%", "+20%", "-10%", "-10%", "-10%", "-20%",
                "+10%", "+0.5", "+5%", "+10%", "-10%", "-0.5", "-5%", "-10%",
                "+10%", "-10%", "+1", "-1", "+5%", "-5%", "-10%", "+10%", "+10%", "-10%", "-10%", "+10%", "+5%", "-5%"};
        for (GearAffix effect : GearAffix.values()) {
            Item item = switch (effect.kind) {
                case ARMOR, KNOCKBACK, HEALTH -> Items.IRON_CHESTPLATE;
                case MINING -> Items.IRON_PICKAXE;
                case EXPERIENCE -> Items.FISHING_ROD;
                case FLIGHT -> Items.ELYTRA;
                case RECOVERY -> Items.SHIELD;
                case RANGED_DAMAGE, DRAW, PROJECTILE_SPEED -> Items.BOW;
                default -> Items.IRON_SWORD;
            };
            ItemStack stack = gear(item, effect);
            String key = "item.wildfortune.stat." + effect.kind.name().toLowerCase(java.util.Locale.ROOT);
            for (TooltipFlag flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                var lines = stack.getTooltipLines(Item.TooltipContext.of(test.getLevel()), null, flag);
                var stats = lines.stream().filter(line -> line.getContents() instanceof TranslatableContents text
                        && text.getKey().startsWith("item.wildfortune.stat.")).toList();
                require(stats.size() == 1, "Missing or duplicate effect description");
                var text = (TranslatableContents) stats.getFirst().getContents();
                require(text.getKey().equals(key) && text.getArgs().length == 1
                        && text.getArgs()[0].equals(expected[effect.ordinal()]), "Wrong displayed stat for " + effect);
                ChatFormatting color = effect.positive ? ChatFormatting.GREEN : ChatFormatting.RED;
                require(stats.getFirst().getStyle().getColor().equals(TextColor.fromLegacyFormat(color)), "Wrong stat color");
                requireNameColor(lines.getFirst(), color);
            }
        }
        ItemStack tool = gear(Items.IRON_PICKAXE, GearAffix.LONG);
        require(((TranslatableContents) GearAffixes.descriptions(tool).getFirst().getContents()).getKey()
                .equals("item.wildfortune.stat.tool_reach"), "Tool tooltip says melee reach");
        ItemStack canceled = gear(Items.IRON_SWORD, GearAffix.SHARP, GearAffix.BLUNT);
        requireNameColor(canceled.getTooltipLines(Item.TooltipContext.of(test.getLevel()), null, TooltipFlag.NORMAL).getFirst(), ChatFormatting.GRAY);
        require(GearAffixes.descriptions(canceled).isEmpty(), "Canceled effect still displayed");
        ItemStack mixed = gear(Items.IRON_SWORD, GearAffix.SHARP, GearAffix.HEAVY);
        requireNameColor(mixed.getStyledHoverName(), ChatFormatting.GOLD);
        test.succeed();
    }

    @GameTest
    public void hiddenTooltipsStayHidden(GameTestHelper test) {
        ItemStack stack = gear(Items.IRON_SWORD, GearAffix.SHARP, GearAffix.LIGHT);
        stack.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new java.util.LinkedHashSet<>()));
        require(stack.getTooltipLines(Item.TooltipContext.of(test.getLevel()), null, TooltipFlag.NORMAL).isEmpty(),
                "Affix descriptions leaked through hide_tooltip");
        test.succeed();
    }

    private static ItemStack gear(Item item, GearAffix... effects) {
        ItemStack stack = new ItemStack(item);
        GearAffixes.applyAll(stack, List.of(effects));
        return stack;
    }

    private static void update(ServerPlayer player) { player.doTick(); }

    private static void stats(ServerPlayer player, double attack, double attackSpeed, double reach, double speed) {
        close(player.getAttributeValue(Attributes.ATTACK_DAMAGE), attack, "Player attack damage");
        close(player.getAttributeValue(Attributes.ATTACK_SPEED), attackSpeed, "Player attack speed");
        close(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), reach, "Player melee reach");
        close(player.getAttributeValue(Attributes.MOVEMENT_SPEED), speed, "Player movement speed");
    }

    private static void knockback(ServerPlayer player, double expected) {
        player.setDeltaMovement(0, 0, 0);
        player.knockback(1, 1, 0, player.damageSources().generic(), 0, false);
        close(Math.abs(player.getDeltaMovement().x), expected, "Received knockback");
    }

    private static void requireNameColor(Component name, ChatFormatting color) {
        TextColor expected = TextColor.fromLegacyFormat(color);
        name.visit((style, text) -> {
            if (!text.isEmpty()) require(expected.equals(style.getColor()), "Name color overwritten by vanilla rarity: " + text);
            return Optional.empty();
        }, Style.EMPTY);
    }

    private static void close(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 0.0001, message + ": " + actual + " expected " + expected);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
