package dev.wildfortune.mixin;

import dev.wildfortune.gear.GearAffixes;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class GearKnockbackMixin {
    @ModifyVariable(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double wildfortune$stability(double strength) {
        return GearAffixes.knockbackStrength((LivingEntity) (Object) this, strength);
    }
}
