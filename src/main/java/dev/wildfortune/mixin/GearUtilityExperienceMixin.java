package dev.wildfortune.mixin;

import dev.wildfortune.gear.GearExperience;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class GearUtilityExperienceMixin {
    @ModifyVariable(method = "giveExperiencePoints", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int wildfortune$experience(int amount) {
        return (Object) this instanceof ServerPlayer player ? GearExperience.utilityExperience(player, amount) : amount;
    }
}
