package dev.wildfortune.gear;

import dev.wildfortune.mixin.GearArrowAccess;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class GearActions {
    private GearActions() {}
    public static double durationFactor(ItemStack stack, GearAffix.Kind kind) {
        return Math.max(0.1, 1 - GearAffixes.net(stack, kind));
    }
    public static int bowDrawTicks(ItemStack bow, int elapsed) {
        return (int) Math.min(Integer.MAX_VALUE, Math.floor((elapsed + 0.000001) / durationFactor(bow, GearAffix.Kind.DRAW)));
    }
    public static int chargeTicks(ItemStack crossbow, int vanilla) {
        return Math.max(1, (int) Math.round(vanilla * durationFactor(crossbow, GearAffix.Kind.DRAW)));
    }
    public static float projectileSpeed(ItemStack weapon, float vanilla) {
        return (float) (vanilla * (1 + GearAffixes.net(weapon, GearAffix.Kind.PROJECTILE_SPEED)));
    }
    public static Projectile prepareProjectile(Projectile projectile, ItemStack weapon) {
        double bonus = GearAffixes.net(weapon, GearAffix.Kind.RANGED_DAMAGE);
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setBaseDamage(((GearArrowAccess) arrow).wildfortune$getBaseDamage() * (1 + bonus));
        } else if (projectile instanceof GearFireworkData rocket) {
            rocket.wildfortune$setDamageBonus(bonus);
        }
        return projectile;
    }
    public static Vec3 flightMovement(LivingEntity wearer, Vec3 vanilla) {
        ItemStack chest = wearer.getItemBySlot(EquipmentSlot.CHEST);
        if (GearAffixes.category(chest) != GearAffixes.Category.GLIDER) return vanilla;
        double factor = 1 + GearAffixes.net(chest, GearAffix.Kind.FLIGHT);
        // Scale displacement, not stored velocity: no compounding acceleration on later ticks.
        return vanilla.multiply(factor, 1, factor);
    }
    public static float shieldDisableSeconds(LivingEntity wearer, ItemStack shield, float vanilla) {
        return wearer.getOffhandItem() == shield
                ? (float) (vanilla * durationFactor(shield, GearAffix.Kind.RECOVERY)) : vanilla;
    }
}
