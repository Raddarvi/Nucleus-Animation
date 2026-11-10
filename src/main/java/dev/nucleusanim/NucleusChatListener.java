package dev.nucleusanim;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Erkennt das Ende eines Nucleus-Runs im Chat und sammelt die Rewards.
 *
 * Hypixel schickt nach dem 5. Crystal ungefähr das hier:
 *
 *   ✦ You placed the Topaz Crystal! You've earned a Crystal Loot Bundle!
 *   ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬
 *     CRYSTAL NUCLEUS LOOT BUNDLE
 *
 *     REWARDS
 *       <Reward>
 *       <Reward>
 *       ...
 *       Gemstone Powder <Menge>
 *   ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬
 *   Pick it up near the Nucleus Vault!
 *
 * Funktioniert egal ob Hypixel das als viele einzelne Zeilen oder als eine
 * mehrzeilige Nachricht schickt.
 */
final class NucleusChatListener {

    /** So lange nach der letzten Reward-Zeile warten, falls die Abschlusslinie fehlt. */
    private static final long COLLECT_TIMEOUT_MS = 1500;
    /** Fallback: "You've earned a Crystal Loot Bundle" gesehen, aber kein Reward-Block. */
    private static final long FALLBACK_DELAY_MS = 3000;

    private static final Pattern PLACED =
            Pattern.compile("You placed the (\\w+) Crystal!", Pattern.CASE_INSENSITIVE);

    private enum State { IDLE, COLLECTING }

    private State state = State.IDLE;
    private final List<Component> rewards = new ArrayList<>();
    private final Set<String> placedCrystals = new LinkedHashSet<>();
    private long lastLineAt;
    private long earnedAt = -1;

    void onMessage(Component message) {
        for (Component line : TextUtil.splitLines(message)) {
            handleLine(line);
        }
    }

    private void handleLine(Component line) {
        long now = System.currentTimeMillis();
        String plain = TextUtil.stripFormatting(line.getString()).trim();

        if (state == State.COLLECTING) {
            lastLineAt = now;
            if (plain.isEmpty()) return;
            if (TextUtil.isSeparatorBar(plain)) {
                if (!rewards.isEmpty()) finish();
                return;
            }
            if (plain.equalsIgnoreCase("REWARDS")) return;
            if (plain.toLowerCase(Locale.ROOT).startsWith("pick it up")) {
                finish();
                return;
            }
            rewards.add(TextUtil.trimLeadingWhitespace(line));
            return;
        }

        String upper = plain.toUpperCase(Locale.ROOT);
        if (upper.contains("CRYSTAL NUCLEUS LOOT BUNDLE")) {
            state = State.COLLECTING;
            rewards.clear();
            lastLineAt = now;
            earnedAt = -1;
            return;
        }

        Matcher m = PLACED.matcher(plain);
        if (m.find()) {
            placedCrystals.add(capitalize(m.group(1)));
            if (upper.contains("LOOT BUNDLE")) {
                earnedAt = now; // letzter Crystal -> Reward-Block sollte gleich kommen
            }
        }
    }

    /** Wird jeden Client-Tick aufgerufen. */
    void tick() {
        long now = System.currentTimeMillis();

        if (state == State.COLLECTING && now - lastLineAt > COLLECT_TIMEOUT_MS) {
            if (rewards.isEmpty()) {
                state = State.IDLE;
            } else {
                finish();
            }
        }

        if (state == State.IDLE && earnedAt > 0 && now - earnedAt > FALLBACK_DELAY_MS) {
            // Reward-Liste kam nicht an (z.B. von einer anderen Mod versteckt) –
            // trotzdem die Animation zeigen.
            earnedAt = -1;
            RewardAnimation.INSTANCE.start(List.of(), new ArrayList<>(placedCrystals));
            placedCrystals.clear();
        }
    }

    private void finish() {
        RewardAnimation.INSTANCE.start(new ArrayList<>(rewards), new ArrayList<>(placedCrystals));
        rewards.clear();
        placedCrystals.clear();
        earnedAt = -1;
        state = State.IDLE;
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
