package dev.wildfortune;

import net.fabricmc.api.ModInitializer;
import dev.wildfortune.monster.MonsterMutations;
import dev.wildfortune.config.WildFortuneConfig;
import dev.wildfortune.config.ConfigCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WildFortune implements ModInitializer {
    public static final String MOD_ID = "wildfortune";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        WildFortuneConfig.load(WildFortuneConfig.path());
        ConfigCommands.register();
        MonsterMutations.register();
        dev.wildfortune.gear.GearAcquisition.register();
        LOGGER.info("Wild Fortune initialized. Monster and crafted gear rolls use config/wildfortune.json.");
    }
}
