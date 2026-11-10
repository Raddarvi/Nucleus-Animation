package dev.nucleusanim;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Alle Anime-Girls, sortiert nach Seltenheit.
 *
 * Eingebaut sind eigene Pixel-Art-Figuren. Zusätzlich kannst du eigene Bilder (PNG) in
 *   .minecraft/config/nucleusanimation/girls/<seltenheit>/
 * legen, z.B. config/nucleusanimation/girls/legendary/meinbild.png
 * Ordnernamen: common, uncommon, rare, epic, legendary, mythic.
 * Der Dateiname (ohne .png) wird als Name angezeigt. Neu laden: /nucleusanim reload
 */
public final class GirlRegistry {

    public record Girl(String name, Rarity rarity, Identifier texture, int width, int height) {}

    private static final Map<Rarity, List<Girl>> GIRLS = new EnumMap<>(Rarity.class);
    private static boolean customLoaded;
    private static int customCount;

    static {
        builtin("Hana", Rarity.COMMON, "hana");
        builtin("Nori", Rarity.COMMON, "nori");
        builtin("Sora", Rarity.UNCOMMON, "sora");
        builtin("Kiko", Rarity.UNCOMMON, "kiko");
        builtin("Ruri", Rarity.RARE, "ruri");
        builtin("Shion", Rarity.EPIC, "shion");
        builtin("Kohaku", Rarity.EPIC, "kohaku");
        builtin("Midori", Rarity.LEGENDARY, "midori");
        builtin("Divana", Rarity.MYTHIC, "divana");
    }

    private GirlRegistry() {}

    private static void builtin(String name, Rarity rarity, String file) {
        Identifier tex = Identifier.fromNamespaceAndPath(NucleusAnimationClient.MOD_ID,
                "textures/gui/girls/" + file + ".png");
        add(new Girl(name, rarity, tex, 48, 64));
    }

    private static boolean isCustom(Girl g) {
        return g.texture().getPath().startsWith("custom/");
    }

    private static void add(Girl g) {
        GIRLS.computeIfAbsent(g.rarity(), r -> new ArrayList<>()).add(g);
    }

    /** Zufälliges Girl dieser Seltenheit (fällt auf die nächstniedrigere zurück, falls leer). */
    public static Girl random(Rarity rarity, Random rng) {
        ensureCustomLoaded();
        for (int i = rarity.ordinal(); i >= 0; i--) {
            List<Girl> list = GIRLS.get(Rarity.values()[i]);
            if (list == null || list.isEmpty()) continue;
            // Eigene Bilder ersetzen die eingebauten Figuren dieser Seltenheit
            List<Girl> custom = list.stream().filter(GirlRegistry::isCustom).toList();
            List<Girl> pool = custom.isEmpty() ? list : custom;
            return pool.get(rng.nextInt(pool.size()));
        }
        throw new IllegalStateException("Keine Girls registriert");
    }

    public static void ensureCustomLoaded() {
        if (!customLoaded) {
            customLoaded = true;
            loadCustom();
        }
    }

    /** Lädt eigene Bilder neu. Gibt die Anzahl geladener Bilder zurück. */
    public static int reload() {
        for (List<Girl> list : GIRLS.values()) list.removeIf(GirlRegistry::isCustom);
        customLoaded = true;
        loadCustom();
        return customCount;
    }

    private static void loadCustom() {
        customCount = 0;
        Path root = FabricLoader.getInstance().getConfigDir().resolve("nucleusanimation").resolve("girls");
        try {
            for (Rarity r : Rarity.values()) {
                Files.createDirectories(root.resolve(r.name().toLowerCase(Locale.ROOT)));
            }
        } catch (Exception ignored) {
        }

        Minecraft mc = Minecraft.getInstance();
        for (Rarity r : Rarity.values()) {
            Path dir = root.resolve(r.name().toLowerCase(Locale.ROOT));
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.list(dir)) {
                for (Path p : files.sorted().toList()) {
                    String fn = p.getFileName().toString();
                    if (!fn.toLowerCase(Locale.ROOT).endsWith(".png")) continue;
                    try (InputStream in = Files.newInputStream(p)) {
                        NativeImage img = NativeImage.read(in);
                        int w = img.getWidth(), h = img.getHeight();
                        String clean = fn.substring(0, fn.length() - 4);
                        String idPath = "custom/" + r.name().toLowerCase(Locale.ROOT) + "/"
                                + clean.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
                        Identifier id = Identifier.fromNamespaceAndPath(NucleusAnimationClient.MOD_ID, idPath);
                        mc.getTextureManager().register(id, new DynamicTexture(() -> "nucleusanim " + clean, img));
                        add(new Girl(clean, r, id, w, h));
                        customCount++;
                    } catch (Exception e) {
                        System.err.println("[NucleusAnimation] Konnte Bild nicht laden: " + p + " (" + e + ")");
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }
}
