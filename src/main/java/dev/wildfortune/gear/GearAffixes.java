package dev.wildfortune.gear;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.function.ToDoubleFunction;
import dev.wildfortune.config.WildFortuneConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public final class GearAffixes {
    public static final String AFFIX_KEY = "wildfortune:affix"; // 0.4 compatibility
    public static final String AFFIXES_KEY = "wildfortune:affixes";
    public static final String CRAFTED_KEY = "wildfortune:crafted";
    public static final String VALUES_KEY = "wildfortune:affix_values_v1";
    public enum Category { MELEE, TOOL, ARMOR, GLIDER, SHIELD, RANGED, UTILITY }

    private GearAffixes() {}

    public static List<GearAffix> getAll(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return List.of();
        return getAll(stack, data.copyTag());
    }

    private static List<GearAffix> getAll(ItemStack stack, CompoundTag tag) {
        EnumSet<GearAffix> effects = EnumSet.noneOf(GearAffix.class);
        if (tag.contains(AFFIXES_KEY)) {
            ListTag list = tag.getListOrEmpty(AFFIXES_KEY);
            for (int i = 0; i < list.size(); i++) {
                GearAffix effect = GearAffix.byId(list.getStringOr(i, ""));
                if (effect != null && supports(stack, effect)) effects.add(effect);
            }
        } else {
            GearAffix legacy = GearAffix.byId(tag.getStringOr(AFFIX_KEY, ""));
            if (legacy != null && supports(stack, legacy)) effects.add(legacy);
        }
        removeOpposites(effects);
        return List.copyOf(effects);
    }

    private static void removeOpposites(EnumSet<GearAffix> effects) {
        for (GearAffix.Kind kind : GearAffix.Kind.values()) {
            boolean positive = effects.stream().anyMatch(e -> e.kind == kind && e.positive);
            boolean negative = effects.stream().anyMatch(e -> e.kind == kind && !e.positive);
            if (positive && negative) effects.removeIf(e -> e.kind == kind);
        }
    }

    public static GearAffix get(ItemStack stack) {
        List<GearAffix> effects = getAll(stack);
        return effects.isEmpty() ? null : effects.getFirst();
    }

    public static boolean isProcessed(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(CRAFTED_KEY, false);
    }

    public static boolean isGear(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamageableItem() && stack.getMaxStackSize() == 1;
    }

    public static Category category(ItemStack stack) {
        if (stack.has(DataComponents.GLIDER)) return Category.GLIDER;
        if (stack.is(Items.SHIELD)) return Category.SHIELD;
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null && equippable.slot().isArmor()) return hasArmor(stack) ? Category.ARMOR : Category.UTILITY;
        if (stack.getItem() instanceof ProjectileWeaponItem) return Category.RANGED;
        if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.SPEARS)
                || stack.is(Items.TRIDENT) || stack.is(Items.MACE)) return Category.MELEE;
        if (stack.has(DataComponents.TOOL)) return Category.TOOL;
        if (rawAttributes(stack).modifiers().stream().anyMatch(e ->
                e.attribute().equals(Attributes.ATTACK_DAMAGE) && e.modifier().amount() > 0)) return Category.MELEE;
        return Category.UTILITY;
    }

    public static boolean supports(ItemStack stack, GearAffix affix) {
        if (!isGear(stack)) return false;
        Category type = category(stack);
        return switch (affix.kind) {
            case DURABILITY, MOVEMENT -> true;
            case ATTACK, ATTACK_SPEED -> type == Category.MELEE;
            case REACH -> type == Category.MELEE || type == Category.TOOL;
            case MINING -> type == Category.TOOL;
            case ARMOR -> type == Category.ARMOR;
            case KNOCKBACK -> type == Category.ARMOR || type == Category.GLIDER || type == Category.SHIELD || type == Category.UTILITY;
            case EXPERIENCE -> type == Category.TOOL || type == Category.UTILITY;
            case HEALTH -> type == Category.ARMOR || type == Category.GLIDER || type == Category.SHIELD || type == Category.UTILITY;
            case FLIGHT -> type == Category.GLIDER;
            case RECOVERY -> type == Category.SHIELD;
            case RANGED_DAMAGE, DRAW, PROJECTILE_SPEED -> type == Category.RANGED;
        };
    }

    private static boolean hasArmor(ItemStack stack) {
        return rawAttributes(stack).modifiers().stream()
                .anyMatch(e -> e.attribute().equals(Attributes.ARMOR) && e.modifier().amount() > 0);
    }

    public static void roll(ItemStack stack, RandomSource random) {
        if (!isGear(stack) || isProcessed(stack) || !getAll(stack).isEmpty()) return;
        var settings = WildFortuneConfig.get();
        List<GearAffix> chosen = new ArrayList<>();
        for (GearAffix effect : GearAffix.values()) {
            var rule = settings.gear().get(effect);
            if (settings.gearEnabled() && rule.enabled() && supports(stack, effect)
                    && random.nextFloat() < rule.chance()) chosen.add(effect);
        }
        // Empty rolls are final; opposite effects are removed before saving.
        applyAll(stack, chosen);
    }

    public static void apply(ItemStack stack, GearAffix effect) { applyAll(stack, List.of(effect)); }

    public static void applyAll(ItemStack stack, Collection<GearAffix> effects) {
        var settings = WildFortuneConfig.get();
        applyValues(stack, effects, effect -> settings.gear().get(effect).amount());
    }

    private static void applyValues(ItemStack stack, Collection<GearAffix> effects, ToDoubleFunction<GearAffix> amounts) {
        EnumSet<GearAffix> canonical = EnumSet.noneOf(GearAffix.class);
        for (GearAffix effect : effects) {
            if (!supports(stack, effect)) throw new IllegalArgumentException("Unsupported gear effect: " + effect);
            canonical.add(effect);
        }
        removeOpposites(canonical);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            ListTag list = new ListTag();
            canonical.forEach(effect -> list.add(StringTag.valueOf(effect.id)));
            CompoundTag values = new CompoundTag();
            canonical.forEach(effect -> values.putDouble(effect.id, amounts.applyAsDouble(effect)));
            tag.put(AFFIXES_KEY, list);
            tag.put(VALUES_KEY, values);
            tag.remove(AFFIX_KEY);
            tag.putBoolean(CRAFTED_KEY, true);
        });
        normalizeDurability(stack);
    }

    public static void prepareCraft(ItemStack result, CraftingContainer inputs, Player player) {
        if (player.level() instanceof ServerLevel && isGear(result) && !isProcessed(result)) roll(result, player.getRandom());
    }

    public static double net(ItemStack stack, GearAffix.Kind kind) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || stack.isEmpty()) return 0;
        CompoundTag tag = data.copyTag();
        return net(tag.getCompoundOrEmpty(VALUES_KEY), getAll(stack, tag), kind);
    }

    private static double net(CompoundTag values, List<GearAffix> effects, GearAffix.Kind kind) {
        return effects.stream().filter(effect -> effect.kind == kind).mapToDouble(effect -> savedAmount(values, effect)).sum();
    }

    private static double savedAmount(CompoundTag values, GearAffix effect) {
        double amount = values.getDoubleOr(effect.id, effect.amount);
        return WildFortuneConfig.validAmount(effect, amount) ? amount : effect.amount;
    }

    public static ItemAttributeModifiers rawAttributes(ItemStack stack) {
        return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
    }

    public static ItemAttributeModifiers attributes(ItemStack stack, ItemAttributeModifiers base) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || stack.isEmpty()) return base;
        CompoundTag tag = data.copyTag();
        List<GearAffix> effects = getAll(stack, tag);
        if (effects.isEmpty()) return base;
        CompoundTag values = tag.getCompoundOrEmpty(VALUES_KEY);
        List<ItemAttributeModifiers.Entry> entries = new ArrayList<>(base.modifiers());
        EquipmentSlotGroup slot = activeSlot(stack);
        add(entries, Attributes.ATTACK_DAMAGE, "attack", net(values, effects, GearAffix.Kind.ATTACK),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, slot);
        add(entries, Attributes.ATTACK_SPEED, "attack_speed", net(values, effects, GearAffix.Kind.ATTACK_SPEED),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, slot);
        add(entries, category(stack) == Category.TOOL ? Attributes.BLOCK_INTERACTION_RANGE : Attributes.ENTITY_INTERACTION_RANGE,
                "reach", net(values, effects, GearAffix.Kind.REACH), AttributeModifier.Operation.ADD_VALUE, slot);
        add(entries, Attributes.MOVEMENT_SPEED, "movement", net(values, effects, GearAffix.Kind.MOVEMENT),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE, slot);
        add(entries, Attributes.MAX_HEALTH, "health", net(values, effects, GearAffix.Kind.HEALTH),
                AttributeModifier.Operation.ADD_VALUE, slot);
        double armor = net(values, effects, GearAffix.Kind.ARMOR);
        for (var entry : base.modifiers()) {
            if (entry.attribute().equals(Attributes.ARMOR) && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                add(entries, Attributes.ARMOR, "armor", entry.modifier().amount() * armor,
                        AttributeModifier.Operation.ADD_VALUE, entry.slot());
            }
        }
        return new ItemAttributeModifiers(List.copyOf(entries));
    }

    private static void add(List<ItemAttributeModifiers.Entry> entries, Holder<Attribute> attribute, String id,
            double amount, AttributeModifier.Operation operation, EquipmentSlotGroup slot) {
        if (Math.abs(amount) < 0.000001) return;
        entries.add(new ItemAttributeModifiers.Entry(attribute,
                new AttributeModifier(Identifier.fromNamespaceAndPath("wildfortune", "gear_" + id + "_" + slot.getSerializedName()),
                        amount, operation), slot, ItemAttributeModifiers.Display.hidden()));
    }

    private static EquipmentSlotGroup activeSlot(ItemStack stack) {
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null && equippable.slot().isArmor()) return EquipmentSlotGroup.bySlot(equippable.slot());
        return stack.is(Items.SHIELD) ? EquipmentSlotGroup.OFFHAND : EquipmentSlotGroup.MAINHAND;
    }

    public static float miningSpeed(ItemStack held, float vanillaSpeed) {
        return (float) (vanillaSpeed * (1 + net(held, GearAffix.Kind.MINING)));
    }

    public static double knockbackStrength(LivingEntity wearer, double strength) {
        double resistance = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack equipped = wearer.getItemBySlot(slot);
            if (!equipped.isEmpty() && activeSlot(equipped).test(slot)) {
                resistance += net(equipped, GearAffix.Kind.KNOCKBACK);
            }
        }
        // Scale the hit itself: negative stability still works on armor with zero vanilla resistance.
        return strength * Math.max(0, 1 - resistance);
    }

    public static Component displayName(ItemStack stack, Component original) {
        List<GearAffix> effects = getAll(stack);
        if (effects.isEmpty()) return isProcessed(stack)
                ? Component.translatable("item.wildfortune.affix.plain.name", original).withStyle(ChatFormatting.GRAY)
                : original;
        int positives = (int) effects.stream().filter(e -> e.positive).count();
        Component name = Component.translatable(nameKey(effects), original);
        return name.copy().withStyle(nameStyle(effects.size(), positives));
    }

    public static Style nameStyle(int total, int positives) {
        if (total == 0) return Style.EMPTY.withColor(ChatFormatting.GRAY);
        if (positives != 0 && positives != total) return Style.EMPTY.withColor(ChatFormatting.GOLD);
        boolean positive = positives == total;
        if (total >= 5) return PrismaticNames.style(positive);
        if (total == 4) return Style.EMPTY.withColor(positive ? 0xB388FF : 0xFF3366).withBold(true);
        if (total == 3) return Style.EMPTY.withColor(positive ? 0x55FFFF : 0xFF8844).withBold(true);
        return Style.EMPTY.withColor(positive ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    public static String nameKey(Collection<GearAffix> effects) {
        if (effects.isEmpty()) return "item.wildfortune.affix.plain.name";
        if (effects.size() == 1) return "item.wildfortune.affix." + effects.iterator().next().id + ".name";
        long positive = effects.stream().filter(effect -> effect.positive).count();
        return "item.wildfortune.combo." + positive + "_" + (effects.size() - positive);
    }

    public static List<Component> descriptions(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || stack.isEmpty()) return List.of();
        CompoundTag tag = data.copyTag();
        List<GearAffix> effects = getAll(stack, tag);
        List<Component> lines = new ArrayList<>();
        CompoundTag values = tag.getCompoundOrEmpty(VALUES_KEY);
        for (GearAffix.Kind kind : GearAffix.Kind.values()) {
            if (effects.stream().noneMatch(e -> e.kind == kind)) continue;
            double value = net(values, effects, kind);
            boolean absolute = kind == GearAffix.Kind.REACH || kind == GearAffix.Kind.HEALTH;
            boolean duration = kind == GearAffix.Kind.DRAW || kind == GearAffix.Kind.RECOVERY;
            String suffix = absolute ? "" : "%";
            double displayValue = duration ? -value : value;
            var number = java.math.BigDecimal.valueOf(Math.abs(displayValue))
                    .multiply(java.math.BigDecimal.valueOf(absolute ? 1 : 100));
            String amount = (displayValue > 0 ? "+" : "-") + number.stripTrailingZeros().toPlainString();
            String key = kind == GearAffix.Kind.REACH && category(stack) == Category.TOOL ? "tool_reach" : kind.name().toLowerCase(java.util.Locale.ROOT);
            if (kind == GearAffix.Kind.EXPERIENCE && category(stack) == Category.TOOL) key = "mining_experience";
            lines.add(Component.translatable("item.wildfortune.stat." + key, amount + suffix)
                    .withStyle(Math.abs(value) < 0.000001 ? ChatFormatting.GRAY : value > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        return lines;
    }

    public static void inherit(ItemStack result, ItemStack source) {
        if (result.isEmpty() || !isGear(result) || source.isEmpty()) return;
        CustomData data = source.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        CompoundTag values = tag.getCompoundOrEmpty(VALUES_KEY);
        applyValues(result, getAll(source, tag).stream().filter(effect -> supports(result, effect)).toList(),
                effect -> savedAmount(values, effect));
    }

    public static void inheritRepair(ItemStack result, ItemStack first, ItemStack second) {
        if (result.isEmpty() || !isGear(result)) return;
        inherit(result, first);
        int max = result.getMaxDamage();
        int remaining = first.getMaxDamage() - first.getDamageValue() + second.getMaxDamage() - second.getDamageValue() + max * 5 / 100;
        result.setDamageValue(Math.max(0, max - remaining));
    }

    private static void normalizeDurability(ItemStack stack) {
        int baseMax = stack.getItem().components().getOrDefault(DataComponents.MAX_DAMAGE, 0);
        if (baseMax <= 0) return;
        int maximum = Math.max(1, (int) Math.round(baseMax * (1 + net(stack, GearAffix.Kind.DURABILITY))));
        int damage = stack.getDamageValue();
        stack.set(DataComponents.MAX_DAMAGE, maximum);
        stack.setDamageValue(Math.min(damage, maximum - 1));
    }
}
