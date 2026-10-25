package dev.wildfortune.mixin;

import dev.wildfortune.gear.GearAffixes;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmithingMenu.class)
public abstract class GearSmithingMixin extends ItemCombinerMenu {
    protected GearSmithingMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access,
            ItemCombinerMenuSlotDefinition slots) { super(type, id, inventory, access, slots); }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void wildfortune$inheritUpgrade(CallbackInfo callback) {
        GearAffixes.inherit(resultSlots.getItem(0), inputSlots.getItem(1));
    }
}
