package dev.wildfortune.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class MutationNameRendererMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z",
            at = @At("HEAD"), cancellable = true)
    private void wildfortune$limitMutationLabelDistance(LivingEntity entity, double cameraDistanceSquared,
            CallbackInfoReturnable<Boolean> callback) {
        if (!wildfortune$isMutationLabel(entity)) return;
        var player = Minecraft.getInstance().player;
        double range = entity.isInvisible() ? 6.0 : 15.0;
        // Use player position instead of third-person camera position. Include the exact boundary.
        if (player == null || player.distanceToSqr(entity) > range * range) {
            callback.setReturnValue(false);
        }
    }

    // Only the label bypasses invisibility. Body rendering and other labels keep vanilla behavior.
    @ModifyExpressionValue(
            method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isInvisibleTo(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean wildfortune$showMutationLabel(boolean invisible, LivingEntity entity, double distance) {
        if (wildfortune$isMutationLabel(entity)) {
            return false;
        }
        return invisible;
    }

    private static boolean wildfortune$isMutationLabel(LivingEntity entity) {
        Component name = entity.getCustomName();
        return entity.isCustomNameVisible() && name != null
                && name.getContents() instanceof TranslatableContents text
                && text.getKey().startsWith("entity.wildfortune.rank.");
    }
}
