package dev.wildfortune.mixin;

import dev.wildfortune.gear.GearAffixes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RepairItemRecipe.class)
public abstract class GearRepairRecipeMixin {
    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void wildfortune$keepRepairAffix(CraftingInput input, CallbackInfoReturnable<ItemStack> callback) {
        ItemStack first = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack item = input.getItem(i);
            if (item.isEmpty()) continue;
            if (first.isEmpty()) first = item;
            else {
                GearAffixes.inheritRepair(callback.getReturnValue(), first, item);
                return;
            }
        }
    }
}
