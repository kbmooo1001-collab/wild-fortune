package dev.wildfortune;

import dev.wildfortune.gear.GearAffix;
import dev.wildfortune.gear.GearAffixes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.level.block.Blocks;

public final class GearAffixesGameTest {
    @GameTest
    public void normalCraftDoesNotRollPreviewAndConsumesMaterials(GameTestHelper test) {
        ServerPlayer player = test.makeMockServerPlayerInLevel();
        CraftingMenu menu = swordMenu(player, 2);
        ItemStack preview = menu.getResultSlot().getItem();
        require(preview.is(Items.IRON_SWORD), "Sword recipe not loaded");
        require(GearAffixes.get(preview) == null, "Preview already has a random affix");
        menu.clicked(0, 0, ContainerInput.PICKUP, player);
        ItemStack crafted = menu.getCarried();
        require(crafted.is(Items.IRON_SWORD) && GearAffixes.isProcessed(crafted), "Normal crafting missed affix");
        require(GearAffixes.getAll(crafted).stream().allMatch(e -> GearAffixes.supports(crafted, e)), "Invalid crafted effect");
        require(menu.getInputGridSlots().get(0).getItem().getCount() == 1, "Ingredients consumed incorrectly");
        require(GearAffixes.get(menu.getResultSlot().getItem()) == null, "Next preview inherited previous roll");
        List<GearAffix> affix = GearAffixes.getAll(crafted);
        GearAffixes.roll(crafted, RandomSource.create(999));
        require(GearAffixes.getAll(crafted).equals(affix), "Same crafted item rerolled");
        test.succeed();
    }

    @GameTest
    public void shiftCraftAndFullInventoryAreSafe(GameTestHelper test) {
        ServerPlayer player = test.makeMockServerPlayerInLevel();
        CraftingMenu menu = swordMenu(player, 3);
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
        require(GearAffixes.get(menu.getResultSlot().getItem()) == null, "Failed transfer rolled the preview");
        require(menu.getInputGridSlots().get(0).getItem().getCount() == 3, "Failed transfer consumed ingredients");
        player.getInventory().clearContent();
        menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
        int swords = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.IRON_SWORD)) {
                swords += stack.getCount();
                require(GearAffixes.isProcessed(stack), "Shift-crafted gear was not processed");
            }
        }
        require(swords == 3 && menu.getResultSlot().getItem().isEmpty(), "Shift crafting lost or duplicated gear: " + swords);
        test.succeed();
    }

    @GameTest
    public void inventoryCraftAndHotbarSwapWork(GameTestHelper test) {
        ServerPlayer player = test.makeMockServerPlayerInLevel();
        InventoryMenu inventory = new InventoryMenu(player.getInventory(), true, player);
        player.containerMenu = inventory;
        inventory.getInputGridSlots().get(0).set(new ItemStack(Items.IRON_INGOT));
        inventory.getInputGridSlots().get(3).set(new ItemStack(Items.IRON_INGOT));
        inventory.slotsChanged(inventory.getInputGridSlots().getFirst().container);
        require(inventory.getResultSlot().getItem().is(Items.SHEARS), "2x2 shears recipe not found");
        inventory.clicked(0, 0, ContainerInput.PICKUP, player);
        require(GearAffixes.isProcessed(inventory.getCarried()), "2x2 craft was not processed");
        inventory.setCarried(ItemStack.EMPTY);
        CraftingMenu menu = swordMenu(player, 1);
        menu.clicked(0, 0, ContainerInput.SWAP, player);
        require(GearAffixes.isProcessed(player.getInventory().getItem(0)), "Hotbar swap was not processed");
        require(menu.getResultSlot().getItem().isEmpty(), "Hotbar swap did not consume recipe");
        test.succeed();
    }

    @GameTest
    public void independentEffectsAndProbability(GameTestHelper test) {
        require(GearAffix.values().length == 30, "Expected thirty effects");
        Set<GearAffix> observed = new HashSet<>();
        RandomSource random = RandomSource.create(332211);
        int samples = 12000;
        int[] eligible = new int[GearAffix.values().length];
        int[] hits = new int[GearAffix.values().length];
        int empty = 0;
        int multiple = 0;
        for (int i = 0; i < samples; i++) {
            Item[] types = {Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_CHESTPLATE, Items.ELYTRA, Items.SHIELD, Items.BOW, Items.FISHING_ROD};
            ItemStack item = new ItemStack(types[i % types.length]);
            for (GearAffix effect : GearAffix.values()) if (GearAffixes.supports(item, effect)) eligible[effect.ordinal()]++;
            GearAffixes.roll(item, random);
            List<GearAffix> effects = GearAffixes.getAll(item);
            for (GearAffix effect : effects) {
                require(GearAffixes.supports(item, effect), "Inapplicable effect selected");
                observed.add(effect);
                hits[effect.ordinal()]++;
            }
            if (effects.isEmpty()) empty++;
            if (effects.size() > 1) multiple++;
            GearAffixes.roll(item, random);
            require(effects.equals(GearAffixes.getAll(item)), "Empty/multiple roll changed on retry");
        }
        require(observed.size() == 30 && empty > 0 && multiple > 0, "Missing effects or independent roll outcomes");
        for (GearAffix effect : GearAffix.values()) {
            require(eligible[effect.ordinal()] > 0, "Effect has no valid target");
            double frequency = (double) hits[effect.ordinal()] / eligible[effect.ordinal()];
            double survivingChance = effect.chance * (1 - effect.chance);
            require(Math.abs(frequency - survivingChance) < 0.025, "Wrong surviving frequency for " + effect + ": " + frequency);
        }
        ItemStack stone = new ItemStack(Items.COBBLESTONE);
        GearAffixes.roll(stone, random);
        require(GearAffixes.get(stone) == null, "Non-gear item received an affix");
        WildFortune.LOGGER.info("Independent effect sample: {} items, {} empty, {} multiple; all 30 effects within 2.5 percentage points of 21% after cancellation",
                samples, empty, multiple);
        test.succeed();
    }

    @GameTest
    public void statsSlotsMiningAndDurabilityAreReal(GameTestHelper test) {
        for (GearAffix affix : GearAffix.values()) {
            Item item = switch (affix.kind) {
                case ARMOR, KNOCKBACK, HEALTH -> Items.IRON_CHESTPLATE;
                case MINING -> Items.IRON_PICKAXE;
                case EXPERIENCE -> Items.FISHING_ROD;
                case FLIGHT -> Items.ELYTRA;
                case RECOVERY -> Items.SHIELD;
                case RANGED_DAMAGE, DRAW, PROJECTILE_SPEED -> Items.BOW;
                default -> Items.IRON_SWORD;
            };
            ItemStack stack = new ItemStack(item);
            GearAffixes.apply(stack, affix);
            require(stack.getHoverName().getContents() instanceof TranslatableContents text
                    && text.getKey().equals("item.wildfortune.affix." + affix.id + ".name"), "Name prefix missing");
            require(stack.getTooltipLines(Item.TooltipContext.of(test.getLevel()), null, TooltipFlag.NORMAL).size() > 1,
                    "Tooltip missing");
            if (affix.kind == GearAffix.Kind.ATTACK) {
                AttributeInstance damage = new AttributeInstance(Attributes.ATTACK_DAMAGE, changed -> {});
                damage.setBaseValue(1);
                stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                    if (attribute.equals(Attributes.ATTACK_DAMAGE)) damage.addTransientModifier(modifier);
                });
                close(damage.getValue(), 6 * (1 + affix.amount), "Iron sword damage incorrect");
                stack.forEachModifier(EquipmentSlot.OFFHAND, (attribute, modifier) -> {
                    require(!modifier.id().getNamespace().equals("wildfortune"), "Melee affix applied in off hand");
                });
            } else if (affix.kind == GearAffix.Kind.ARMOR) {
                AttributeInstance armor = new AttributeInstance(Attributes.ARMOR, changed -> {});
                stack.forEachModifier(EquipmentSlot.CHEST, (attribute, modifier) -> {
                    if (attribute.equals(Attributes.ARMOR)) armor.addTransientModifier(modifier);
                });
                close(armor.getValue(), 6 * (1 + affix.amount), "Chestplate armor incorrect");
                stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                    require(!modifier.id().getNamespace().equals("wildfortune"), "Armor affix applied in hand");
                });
            } else if (affix.kind == GearAffix.Kind.MINING) {
                ServerPlayer player = test.makeMockServerPlayerInLevel();
                int selected = player.getInventory().getSelectedSlot();
                player.getInventory().setItem(selected, new ItemStack(item));
                float base = player.getDestroySpeed(Blocks.STONE.defaultBlockState());
                player.getInventory().setItem(selected, stack);
                close(player.getDestroySpeed(Blocks.STONE.defaultBlockState()), base * (1 + affix.amount), "Mining speed incorrect");
            } else if (affix.kind == GearAffix.Kind.DURABILITY) {
                int expected = (int) Math.round(new ItemStack(item).getMaxDamage() * (1 + affix.amount));
                require(stack.getMaxDamage() == expected, "Maximum durability unchanged");
                GearAffixes.apply(stack, affix);
                require(stack.getMaxDamage() == expected, "Maximum durability stacked twice");
            } else if (affix.kind == GearAffix.Kind.ATTACK_SPEED || affix.kind == GearAffix.Kind.REACH || affix.kind == GearAffix.Kind.MOVEMENT) {
                var attribute = switch (affix.kind) {
                    case ATTACK_SPEED -> Attributes.ATTACK_SPEED;
                    case REACH -> Attributes.ENTITY_INTERACTION_RANGE;
                    default -> Attributes.MOVEMENT_SPEED;
                };
                double base = affix.kind == GearAffix.Kind.ATTACK_SPEED ? 4 : affix.kind == GearAffix.Kind.REACH ? 3 : 0.1;
                AttributeInstance stat = new AttributeInstance(attribute, changed -> {});
                stat.setBaseValue(base);
                stack.forEachModifier(EquipmentSlot.MAINHAND, (actual, modifier) -> {
                    if (actual.equals(attribute)) stat.addTransientModifier(modifier);
                });
                double expected = affix.kind == GearAffix.Kind.REACH ? base + affix.amount
                        : (affix.kind == GearAffix.Kind.ATTACK_SPEED ? 1.6 : base) * (1 + affix.amount);
                close(stat.getValue(), expected, "New attribute effect incorrect");
            } else if (affix.kind == GearAffix.Kind.KNOCKBACK) {
                ServerPlayer player = test.makeMockServerPlayerInLevel();
                player.setItemSlot(EquipmentSlot.CHEST, stack);
                player.setDeltaMovement(0, 0, 0);
                player.knockback(1, 1, 0, player.damageSources().generic(), 0, false);
                close(Math.abs(player.getDeltaMovement().x), 1 - affix.amount, "Stability effect did not change received knockback");
            }
        }
        test.succeed();
    }

    @GameTest
    public void equipmentTypesAndActiveSlotsAreRestricted(GameTestHelper test) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        require(!GearAffixes.supports(sword, GearAffix.EFFICIENT) && !GearAffixes.supports(sword, GearAffix.STURDY),
                "Sword accepts mining or armor effect");
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        require(!GearAffixes.supports(pickaxe, GearAffix.SHARP) && !GearAffixes.supports(pickaxe, GearAffix.SWIFT),
                "Mining tool accepts weapon-only effects");
        ItemStack bow = new ItemStack(Items.BOW);
        require(!GearAffixes.supports(bow, GearAffix.LONG) && !GearAffixes.supports(bow, GearAffix.SHARP),
                "Ranged weapon accepts melee-only effects");
        GearAffixes.apply(pickaxe, GearAffix.LONG);
        AttributeInstance reach = new AttributeInstance(Attributes.BLOCK_INTERACTION_RANGE, changed -> {});
        reach.setBaseValue(4.5);
        pickaxe.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.BLOCK_INTERACTION_RANGE)) reach.addTransientModifier(modifier);
        });
        close(reach.getValue(), 5, "Tool reach does not affect block interaction");
        ItemStack shield = new ItemStack(Items.SHIELD);
        GearAffixes.apply(shield, GearAffix.LIGHT);
        shield.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            require(!modifier.id().getNamespace().equals("wildfortune"), "Shield movement applied in wrong hand");
        });
        AttributeInstance speed = new AttributeInstance(Attributes.MOVEMENT_SPEED, changed -> {});
        speed.setBaseValue(0.1);
        shield.forEachModifier(EquipmentSlot.OFFHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.MOVEMENT_SPEED)) speed.addTransientModifier(modifier);
        });
        close(speed.getValue(), 0.105, "Shield movement missing in off hand");
        test.succeed();
    }

    @GameTest
    public void combinationsUseOnlyPolarityCountsAndCancelOpposites(GameTestHelper test) {
        List<GearAffix> allowed = java.util.Arrays.stream(GearAffix.values())
                .filter(e -> GearAffixes.supports(new ItemStack(Items.IRON_SWORD), e)).toList();
        java.util.Map<String, Component> names = new java.util.HashMap<>();
        for (int mask = 0; mask < 1 << allowed.size(); mask++) {
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            List<GearAffix> effects = new java.util.ArrayList<>();
            for (int i = 0; i < allowed.size(); i++) if ((mask & 1 << i) != 0) effects.add(allowed.get(i));
            GearAffixes.applyAll(sword, effects);
            var remaining = GearAffixes.getAll(sword);
            long positives = remaining.stream().filter(e -> e.positive).count();
            String signature = remaining.size() == 1 ? remaining.getFirst().id : positives + "_" + (remaining.size() - positives);
            Component previous = names.putIfAbsent(signature, sword.getHoverName());
            require(previous == null || previous.equals(sword.getHoverName()), "Same counts produced different names");
            require(sword.get(DataComponents.CUSTOM_DATA).copyTag().getListOrEmpty(GearAffixes.AFFIXES_KEY).size()
                    == GearAffixes.getAll(sword).size(), "Canceled effects remained in saved data");
        }
        require(names.size() == 29 && new HashSet<>(names.values()).size() == 29, "Expected 18 count names, 10 singles and one neutral name");
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        GearAffixes.applyAll(sword, List.of(GearAffix.SHARP, GearAffix.SWIFT));
        require(((TranslatableContents) sword.getHoverName().getContents()).getKey().equals("item.wildfortune.combo.2_0"),
                "Expected named combination");
        GearAffixes.applyAll(sword, List.of(GearAffix.SHARP, GearAffix.BLUNT, GearAffix.DURABLE, GearAffix.FRAGILE));
        close(GearAffixes.net(sword, GearAffix.Kind.ATTACK), 0, "Opposite damage effects failed to cancel");
        require(sword.getMaxDamage() == 250 && GearAffixes.descriptions(sword).isEmpty(), "Canceled effects still described");
        require(GearAffixes.getAll(sword).isEmpty(), "Canceled effects still stored");
        require(sword.getHoverName().getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.GRAY)),
                "Neutral name must be gray");
        require(((TranslatableContents) sword.getHoverName().getContents()).getKey().equals("item.wildfortune.affix.plain.name"),
                "Missing neutral name");
        GearAffixes.applyAll(sword, List.of(GearAffix.SHARP, GearAffix.BLUNT, GearAffix.SWIFT));
        require(GearAffixes.getAll(sword).equals(List.of(GearAffix.SWIFT)), "Unrelated effect removed with canceled pair");
        require(((TranslatableContents) sword.getHoverName().getContents()).getKey().equals("item.wildfortune.affix.swift.name"),
                "Canceled pair still affected mixed name");
        for (GearAffix.Kind kind : GearAffix.Kind.values()) {
            ItemStack target = new ItemStack(switch (kind) {
                case ARMOR, KNOCKBACK, HEALTH -> Items.IRON_CHESTPLATE;
                case MINING -> Items.IRON_PICKAXE;
                case EXPERIENCE -> Items.FISHING_ROD;
                case FLIGHT -> Items.ELYTRA;
                case RECOVERY -> Items.SHIELD;
                case RANGED_DAMAGE, DRAW, PROJECTILE_SPEED -> Items.BOW;
                default -> Items.IRON_SWORD;
            });
            var pair = java.util.Arrays.stream(GearAffix.values()).filter(e -> e.kind == kind).toList();
            // Simulate an existing 0.5.0 item containing both sides.
            CustomData.update(DataComponents.CUSTOM_DATA, target, tag -> {
                var list = new net.minecraft.nbt.ListTag();
                pair.forEach(e -> list.add(net.minecraft.nbt.StringTag.valueOf(e.id)));
                tag.put(GearAffixes.AFFIXES_KEY, list);
                tag.putBoolean(GearAffixes.CRAFTED_KEY, true);
            });
            require(GearAffixes.getAll(target).isEmpty() && GearAffixes.descriptions(target).isEmpty(), "Legacy pair still active: " + kind);
            GearAffixes.applyAll(target, pair);
            require(target.get(DataComponents.CUSTOM_DATA).copyTag().getListOrEmpty(GearAffixes.AFFIXES_KEY).isEmpty(),
                    "Pair not removed from saved data: " + kind);
        }
        ItemStack uncrafted = new ItemStack(Items.IRON_SWORD);
        require(((TranslatableContents) uncrafted.getHoverName().getContents()).getKey().equals("item.minecraft.iron_sword"),
                "Uncrafted item received neutral prefix");
        WildFortune.LOGGER.info("Count naming: all 1024 raw sword rolls reduce to {} names; 18 multiple-effect count groups verified", names.size());
        test.succeed();
    }

    @GameTest
    public void multipleEffectsSurviveSaveRepairAndUpgrade(GameTestHelper test) {
        ItemStack first = new ItemStack(Items.DIAMOND_SWORD);
        List<GearAffix> effects = List.of(GearAffix.SHARP, GearAffix.DURABLE, GearAffix.SWIFT, GearAffix.LONG, GearAffix.LIGHT);
        GearAffixes.applyAll(first, effects);
        var ops = test.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        ItemStack loaded = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, first).getOrThrow()).getOrThrow();
        require(GearAffixes.getAll(loaded).equals(GearAffixes.getAll(first)), "Multiple effects lost on load");
        require(first.getHoverName().equals(loaded.getHoverName()), "Combination name changed on load");
        first.setDamageValue(1000);
        ItemStack second = new ItemStack(Items.DIAMOND_SWORD);
        second.setDamageValue(1000);
        ItemStack repaired = new RepairItemRecipe().assemble(CraftingInput.of(2, 1, List.of(first, second)));
        require(GearAffixes.getAll(repaired).equals(GearAffixes.getAll(first)), "Repair lost part of the combination");
        ServerPlayer player = test.makeMockServerPlayerInLevel();
        SmithingMenu menu = new SmithingMenu(8, player.getInventory());
        menu.getSlot(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        menu.getSlot(1).set(loaded);
        menu.getSlot(2).set(new ItemStack(Items.NETHERITE_INGOT));
        menu.createResult();
        require(GearAffixes.getAll(menu.getSlot(3).getItem()).equals(GearAffixes.getAll(loaded)), "Upgrade lost part of the combination");
        ItemStack old = new ItemStack(Items.IRON_SWORD);
        CustomData.update(DataComponents.CUSTOM_DATA, old, tag -> {
            tag.putString(GearAffixes.AFFIX_KEY, "sharp");
            tag.putBoolean(GearAffixes.CRAFTED_KEY, true);
        });
        require(GearAffixes.getAll(old).equals(List.of(GearAffix.SHARP)), "Legacy item unreadable");
        ItemStack upgraded = new ItemStack(Items.NETHERITE_SWORD);
        GearAffixes.inherit(upgraded, old);
        require(GearAffixes.getAll(upgraded).equals(List.of(GearAffix.SHARP)), "Legacy inheritance failed");
        test.succeed();
    }

    @GameTest
    public void saveLoadAndRepairKeepFirstAffix(GameTestHelper test) {
        ItemStack first = new ItemStack(Items.IRON_SWORD);
        GearAffixes.apply(first, GearAffix.FRAGILE);
        first.setDamageValue(150);
        var ops = test.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var saved = ItemStack.CODEC.encodeStart(ops, first).getOrThrow();
        ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
        require(GearAffixes.get(loaded) == GearAffix.FRAGILE && loaded.getMaxDamage() == 200, "Save/load lost affix or stats");
        ItemStack second = new ItemStack(Items.IRON_SWORD);
        GearAffixes.apply(second, GearAffix.DURABLE);
        second.setDamageValue(250);
        ItemStack repaired = new RepairItemRecipe().assemble(CraftingInput.of(2, 1, List.of(loaded, second)));
        require(GearAffixes.get(repaired) == GearAffix.FRAGILE, "Repair rerolled or used second item's affix");
        require(repaired.getMaxDamage() == 200 && repaired.getDamageValue() == 90, "Repair incorrectly compounded durability");
        ItemStack plain = new ItemStack(Items.IRON_SWORD);
        plain.setDamageValue(200);
        ItemStack plainRepair = new RepairItemRecipe().assemble(CraftingInput.of(2, 1, List.of(plain, second)));
        GearAffixes.roll(plainRepair, RandomSource.create(1));
        require(GearAffixes.get(plainRepair) == null, "Repairing plain first item created a random affix");
        test.succeed();
    }

    @GameTest
    public void grindstoneAnvilAndSmithingPreserveAffixes(GameTestHelper test) {
        ServerPlayer player = test.makeMockServerPlayerInLevel();
        ItemStack first = new ItemStack(Items.IRON_SWORD);
        GearAffixes.apply(first, GearAffix.FRAGILE);
        first.setDamageValue(150);
        ItemStack second = new ItemStack(Items.IRON_SWORD);
        GearAffixes.apply(second, GearAffix.DURABLE);
        second.setDamageValue(250);
        GrindstoneMenu grindstone = new GrindstoneMenu(5, player.getInventory());
        grindstone.getSlot(0).set(first);
        grindstone.getSlot(1).set(second);
        grindstone.slotsChanged(grindstone.getSlot(0).container);
        ItemStack ground = grindstone.getSlot(2).getItem();
        require(GearAffixes.get(ground) == GearAffix.FRAGILE && ground.getMaxDamage() == 200, "Grindstone lost first affix");

        AnvilMenu anvil = new AnvilMenu(6, player.getInventory());
        anvil.getSlot(0).set(first.copy());
        anvil.setItemName("My sword");
        anvil.createResult();
        ItemStack renamed = anvil.getSlot(2).getItem();
        require(GearAffixes.get(renamed) == GearAffix.FRAGILE, "Anvil rename lost affix");
        require(renamed.get(DataComponents.CUSTOM_NAME) != null, "Anvil rename missing");

        for (GearAffix affix : new GearAffix[] {GearAffix.DURABLE, GearAffix.SHARP}) {
            SmithingMenu smithing = new SmithingMenu(7, player.getInventory());
            ItemStack diamond = new ItemStack(Items.DIAMOND_SWORD);
            GearAffixes.apply(diamond, affix);
            smithing.getSlot(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            smithing.getSlot(1).set(diamond);
            smithing.getSlot(2).set(new ItemStack(Items.NETHERITE_INGOT));
            smithing.createResult();
            ItemStack upgraded = smithing.getSlot(3).getItem();
            require(upgraded.is(Items.NETHERITE_SWORD) && GearAffixes.get(upgraded) == affix, "Upgrade lost affix");
            if (affix == GearAffix.DURABLE) {
                require(upgraded.getMaxDamage() == (int) Math.round(new ItemStack(Items.NETHERITE_SWORD).getMaxDamage() * 1.2),
                        "Upgrade retained old material durability");
            } else {
                AttributeInstance damage = new AttributeInstance(Attributes.ATTACK_DAMAGE, changed -> {});
                damage.setBaseValue(1);
                upgraded.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                    if (attribute.equals(Attributes.ATTACK_DAMAGE)) damage.addTransientModifier(modifier);
                });
                close(damage.getValue(), 8.8, "Upgrade retained diamond attack stats");
            }
        }
        test.succeed();
    }

    private static CraftingMenu swordMenu(ServerPlayer player, int quantity) {
        CraftingMenu menu = new CraftingMenu(1, player.getInventory(),
                ContainerLevelAccess.create(player.level(), player.blockPosition()));
        player.containerMenu = menu;
        menu.getInputGridSlots().get(0).set(new ItemStack(Items.IRON_INGOT, quantity));
        menu.getInputGridSlots().get(3).set(new ItemStack(Items.IRON_INGOT, quantity));
        menu.getInputGridSlots().get(6).set(new ItemStack(Items.STICK, quantity));
        menu.slotsChanged(menu.getInputGridSlots().getFirst().container);
        return menu;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void close(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 0.0001, message + ": " + actual + " expected " + expected);
    }
}
