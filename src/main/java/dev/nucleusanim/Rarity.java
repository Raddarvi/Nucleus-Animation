package dev.nucleusanim;

/** Seltenheiten der Anime-Girls (Farben wie in Hypixel SkyBlock). */
public enum Rarity {
    COMMON("GEWÖHNLICH", 0xFFFFFF, 45),
    UNCOMMON("UNGEWÖHNLICH", 0x55FF55, 25),
    RARE("SELTEN", 0x5577FF, 15),
    EPIC("EPISCH", 0xB24DFF, 9),
    LEGENDARY("LEGENDÄR", 0xFFAA00, 4.5),
    MYTHIC("MYTHISCH", 0xFF55FF, 1.5);

    public final String label;
    public final int color;
    /** Gewicht für die "Füll"-Karten auf der Spin-Rolle. */
    public final double weight;

    Rarity(String label, int color, double weight) {
        this.label = label;
        this.color = color;
        this.weight = weight;
    }

    public boolean atLeast(Rarity other) {
        return ordinal() >= other.ordinal();
    }

    public Rarity next() {
        return values()[Math.min(values().length - 1, ordinal() + 1)];
    }

    public static Rarity byName(String s) {
        for (Rarity r : values()) if (r.name().equalsIgnoreCase(s)) return r;
        return null;
    }
}
