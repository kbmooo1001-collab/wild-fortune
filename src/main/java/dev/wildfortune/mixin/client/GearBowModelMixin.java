package dev.wildfortune.mixin.client;

import dev.wildfortune.gear.GearActions;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(UseDuration.class)
public abstract class GearBowModelMixin {
    @Inject(method = "useDuration", at = @At("RETURN"), cancellable = true)
    private static void wildfortune$drawModel(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> callback) {
        if (stack.is(Items.BOW)) callback.setReturnValue(GearActions.bowDrawTicks(stack, callback.getReturnValue()));
    }
}
