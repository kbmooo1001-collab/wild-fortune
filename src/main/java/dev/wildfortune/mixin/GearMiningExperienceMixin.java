package dev.wildfortune.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.wildfortune.gear.GearExperience;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerPlayerGameMode.class)
public abstract class GearMiningExperienceMixin {
    @Shadow @Final protected ServerPlayer player;
    @WrapMethod(method = "destroyBlock")
    private boolean wildfortune$mining(BlockPos pos, Operation<Boolean> original) {
        return GearExperience.duringMining(player, () -> original.call(pos));
    }
}
