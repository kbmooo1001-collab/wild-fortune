package dev.wildfortune.mixin;

import dev.wildfortune.gear.GearAffixes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({CraftingMenu.class, InventoryMenu.class})
public abstract class GearQuickCraftMixin {
    @Inject(method = "quickMoveStack", at = @At("HEAD"))
    private void wildfortune$shiftCraft(Player player, int slot, CallbackInfoReturnable<ItemStack> callback) {
        if (slot != 0 || !(player.level() instanceof ServerLevel) || player.getInventory().getFreeSlot() < 0) return;
        AbstractCraftingMenu menu = (AbstractCraftingMenu) (Object) this;
        // Damageable gear is unstackable, so a free inventory slot guarantees a successful transfer.
        GearAffixes.roll(menu.getResultSlot().getItem(), player.getRandom());
    }
}
