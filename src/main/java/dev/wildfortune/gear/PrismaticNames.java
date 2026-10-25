package dev.wildfortune.gear;

import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/** Font IDs mark only our top-tier names; ordinary text never enters the animation path. */
public final class PrismaticNames {
    public static final Identifier LEGENDARY = Identifier.fromNamespaceAndPath("wildfortune", "legendary_prism");
    public static final Identifier CALAMITOUS = Identifier.fromNamespaceAndPath("wildfortune", "calamitous_prism");
    private static final int[] BLUE_WHITE = {0xF7FDFF, 0xBCEEFF, 0x7DC9FF, 0xC8CFFF, 0xE6D6FF, 0xB8F5FF, 0xFFFFFF};
    private static final int[] RED = {0xFF294C, 0xFF6484, 0xFF364D, 0xDC195C, 0xFF6A47, 0xFFA0AA, 0xFF294C};
    private PrismaticNames() {}

    public static Style style(boolean legendary) {
        return Style.EMPTY.withColor(legendary ? 0xE8FAFF : 0xFF294C).withBold(true)
                .withFont(new FontDescription.Resource(legendary ? LEGENDARY : CALAMITOUS));
    }

    public static Style animate(Style original, int character, double seconds) {
        if (!(original.getFont() instanceof FontDescription.Resource font)) return original;
        boolean legendary = font.id().equals(LEGENDARY);
        if (!legendary && !font.id().equals(CALAMITOUS)) return original;
        return original.withColor(color(legendary, character, seconds))
                .withShadowColor(legendary ? 0xFF17364D : 0xFF4A091C);
    }

    public static int color(boolean legendary, int character, double seconds) {
        int[] palette = legendary ? BLUE_WHITE : RED;
        double phase = seconds * 0.28 - character * 0.065;
        double position = (phase - Math.floor(phase)) * palette.length;
        int from = (int) position;
        int base = blend(palette[from], palette[(from + 1) % palette.length], position - from);
        double sweep = Math.pow(0.5 + 0.5 * Math.cos(character * 0.52 - seconds * 2.1), 12);
        double sparkle = Math.pow(0.5 + 0.5 * Math.sin(character * 1.73 + seconds * 2.4), 22);
        // Moving narrow highlights keep the text readable; no global flashing or alpha changes.
        return blend(base, legendary ? 0xFFFFFF : 0xFFE3E7, Math.min(0.7, sweep * 0.45 + sparkle * 0.25));
    }

    private static int blend(int from, int to, double factor) {
        int red = channel(from, to, 16, factor);
        int green = channel(from, to, 8, factor);
        int blue = channel(from, to, 0, factor);
        return red << 16 | green << 8 | blue;
    }
    private static int channel(int from, int to, int shift, double factor) {
        int a = from >> shift & 255;
        int b = to >> shift & 255;
        return (int) Math.round(a + (b - a) * factor);
    }
}
