package dev.wildfortune.mixin;

import dev.wildfortune.gear.GearAffixes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResultSlot.class)
public abstract class GearResultSlotMixin {
    @Shadow @Final private Player player;
    @Shadow @Final private CraftingContainer craftSlots;

    @Inject(method = "remove", at = @At("RETURN"))
    private void wildfortune$normalCraft(int count, CallbackInfoReturnable<ItemStack> callback) {
        GearAffixes.prepareCraft(callback.getReturnValue(), craftSlots, player);
    }

    @Inject(method = "onSwapCraft", at = @At("HEAD"))
    private void wildfortune$hotbarCraft(int count, CallbackInfo callback) {
        GearAffixes.prepareCraft(((Slot) (Object) this).getItem(), craftSlots, player);
    }
}
