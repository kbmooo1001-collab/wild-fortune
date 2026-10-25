package dev.wildfortune.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import dev.wildfortune.gear.PrismaticNames;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public abstract class PrismaticTextMixin {
    @Unique private static final long wildfortune$animationStart = System.nanoTime();

    @ModifyVariable(method = "accept(ILnet/minecraft/network/chat/Style;Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Style wildfortune$prism(Style style, @Local(argsOnly = true) int character) {
        double seconds = (System.nanoTime() - wildfortune$animationStart) / 1_000_000_000.0;
        return PrismaticNames.animate(style, character, seconds);
    }
}
