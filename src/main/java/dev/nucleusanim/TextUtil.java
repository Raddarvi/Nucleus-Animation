package dev.nucleusanim;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Kleine Helfer für Chat-Komponenten. Farben/Styles von Hypixel bleiben erhalten. */
final class TextUtil {

    private TextUtil() {}

    /** Entfernt alte §-Farbcodes aus einem String. */
    static String stripFormatting(String s) {
        return s.replaceAll("§.", "");
    }

    /** true, wenn die Zeile nur aus ▬ (oder ähnlichen Trennzeichen) besteht. */
    static boolean isSeparatorBar(String plain) {
        if (plain.length() < 8) return false;
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            if (c != '▬' && c != '-' && c != '=' && c != '━' && !Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }

    /** Teilt eine (evtl. mehrzeilige) Komponente in einzelne Zeilen, Styles bleiben erhalten. */
    static List<Component> splitLines(Component message) {
        List<Component> lines = new ArrayList<>();
        MutableComponent[] current = { Component.empty() };

        message.<Object>visit((style, text) -> {
            String[] parts = text.split("\n", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    lines.add(current[0]);
                    current[0] = Component.empty();
                }
                if (!parts[i].isEmpty()) {
                    current[0].append(Component.literal(parts[i]).setStyle(style));
                }
            }
            return Optional.empty();
        }, Style.EMPTY);

        lines.add(current[0]);
        return lines;
    }

    /** Entfernt führende Leerzeichen, lässt Farben (auch §-Codes) aber stehen. */
    static Component trimLeadingWhitespace(Component line) {
        MutableComponent out = Component.empty();
        boolean[] started = { false };

        line.<Object>visit((style, text) -> {
            String piece = text;
            if (!started[0]) {
                StringBuilder kept = new StringBuilder();
                int i = 0;
                while (i < piece.length()) {
                    char c = piece.charAt(i);
                    if (c == '§' && i + 1 < piece.length()) {
                        kept.append(c).append(piece.charAt(i + 1)); // Farbcode behalten
                        i += 2;
                    } else if (Character.isWhitespace(c)) {
                        i++;
                    } else {
                        started[0] = true;
                        break;
                    }
                }
                piece = kept + piece.substring(i);
            }
            if (!piece.isEmpty()) {
                out.append(Component.literal(piece).setStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);

        return out;
    }
}
