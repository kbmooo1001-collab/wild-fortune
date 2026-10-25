package dev.wildfortune.mixin;

import dev.wildfortune.monster.MonsterMutations;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.entity.EntitySpawnReason;

@Mixin(EntityType.class)
public abstract class EntityTypeMixin {
    // Structure templates load entity NBT after construction, replacing its Tags list.
    // Capture the reason again after that load so the eligibility marker survives.
    @Inject(method = "create(Lnet/minecraft/world/level/storage/ValueInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnRequest;)Ljava/util/Optional;",
            at = @At("RETURN"))
    private static void wildfortune$recordTemplateReason(ValueInput input, Level level, EntitySpawnRequest request,
            CallbackInfoReturnable<Optional<Entity>> callback) {
        callback.getReturnValue().ifPresent(entity -> MonsterMutations.recordSpawnReason(entity, request.reason()));
    }

    @Inject(method = "create(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/storage/ValueInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;)Ljava/util/Optional;",
            at = @At("RETURN"))
    private static void wildfortune$recordTypedTemplateReason(EntityType<?> type, ValueInput input, Level level,
            EntitySpawnReason reason, CallbackInfoReturnable<Optional<Entity>> callback) {
        callback.getReturnValue().ifPresent(entity -> MonsterMutations.recordSpawnReason(entity, reason));
    }

    @Inject(method = "create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnRequest;)Lnet/minecraft/world/entity/Entity;",
            at = @At("RETURN"))
    private void wildfortune$recordSpawnReason(Level level, EntitySpawnRequest request,
            CallbackInfoReturnable<Entity> callback) {
        Entity entity = callback.getReturnValue();
        if (entity != null) {
            MonsterMutations.recordSpawnReason(entity, request.reason());
        }
    }
}
