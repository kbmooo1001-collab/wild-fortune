package dev.wildfortune.mixin;

import dev.wildfortune.gear.GearFireworkData;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireworkRocketEntity.class)
public abstract class GearFireworkMixin implements GearFireworkData {
    @Unique private double wildfortune$damageBonus;
    public void wildfortune$setDamageBonus(double bonus) { wildfortune$damageBonus = bonus; }
    public double wildfortune$getDamageBonus() { return wildfortune$damageBonus; }
    @ModifyArg(method = "dealExplosionDamage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"), index = 2)
    private float wildfortune$damage(float vanilla) { return (float) (vanilla * (1 + wildfortune$damageBonus)); }
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void wildfortune$save(ValueOutput output, CallbackInfo callback) { output.putDouble("wildfortune:ranged_bonus", wildfortune$damageBonus); }
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void wildfortune$load(ValueInput input, CallbackInfo callback) {
        double bonus = input.getDoubleOr("wildfortune:ranged_bonus", 0);
        wildfortune$damageBonus = Double.isFinite(bonus) && bonus >= -0.95 && bonus <= 5 ? bonus : 0;
    }
}
