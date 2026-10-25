package dev.wildfortune.mixin.client;

import dev.wildfortune.monster.MonsterMutations;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class MutationNameStyleMixin {
    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void wildfortune$unifiedName(Entity entity, CallbackInfoReturnable<Component> callback) {
        if (entity instanceof Mob) callback.setReturnValue(MonsterMutations.redesignName(callback.getReturnValue()));
    }
}
