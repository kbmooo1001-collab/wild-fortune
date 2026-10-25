package dev.wildfortune.mixin;

import dev.wildfortune.gear.GearActions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrossbowItem.class)
public abstract class GearCrossbowMixin {
    @Inject(method = "getChargeDuration", at = @At("RETURN"), cancellable = true)
    private static void wildfortune$charge(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(GearActions.chargeTicks(stack, callback.getReturnValue()));
    }
}
