package dev.wildfortune.mixin;

import dev.wildfortune.gear.GearExperience;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Block.class)
public abstract class GearBlockExperienceMixin {
    @ModifyArg(method = "popExperience", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"), index = 2)
    private int wildfortune$experience(int amount) { return GearExperience.miningExperience(amount); }
}
