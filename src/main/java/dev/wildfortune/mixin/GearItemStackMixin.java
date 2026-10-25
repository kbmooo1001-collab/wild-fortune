package dev.wildfortune.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.wildfortune.gear.GearAffixes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class GearItemStackMixin {
    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void wildfortune$affixName(CallbackInfoReturnable<Component> callback) {
        callback.setReturnValue(GearAffixes.displayName((ItemStack) (Object) this, callback.getReturnValue()));
    }

    @Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
    private void wildfortune$affixDescription(Item.TooltipContext context, Player player, TooltipFlag flag,
            CallbackInfoReturnable<List<Component>> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        // Honor vanilla tooltip visibility, including its creative-only exception.
        if (stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT).hideTooltip()
                && !flag.isCreative()) return;
        if (!GearAffixes.getAll(stack).isEmpty()) {
            List<Component> lines = new ArrayList<>(callback.getReturnValue());
            lines.addAll(Math.min(1, lines.size()), GearAffixes.descriptions(stack));
            callback.setReturnValue(lines);
        }
    }

    @ModifyExpressionValue(method = {
            "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlotGroup;Lorg/apache/commons/lang3/function/TriConsumer;)V",
            "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getOrDefault(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object wildfortune$gearAttributes(Object original) {
        return original instanceof ItemAttributeModifiers modifiers
                ? GearAffixes.attributes((ItemStack) (Object) this, modifiers) : original;
    }
}
