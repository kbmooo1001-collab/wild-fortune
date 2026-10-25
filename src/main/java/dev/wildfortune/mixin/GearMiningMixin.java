package dev.wildfortune.mixin;

import dev.wildfortune.gear.GearAffixes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class GearMiningMixin {
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void wildfortune$miningSpeed(BlockState state, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(GearAffixes.miningSpeed(((Player) (Object) this).getMainHandItem(), callback.getReturnValue()));
    }
}
