package dev.wildfortune.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SubmitNodeCollection.class)
public abstract class MutationNameOcclusionMixin {
    // Vanilla first draws an opaque, depth-tested name, then an extra see-through copy.
    // Keep the original visible text (including its style, light and animated font),
    // but omit the copy that reveals our monster names through terrain.
    @WrapOperation(method = "submitNameTag", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/phase/TranslucentFeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/TranslucentSubmit;)V"))
    private void wildfortune$occludeMutationName(TranslucentFeatureRenderPhase phase, TranslucentSubmit submit,
            Operation<Void> original, @Local(argsOnly = true) Component name) {
        if (name.getContents() instanceof TranslatableContents text) {
            for (int rank = 1; rank <= 4; rank++) {
                if (text.getKey().equals("entity.wildfortune.rank." + rank)) return;
            }
        }
        original.call(phase, submit);
    }
}
