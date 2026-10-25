package dev.wildfortune.mixin;

import dev.wildfortune.gear.GearAffixes;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GrindstoneMenu.class)
public abstract class GearGrindstoneMixin {
    @Inject(method = "computeResult", at = @At("RETURN"))
    private void wildfortune$keepGrindstoneAffix(ItemStack first, ItemStack second, CallbackInfoReturnable<ItemStack> callback) {
        if (first.isEmpty()) GearAffixes.inherit(callback.getReturnValue(), second);
        else if (second.isEmpty()) GearAffixes.inherit(callback.getReturnValue(), first);
        else GearAffixes.inheritRepair(callback.getReturnValue(), first, second);
    }
}
