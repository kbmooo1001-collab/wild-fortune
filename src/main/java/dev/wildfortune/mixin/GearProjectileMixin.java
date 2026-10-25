package dev.wildfortune.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.wildfortune.gear.GearActions;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ProjectileWeaponItem.class)
public abstract class GearProjectileMixin {
    @ModifyVariable(method = "shoot", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float wildfortune$speed(float vanilla, @Local(argsOnly = true) ItemStack weapon) {
        return GearActions.projectileSpeed(weapon, vanilla);
    }
    @ModifyExpressionValue(method = "shoot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ProjectileWeaponItem;createProjectile(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/projectile/Projectile;"))
    private Projectile wildfortune$damage(Projectile projectile, @Local(argsOnly = true) ItemStack weapon) {
        return GearActions.prepareProjectile(projectile, weapon);
    }
}
