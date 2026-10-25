package dev.wildfortune.mixin;

import dev.wildfortune.monster.MonsterMutations;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AreaEffectCloud.class)
public abstract class CreeperEffectCloudMixin {
    @ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private MobEffectInstance wildfortune$limitCreeperCloudDuration(MobEffectInstance effect) {
        return MonsterMutations.creeperCloudEffect((AreaEffectCloud) (Object) this, effect);
    }
}
