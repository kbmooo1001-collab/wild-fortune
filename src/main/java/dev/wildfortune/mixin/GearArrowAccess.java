package dev.wildfortune.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractArrow.class)
public interface GearArrowAccess {
    @Accessor("baseDamage") double wildfortune$getBaseDamage();
}
