package dev.nucleusanim;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Wertet die Reward-Zeilen eines Runs aus: welche besonderen Items sind dabei? */
public final class RunResult {

    public enum Special {
        DIVANS_ALLOY("Divan's Alloy", Rarity.MYTHIC, "divans_alloy"),
        JADE_DYE("Jade Dye", Rarity.LEGENDARY, "jade_dye"),
        JADERALD("Jaderald", Rarity.LEGENDARY, "jaderald"),
        QUICK_CLAW("Quick Claw", Rarity.EPIC, "quick_claw"),
        FLAWLESS_GEMSTONE("Flawless Gemstone", Rarity.EPIC, null),
        DIVAN_FRAGMENT("Divan Fragment", Rarity.RARE, "divan_fragment");

        public final String label;
        public final Rarity rarity;
        final String icon;

        Special(String label, Rarity rarity, String icon) {
            this.label = label;
            this.rarity = rarity;
            this.icon = icon;
        }
    }

    /** Ein Item, das um das Girl kreist. */
    public record Entry(Component text, Identifier icon, Rarity rarity, Special special) {}

    private static final Pattern GEM_TYPE = Pattern.compile("(?i)(?:flawless|fine|flawed|rough)?\\s*(ruby|amber|amethyst|jade|sapphire|topaz|jasper|opal|onyx|aquamarine|citrine|peridot)\\s+gemstone");

    public final List<Entry> specials = new ArrayList<>();
    public final List<Entry> others = new ArrayList<>();
    public final Rarity best;

    public RunResult(List<Component> rewards) {
        for (Component c : rewards) {
            String plain = TextUtil.stripFormatting(c.getString()).toLowerCase(Locale.ROOT);
            Special sp = detect(plain);
            if (sp != null) {
                Identifier icon = sp == Special.FLAWLESS_GEMSTONE ? gemIcon(plain, true) : icon(sp.icon);
                specials.add(new Entry(c, icon, sp.rarity, sp));
            } else {
                others.add(new Entry(c, iconForOther(plain), Rarity.COMMON, null));
            }
        }
        specials.sort(Comparator.comparingInt((Entry e) -> e.rarity().ordinal()).reversed());
        best = specials.isEmpty() ? null : specials.get(0).rarity();
    }

    public boolean has(Special s) {
        for (Entry e : specials) if (e.special() == s) return true;
        return false;
    }

    /** Welche Seltenheit bekommt das Girl? Ohne besondere Items: Common oder Uncommon. */
    public Rarity girlRarity(java.util.Random rng) {
        if (best != null) return best;
        return rng.nextFloat() < 0.35f ? Rarity.UNCOMMON : Rarity.COMMON;
    }

    /** Was kreist um das Girl: die besonderen Items, sonst die normalen (max. 10). */
    public List<Entry> orbitItems() {
        List<Entry> list = specials.isEmpty() ? others : specials;
        return list.size() > 10 ? list.subList(0, 10) : list;
    }

    public int notShownCount() {
        return specials.size() + others.size() - orbitItems().size();
    }

    static Special detect(String p) {
        if (p.contains("divan's alloy") || p.contains("divans alloy") || p.contains("divan alloy")) return Special.DIVANS_ALLOY;
        if (p.contains("jade dye")) return Special.JADE_DYE;
        if (p.contains("jaderald")) return Special.JADERALD;
        if (p.contains("quick claw")) return Special.QUICK_CLAW;
        if (p.contains("flawless") && p.contains("gemstone")) return Special.FLAWLESS_GEMSTONE;
        if (p.contains("divan fragment") || p.contains("divan's fragment")) return Special.DIVAN_FRAGMENT;
        return null;
    }

    private static Identifier iconForOther(String p) {
        if (p.contains("powder")) return icon("powder");
        if (p.contains("gemstone")) return gemIcon(p, false);
        return icon("generic");
    }

    private static Identifier gemIcon(String plain, boolean flawless) {
        Matcher m = GEM_TYPE.matcher(plain);
        String type = m.find() ? m.group(1).toLowerCase(Locale.ROOT) : "ruby";
        return icon((flawless ? "flawless_" : "gem_") + type);
    }

    static Identifier icon(String name) {
        return Identifier.fromNamespaceAndPath(NucleusAnimationClient.MOD_ID, "textures/gui/icons/" + name + ".png");
    }
}
