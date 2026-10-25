package dev.wildfortune.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.wildfortune.gear.GearActions;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(BowItem.class)
public abstract class GearBowMixin {
    @ModifyArg(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BowItem;getPowerForTime(I)F"), index = 0)
    private int wildfortune$draw(int elapsed, @Local(argsOnly = true) ItemStack bow) {
        return GearActions.bowDrawTicks(bow, elapsed);
    }
}
