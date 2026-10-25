package dev.wildfortune.mixin;

import dev.wildfortune.gear.GearActions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LivingEntity.class)
public abstract class GearFlightMixin {
    @ModifyArg(method = "travelFallFlying", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"), index = 1)
    private Vec3 wildfortune$flight(Vec3 vanilla) {
        return GearActions.flightMovement((LivingEntity) (Object) this, vanilla);
    }
}
