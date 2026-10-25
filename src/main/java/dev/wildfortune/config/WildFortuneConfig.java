package dev.wildfortune.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.wildfortune.WildFortune;
import dev.wildfortune.gear.GearAffix;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

/** Server settings affect future rolls. Existing gear reads its own saved strength values. */
public final class WildFortuneConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final double DEFAULT_MONSTER_CHANCE = 0.35;
    public static final double DEFAULT_GEAR_CHANCE = 0.30;
    public static final java.util.List<String> MONSTER_EFFECT_IDS = java.util.List.of("speed", "resistance", "strength", "health");
    public record MonsterRule(boolean enabled, double chance, int level, double amount) {}
    public record GearRule(boolean enabled, double chance, double amount) {}
    public record Settings(boolean monstersEnabled, Set<String> excludedEntities, Map<String, MonsterRule> monsters,
            boolean gearEnabled, Map<GearAffix, GearRule> gear) {
        public Settings {
            excludedEntities = Set.copyOf(excludedEntities);
            monsters = Map.copyOf(monsters);
            gear = Map.copyOf(gear);
        }
    }
    private static volatile Settings current = defaults();
    private WildFortuneConfig() {}

    public static Settings get() { return current; }
    // Package access also lets isolated game tests restore the prior settings in a finally block.
    static void install(Settings settings) { current = settings; }
    public static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("wildfortune.json"); }

    public static Settings defaults() {
        Map<String, MonsterRule> monsters = new LinkedHashMap<>();
        for (String id : MONSTER_EFFECT_IDS) monsters.put(id, new MonsterRule(true, DEFAULT_MONSTER_CHANCE, 1, 0.25));
        Map<GearAffix, GearRule> gear = new EnumMap<>(GearAffix.class);
        for (GearAffix effect : GearAffix.values()) gear.put(effect, new GearRule(true, DEFAULT_GEAR_CHANCE, effect.amount));
        return new Settings(true, Set.of("minecraft:wither", "minecraft:ender_dragon"), monsters, true, gear);
    }

    public static boolean load(Path path) {
        try {
            if (!Files.exists(path)) {
                Path parent = path.toAbsolutePath().getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.writeString(path, toJson(defaults()) + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }
            String original = Files.readString(path, StandardCharsets.UTF_8);
            Settings parsed = parse(original);
            JsonObject document = JsonParser.parseString(original).getAsJsonObject();
            int oldSchema = document.has("schemaVersion") ? document.get("schemaVersion").getAsInt() : 1;
            if (oldSchema < 3) {
                Path backup = path.resolveSibling(path.getFileName() + ".v" + oldSchema + ".bak");
                if (!Files.exists(backup)) Files.copy(path, backup);
                Files.writeString(path, toJson(parsed) + System.lineSeparator(), StandardCharsets.UTF_8);
                WildFortune.LOGGER.info("Upgraded settings to schema 3; original preserved in {}", backup);
            }
            current = parsed; // Publish only after the complete file has passed validation.
            WildFortune.LOGGER.info("Loaded Wild Fortune settings from {}", path.toAbsolutePath());
            return true;
        } catch (Exception exception) {
            WildFortune.LOGGER.warn("Could not load {}: {}. Previous settings retained; file left unchanged.",
                    path.toAbsolutePath(), exception.getMessage());
            return false;
        }
    }

    public static Settings parse(String json) {
        JsonObject root = requireObject(JsonParser.parseString(json), "root");
        keys(root, "root", Set.of("schemaVersion", "monsters", "gear"));
        double schema = number(root, "schemaVersion", 1, 1, 3, "root");
        if (schema != Math.rint(schema)) throw new IllegalArgumentException("schemaVersion must be a whole number");
        Settings defaults = defaults();
        JsonObject monsters = object(root, "monsters", "root");
        keys(monsters, "monsters", Set.of("enabled", "excludedEntities", "effects"));
        Set<String> excluded = new LinkedHashSet<>(defaults.excludedEntities());
        if (monsters.has("excludedEntities")) {
            JsonElement value = monsters.get("excludedEntities");
            if (!value.isJsonArray()) throw new IllegalArgumentException("monsters.excludedEntities must be an array");
            excluded.clear();
            for (JsonElement entry : value.getAsJsonArray()) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString())
                    throw new IllegalArgumentException("excludedEntities entries must be entity IDs");
                String id = entry.getAsString();
                if (!id.contains(":")) throw new IllegalArgumentException("Use namespace:path entity IDs: " + id);
                Identifier.parse(id); // Also supports identifiers from other mods.
                excluded.add(id);
            }
        }
        Map<String, MonsterRule> monsterRules = new LinkedHashMap<>();
        JsonObject monsterEffects = object(monsters, "effects", "monsters");
        if (schema < 3 && monsterEffects.has("invisibility")) {
            if (monsterEffects.has("health")) throw new IllegalArgumentException("Use only health or legacy invisibility");
            JsonObject legacy = requireObject(monsterEffects.remove("invisibility"), "invisibility");
            keys(legacy, "invisibility", Set.of("enabled", "chance", "level"));
            double oldLevel = number(legacy, "level", 1, 1, 10, "invisibility");
            if (oldLevel != Math.rint(oldLevel)) throw new IllegalArgumentException("invisibility.level must be a whole number");
            legacy.remove("level");
            monsterEffects.add("health", legacy);
        }
        keys(monsterEffects, "monsters.effects", Set.copyOf(MONSTER_EFFECT_IDS));
        for (String id : MONSTER_EFFECT_IDS) {
            JsonObject rule = object(monsterEffects, id, "monsters.effects");
            String location = "monsters.effects." + id;
            keys(rule, location, id.equals("health") ? Set.of("enabled", "chance", "amount") : Set.of("enabled", "chance", "level"));
            double level = number(rule, "level", 1, 1, 10, location);
            if (level != Math.rint(level)) throw new IllegalArgumentException(location + ".level must be a whole number");
            monsterRules.put(id, new MonsterRule(bool(rule, "enabled", true, location),
                    number(rule, "chance", DEFAULT_MONSTER_CHANCE, 0, 1, location), (int) level,
                    id.equals("health") ? number(rule, "amount", 0.25, 0, 5, location) : 0.25));
        }
        JsonObject gear = object(root, "gear", "root");
        keys(gear, "gear", Set.of("enabled", "effects"));
        JsonObject gearEffects = object(gear, "effects", "gear");
        Set<String> gearIds = new LinkedHashSet<>();
        for (GearAffix effect : GearAffix.values()) gearIds.add(effect.id);
        keys(gearEffects, "gear.effects", gearIds);
        Map<GearAffix, GearRule> gearRules = new EnumMap<>(GearAffix.class);
        for (GearAffix effect : GearAffix.values()) {
            String location = "gear.effects." + effect.id;
            JsonObject rule = object(gearEffects, effect.id, "gear.effects");
            keys(rule, location, Set.of("enabled", "chance", "amount"));
            double amount = number(rule, "amount", effect.amount, -20, 20, location);
            if (!validAmount(effect, amount)) throw new IllegalArgumentException(location + ".amount has an invalid sign or range");
            gearRules.put(effect, new GearRule(bool(rule, "enabled", true, location),
                    number(rule, "chance", DEFAULT_GEAR_CHANCE, 0, 1, location), amount));
        }
        return new Settings(bool(monsters, "enabled", true, "monsters"), excluded, monsterRules,
                bool(gear, "enabled", true, "gear"), gearRules);
    }

    public static boolean validAmount(GearAffix effect, double amount) {
        if (!Double.isFinite(amount) || (effect.positive ? amount <= 0 : amount >= 0)) return false;
        if (effect.kind == GearAffix.Kind.REACH) return amount >= -2.5 && amount <= 8;
        if (effect.kind == GearAffix.Kind.HEALTH) return amount >= -2 && amount <= 20;
        if (effect.kind == GearAffix.Kind.DRAW || effect.kind == GearAffix.Kind.RECOVERY) return amount >= -0.95 && amount <= 0.9;
        return amount >= -0.95 && amount <= 5;
    }

    public static String toJson(Settings settings) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 3);
        JsonObject monsters = new JsonObject();
        monsters.addProperty("enabled", settings.monstersEnabled());
        var excluded = new com.google.gson.JsonArray();
        settings.excludedEntities().stream().sorted().forEach(excluded::add);
        monsters.add("excludedEntities", excluded);
        JsonObject monsterEffects = new JsonObject();
        for (String id : MONSTER_EFFECT_IDS) {
            MonsterRule rule = settings.monsters().get(id);
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", rule.enabled());
            entry.addProperty("chance", rule.chance());
            if (id.equals("health")) entry.addProperty("amount", rule.amount());
            else entry.addProperty("level", rule.level());
            monsterEffects.add(id, entry);
        }
        monsters.add("effects", monsterEffects);
        root.add("monsters", monsters);
        JsonObject gear = new JsonObject();
        gear.addProperty("enabled", settings.gearEnabled());
        JsonObject gearEffects = new JsonObject();
        for (GearAffix effect : GearAffix.values()) {
            GearRule rule = settings.gear().get(effect);
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", rule.enabled());
            entry.addProperty("chance", rule.chance());
            entry.addProperty("amount", rule.amount());
            gearEffects.add(effect.id, entry);
        }
        gear.add("effects", gearEffects);
        root.add("gear", gear);
        return GSON.toJson(root);
    }

    private static JsonObject requireObject(JsonElement value, String path) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(path + " must be an object");
        return value.getAsJsonObject();
    }
    private static JsonObject object(JsonObject parent, String key, String path) {
        return parent.has(key) ? requireObject(parent.get(key), path + "." + key) : new JsonObject();
    }
    private static void keys(JsonObject object, String path, Set<String> allowed) {
        for (String key : object.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown setting: " + path + "." + key);
    }
    private static boolean bool(JsonObject object, String key, boolean fallback, String path) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(path + "." + key + " must be true/false");
        return value.getAsBoolean();
    }
    private static double number(JsonObject object, String key, double fallback, double min, double max, String path) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(path + "." + key + " must be a number");
        double number = value.getAsDouble();
        if (!Double.isFinite(number) || number < min || number > max) throw new IllegalArgumentException(path + "." + key + " must be between " + min + " and " + max);
        return number;
    }
}
