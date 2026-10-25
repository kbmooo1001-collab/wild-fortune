package dev.wildfortune;

import dev.wildfortune.gear.GearAffixes;
import dev.wildfortune.gear.PrismaticNames;
import java.util.HashSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;

public final class PrismaticNamesGameTest {
    @GameTest
    public void prismMovesAcrossCharactersAndStaysInItsPalette(GameTestHelper test) {
        for (boolean legendary : new boolean[] {true, false}) {
            var colors = new HashSet<Integer>();
            for (int frame = 0; frame < 180; frame++) {
                double time = frame / 60.0;
                for (int character = 0; character < 24; character++) {
                    int color = PrismaticNames.color(legendary, character, time);
                    int red = color >> 16 & 255;
                    int green = color >> 8 & 255;
                    int blue = color & 255;
                    require(legendary ? blue >= red && blue >= green : red >= green && red >= blue,
                            "Prism escaped its blue-white/red palette");
                    colors.add(color);
                    int next = PrismaticNames.color(legendary, character, time + 1.0 / 60);
                    for (int shift : new int[] {0, 8, 16}) {
                        require(Math.abs((color >> shift & 255) - (next >> shift & 255)) < 40, "Abrupt per-frame flashing");
                    }
                }
            }
            require(colors.size() > 100, "Prism color is static");
            require(PrismaticNames.color(legendary, 0, 1) != PrismaticNames.color(legendary, 8, 1), "No character gradient");
        }
        test.succeed();
    }

    @GameTest
    public void onlyTopTierFontMarkersAnimateAndStyleSurvivesEncoding(GameTestHelper test) {
        for (Style ordinary : new Style[] {Style.EMPTY, GearAffixes.nameStyle(3, 3), GearAffixes.nameStyle(4, 0),
                Style.EMPTY.withColor(0xE8FAFF).withBold(true)}) {
            require(PrismaticNames.animate(ordinary, 3, 1) == ordinary, "Unrelated text was animated");
        }
        for (boolean legendary : new boolean[] {true, false}) {
            var base = GearAffixes.nameStyle(5, legendary ? 5 : 0);
            var encoded = FontDescription.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, base.getFont()).getOrThrow();
            var decoded = FontDescription.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow();
            require(decoded.equals(base.getFont()), "Prism font marker lost in codec");
            var animated = PrismaticNames.animate(base, 3, 1);
            require(animated.isBold() && !animated.isObfuscated() && animated.getFont().equals(base.getFont()), "Animation changed glyphs or font");
            require(!animated.getColor().equals(base.getColor()), "Animation did not change color");
            require(animated.equals(PrismaticNames.animate(base, 3, 1)), "Animation at a fixed time is nondeterministic");
        }
        test.succeed();
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
