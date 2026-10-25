package dev.wildfortune.gear;

public enum GearAffix {
    SHARP("sharp", true, Kind.ATTACK, 0.10),
    EFFICIENT("efficient", true, Kind.MINING, 0.10),
    STURDY("sturdy", true, Kind.ARMOR, 0.10),
    DURABLE("durable", true, Kind.DURABILITY, 0.20),
    BLUNT("blunt", false, Kind.ATTACK, -0.10),
    SLUGGISH("sluggish", false, Kind.MINING, -0.10),
    FLIMSY("flimsy", false, Kind.ARMOR, -0.10),
    FRAGILE("fragile", false, Kind.DURABILITY, -0.20),
    SWIFT("swift", true, Kind.ATTACK_SPEED, 0.10),
    LONG("long", true, Kind.REACH, 0.50),
    LIGHT("light", true, Kind.MOVEMENT, 0.05),
    STEADFAST("steadfast", true, Kind.KNOCKBACK, 0.10),
    HEAVY("heavy", false, Kind.ATTACK_SPEED, -0.10),
    SHORT("short", false, Kind.REACH, -0.50),
    BURDENSOME("burdensome", false, Kind.MOVEMENT, -0.05),
    UNSTABLE("unstable", false, Kind.KNOCKBACK, -0.10),
    LEARNED("learned", true, Kind.EXPERIENCE, 0.10),
    FORGETFUL("forgetful", false, Kind.EXPERIENCE, -0.10),
    VITAL("vital", true, Kind.HEALTH, 1.0),
    FRAIL("frail", false, Kind.HEALTH, -1.0),
    SOARING("soaring", true, Kind.FLIGHT, 0.05),
    DRAGGING("dragging", false, Kind.FLIGHT, -0.05),
    RESILIENT("resilient", true, Kind.RECOVERY, 0.10),
    LINGERING("lingering", false, Kind.RECOVERY, -0.10),
    DEADLY("deadly", true, Kind.RANGED_DAMAGE, 0.10),
    FEEBLE("feeble", false, Kind.RANGED_DAMAGE, -0.10),
    READY("ready", true, Kind.DRAW, 0.10),
    HESITANT("hesitant", false, Kind.DRAW, -0.10),
    PIERCING("piercing", true, Kind.PROJECTILE_SPEED, 0.05),
    FLOATING("floating", false, Kind.PROJECTILE_SPEED, -0.05);

    public enum Kind { ATTACK, MINING, ARMOR, DURABILITY, ATTACK_SPEED, REACH, MOVEMENT, KNOCKBACK,
        EXPERIENCE, HEALTH, FLIGHT, RECOVERY, RANGED_DAMAGE, DRAW, PROJECTILE_SPEED }
    public final String id;
    public final boolean positive;
    public final Kind kind;
    public final double amount;
    public final float chance = (float) dev.wildfortune.config.WildFortuneConfig.DEFAULT_GEAR_CHANCE;

    GearAffix(String id, boolean positive, Kind kind, double amount) {
        this.id = id;
        this.positive = positive;
        this.kind = kind;
        this.amount = amount;
    }

    public static GearAffix byId(String id) {
        for (GearAffix affix : values()) if (affix.id.equals(id)) return affix;
        return null;
    }
}
