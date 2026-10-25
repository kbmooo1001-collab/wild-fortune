package dev.wildfortune.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.wildfortune.gear.GearActions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(BlocksAttacks.class)
public abstract class GearShieldMixin {
    @ModifyArg(method = "disable", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/component/BlocksAttacks;disableBlockingForTicks(F)I"), index = 0)
    private float wildfortune$recovery(float seconds, @Local(argsOnly = true) ItemStack shield,
            @Local(argsOnly = true) LivingEntity user) {
        return GearActions.shieldDisableSeconds(user, shield, seconds);
    }
}
