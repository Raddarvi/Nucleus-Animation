package dev.nucleusanim;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Case-Opening-Animation nach einem Nucleus-Run.
 *
 *  1. SPIN:     Eine große Rolle mit Anime-Girl-Karten fährt von links nach rechts
 *               und bremst langsam ab (Tick-Sound bei jeder Karte).
 *  2. REVEAL:   Die Gewinner-Karte leuchtet in ihrer Seltenheit auf, wird groß
 *               und wandert in die Mitte (Blitz, Partikel, Wackeln, Sounds).
 *  3. SHOWCASE: Die besten Items vom Run kreisen um das Girl.
 *               Keine besonderen Items? Dann kreisen die normalen Rewards.
 *               Divan's Alloy: Regenbogen, Feuerwerk, Sternenregen, alles.
 *  4. OUTRO:    Ausblenden.
 */
public final class SpinAnimation {

    public static final SpinAnimation INSTANCE = new SpinAnimation();

    // ---- Timing (ms) ----
    private static final long INTRO = 500;
    private static final long SPIN = 6200;
    private static final long REVEAL = 1100;
    private static final long SHOW = 9000;
    private static final long SHOW_MYTHIC = 13000;
    private static final long OUTRO = 700;

    // ---- Karten-Layout (GUI-Pixel) ----
    private static final int CW = 80, CH = 112, GAP = 6, STEP = CW + GAP;
    private static final int WINNER_INDEX = 34, REEL_LENGTH = 48;

    private static final int[] RAINBOW = {0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5577FF, 0xFF55FF};

    private record Card(GirlRegistry.Girl girl, Rarity rarity) {}

    private static final class Particle {
        float x, y, vx, vy, gravity, size;
        long age, life;
        int color, kind; // 0 = Funke, 1 = Stern, 2 = Konfetti
    }

    private final Random rng = new Random();
    private final List<Card> reel = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();

    private RunResult result;
    private Card winner;
    private float endShift;
    private long startedAt = -1;
    private long lastFrame;

    // Sound-/Effekt-Status
    private int lastCrossing;
    private boolean stopFired, showFired;
    private int itemsPopped;
    private long nextFirework;
    private int fireworksLeft;
    private long shakeStart, shakeDur;
    private float shakeAmp;

    private SpinAnimation() {}

    // =================================================================== Steuerung

    public void start(List<Component> rewards) {
        result = new RunResult(rewards);
        Rarity winRarity = result.girlRarity(rng);

        reel.clear();
        for (int i = 0; i < REEL_LENGTH; i++) {
            Rarity r = i == WINNER_INDEX ? winRarity : randomFillerRarity();
            reel.add(new Card(GirlRegistry.random(r, rng), r));
        }
        // "Knapp daneben": direkt nach dem Gewinner kommt oft etwas Selteneres
        if (winRarity != Rarity.MYTHIC && rng.nextFloat() < 0.55f) {
            Rarity tease = winRarity.next();
            reel.set(WINNER_INDEX + 1, new Card(GirlRegistry.random(tease, rng), tease));
        }
        winner = reel.get(WINNER_INDEX);
        endShift = WINNER_INDEX * STEP + (rng.nextFloat() - 0.5f) * STEP * 0.7f;

        particles.clear();
        startedAt = System.currentTimeMillis();
        lastFrame = startedAt;
        lastCrossing = 0;
        stopFired = showFired = false;
        itemsPopped = 0;
        fireworksLeft = 0;
        shakeDur = 0;
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1f);
        play(SoundEvents.BEACON_POWER_SELECT, 1.4f, 0.6f);
    }

    public void stop() {
        startedAt = -1;
        particles.clear();
    }

    public boolean isActive() {
        return startedAt >= 0;
    }

    private Rarity randomFillerRarity() {
        double total = 0;
        for (Rarity r : Rarity.values()) total += r.weight;
        double x = rng.nextDouble() * total;
        for (Rarity r : Rarity.values()) {
            x -= r.weight;
            if (x <= 0) return r;
        }
        return Rarity.COMMON;
    }

    private boolean mythic() {
        return winner != null && winner.rarity() == Rarity.MYTHIC;
    }

    private long tStop() { return INTRO + SPIN; }
    private long tShow() { return tStop() + REVEAL; }
    private long showLength() { return mythic() ? SHOW_MYTHIC : SHOW; }
    private long total() { return tShow() + showLength() + OUTRO; }

    private float spinShift(long t) {
        float p = clamp((t - INTRO) / (float) SPIN);
        return endShift * easeOutQuint(p);
    }

    // =================================================================== Tick: Sounds & Ereignisse

    void tick(Minecraft mc) {
        if (!isActive()) return;
        long t = System.currentTimeMillis() - startedAt;
        if (t > total()) {
            stop();
            return;
        }
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int cx = sw / 2, cy = sh / 2;

        // --- Tick-Geräusch bei jeder vorbeifahrenden Karte ---
        if (t >= INTRO && t < tStop()) {
            int crossing = (int) Math.floor((spinShift(t) + STEP / 2f) / STEP);
            if (crossing != lastCrossing) {
                lastCrossing = crossing;
                float p = clamp((t - INTRO) / (float) SPIN);
                playH(SoundEvents.NOTE_BLOCK_HAT, 0.9f + 0.9f * p, 0.7f);
                if (p > 0.8f) playH(SoundEvents.NOTE_BLOCK_BASEDRUM, 0.6f + p * 0.4f, 0.4f);
            }
        }

        // --- Stopp: Gewinner ---
        if (!stopFired && t >= tStop()) {
            stopFired = true;
            onStop(cx, cy);
        }

        // --- Showcase: Items erscheinen nacheinander ---
        if (t >= tShow()) {
            if (!showFired) {
                showFired = true;
                play(SoundEvents.ENCHANTMENT_TABLE_USE, 1.2f, 1f);
            }
            List<RunResult.Entry> items = result.orbitItems();
            int due = (int) Math.min(items.size(), (t - tShow() - 200) / 260 + 1);
            while (itemsPopped < due && t - tShow() >= 200) {
                RunResult.Entry e = items.get(itemsPopped);
                onItemPop(e, itemsPopped, cx, cy);
                itemsPopped++;
            }
        }

        // --- Feuerwerk (Legendär / Mythisch) ---
        if (fireworksLeft > 0 && System.currentTimeMillis() >= nextFirework) {
            fireworksLeft--;
            nextFirework = System.currentTimeMillis() + 350 + rng.nextInt(350);
            float fx = sw * (0.12f + rng.nextFloat() * 0.76f);
            float fy = sh * (0.12f + rng.nextFloat() * 0.45f);
            int col = mythic() ? RAINBOW[rng.nextInt(RAINBOW.length)] : (rng.nextBoolean() ? 0xFFAA00 : 0x55FF55);
            burst(fx, fy, 45, col, 90, 0);
            play(rng.nextBoolean() ? SoundEvents.FIREWORK_ROCKET_BLAST : SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                    0.8f + rng.nextFloat() * 0.4f, 0.8f);
            if (rng.nextBoolean()) play(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1f, 0.6f);
        }

        // --- Dauer-Effekte ---
        if (t >= tStop()) {
            Rarity r = winner.rarity();
            if (r.atLeast(Rarity.RARE) && rng.nextFloat() < 0.6f) sparkleAround(cx, cy, r.color);
            if (r.atLeast(Rarity.LEGENDARY) && t < tShow() + showLength()) {
                int n = r == Rarity.MYTHIC ? 4 : 2;
                for (int i = 0; i < n; i++) confetti(sw, r == Rarity.MYTHIC
                        ? RAINBOW[rng.nextInt(RAINBOW.length)] : (rng.nextBoolean() ? 0xFFAA00 : 0xFFFF88));
            }
        }
    }

    private void onStop(int cx, int cy) {
        Rarity r = winner.rarity();
        flashStart = System.currentTimeMillis();
        switch (r) {
            case COMMON -> play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1f);
            case UNCOMMON -> play(SoundEvents.PLAYER_LEVELUP, 1.3f, 0.8f);
            case RARE -> {
                play(SoundEvents.PLAYER_LEVELUP, 1.1f, 1f);
                play(SoundEvents.AMETHYST_CLUSTER_BREAK, 1f, 1f);
                shake(3f, 400);
            }
            case EPIC -> {
                play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                play(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1f, 1f);
                shake(5f, 600);
            }
            case LEGENDARY -> {
                play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                play(SoundEvents.BEACON_ACTIVATE, 1.2f, 1f);
                play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1f, 1f);
                shake(8f, 900);
                fireworksLeft = 8;
                nextFirework = System.currentTimeMillis() + 500;
            }
            case MYTHIC -> {
                play(SoundEvents.ENDER_DRAGON_GROWL, 1.2f, 0.8f);
                play(SoundEvents.TOTEM_USE, 1f, 1f);
                play(SoundEvents.END_PORTAL_SPAWN, 1.3f, 0.7f);
                shake(12f, 1500);
                fireworksLeft = 22;
                nextFirework = System.currentTimeMillis() + 300;
            }
        }
        int count = switch (r) {
            case COMMON -> 20; case UNCOMMON -> 35; case RARE -> 55;
            case EPIC -> 80; case LEGENDARY -> 120; case MYTHIC -> 200;
        };
        int col = r.color;
        burst(cx, cy, count, col, 160, 1);
        if (r == Rarity.MYTHIC) {
            for (int c : RAINBOW) burst(cx, cy, 25, c, 220, 0);
        }
    }

    private void onItemPop(RunResult.Entry e, int index, int cx, int cy) {
        if (e.special() == null) {
            playH(SoundEvents.NOTE_BLOCK_PLING, 0.8f + 0.08f * index, 0.7f);
            return;
        }
        switch (e.special()) {
            case DIVANS_ALLOY -> {
                play(SoundEvents.TOTEM_USE, 1.3f, 1f);
                play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.8f, 1f);
                play(SoundEvents.WITHER_SPAWN, 1.8f, 0.4f);
                shake(10f, 1200);
                for (int c : RAINBOW) burst(cx, cy, 30, c, 260, 1);
                fireworksLeft += 10;
            }
            case JADE_DYE, JADERALD -> {
                play(SoundEvents.BELL_BLOCK, 1.2f, 1f);
                play(SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1f);
                burst(cx, cy, 60, 0x55FF55, 200, 1);
                shake(4f, 500);
            }
            case QUICK_CLAW, FLAWLESS_GEMSTONE -> {
                play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 1f);
                playH(SoundEvents.NOTE_BLOCK_CHIME, 1.2f + 0.05f * index, 1f);
                burst(cx, cy, 35, Rarity.EPIC.color, 170, 1);
            }
            case DIVAN_FRAGMENT -> {
                playH(SoundEvents.NOTE_BLOCK_BELL, 1.0f + 0.05f * index, 1f);
                burst(cx, cy, 25, 0xFFCC33, 150, 1);
            }
        }
    }

    // =================================================================== Rendern

    private long flashStart = -1;

    void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        if (!isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        long now = System.currentTimeMillis();
        long t = now - startedAt;
        float dt = Math.min(50, now - lastFrame) / 1000f;
        lastFrame = now;

        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int cx = sw / 2, cy = sh / 2;

        float alpha = clamp(t / 300f) * clamp((total() - t) / (float) OUTRO);

        // Bildschirm-Wackeln
        float ox = 0, oy = 0;
        if (shakeDur > 0 && now - shakeStart < shakeDur) {
            float k = 1f - (now - shakeStart) / (float) shakeDur;
            ox = (rng.nextFloat() - 0.5f) * 2f * shakeAmp * k;
            oy = (rng.nextFloat() - 0.5f) * 2f * shakeAmp * k;
        }
        g.pose().pushMatrix();
        g.pose().translate(ox, oy);

        // Hintergrund
        int dim = (int) (alpha * 0xB0);
        g.fillGradient(-20, -20, sw + 20, sh + 20, (dim << 24) | 0x08040F, (dim << 24) | 0x1A0A2A);

        if (t < tShow()) {
            renderReel(g, font, sw, cx, cy, t, alpha);
        }

        if (t >= tStop()) {
            renderShowcase(g, font, sw, sh, cx, cy, t, alpha);
        }

        updateAndDrawParticles(g, dt, sh, alpha);

        // Blitz beim Stopp
        if (flashStart > 0 && now - flashStart < 450) {
            float f = 1f - (now - flashStart) / 450f;
            int col = winner.rarity().atLeast(Rarity.EPIC) ? winner.rarity().color : 0xFFFFFF;
            g.fill(-20, -20, sw + 20, sh + 20, argb(f * 0.75f * alpha, col));
        }

        g.pose().popMatrix();
    }

    private void renderReel(GuiGraphicsExtractor g, Font font, int sw, int cx, int cy, long t, float alpha) {
        float shift = spinShift(t);
        float reveal = clamp((t - tStop()) / (float) REVEAL);     // 0 während Spin
        float others = alpha * (1f - easeOutCubic(reveal));

        // Band hinter der Rolle
        int bandTop = cy - CH / 2 - 12, bandBot = cy + CH / 2 + 12;
        g.fill(-20, bandTop, sw + 20, bandBot, argb(0.55f * others, 0x000000));
        g.horizontalLine(-20, sw + 20, bandTop, argb(0.8f * others, 0xFFAA00));
        g.horizontalLine(-20, sw + 20, bandBot - 1, argb(0.8f * others, 0xFFAA00));

        // Karten (fahren nach rechts)
        for (int k = 0; k < reel.size(); k++) {
            if (k == WINNER_INDEX && t >= tStop()) continue; // Gewinner extra
            float x = cx + shift - k * STEP;
            if (x < -CW || x > sw + CW) continue;
            float edge = clamp(Math.min(x, sw - x) / 70f);
            drawCard(g, font, reel.get(k), x, cy, 1f, others * edge, false, t);
        }

        // Gewinner nach dem Stopp: leuchtet, wird groß, wandert in die Mitte
        if (t >= tStop()) {
            float wx = cx + shift - WINNER_INDEX * STEP;
            float e = easeInOutCubic(reveal);
            float x = lerp(wx, cx, e);
            float scale = lerp(1f, showScale(), e);
            drawCard(g, font, winner, x, cy, scale, alpha, true, t);
        }

        // Mittel-Markierung
        if (t < tStop() + 200) {
            int col = argb(others, 0xFFDD44);
            g.fill(cx - 1, bandTop - 6, cx + 1, bandBot + 6, argb(others * 0.9f, 0xFFDD44));
            for (int i = 0; i < 6; i++) {
                g.fill(cx - 6 + i, bandTop - 12 + i, cx + 6 - i, bandTop - 11 + i, col);
                g.fill(cx - 6 + i, bandBot + 11 - i, cx + 6 - i, bandBot + 12 - i, col);
            }
        }
    }

    private float showScale() {
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return Math.max(1f, Math.min(1.6f, (sh - 110f) / CH));
    }

    private void renderShowcase(GuiGraphicsExtractor g, Font font, int sw, int sh, int cx, int cy, long t, float alpha) {
        Rarity r = winner.rarity();
        float scale = showScale();
        float cardW = CW * scale, cardH = CH * scale;
        float showP = clamp((t - tShow()) / 500f);

        // Lichtstrahlen hinter dem Girl
        if (r.atLeast(Rarity.RARE)) {
            float a = alpha * clamp((t - tStop()) / 600f);
            int rays = r == Rarity.MYTHIC ? 24 : (r.atLeast(Rarity.LEGENDARY) ? 18 : 12);
            float len = r.atLeast(Rarity.LEGENDARY) ? Math.max(sw, sh) : cardH * 1.4f;
            float spin = t / (r == Rarity.MYTHIC ? 900f : 2200f);
            for (int i = 0; i < rays; i++) {
                int col = r == Rarity.MYTHIC ? RAINBOW[i % RAINBOW.length] : r.color;
                g.pose().pushMatrix();
                g.pose().translate(cx, cy);
                g.pose().rotate((float) (spin + i * 2 * Math.PI / rays));
                g.fill(0, -2, (int) len, 2, argb(a * 0.18f, col));
                g.fill(0, -1, (int) (len * 0.7f), 1, argb(a * 0.25f, 0xFFFFFF));
                g.pose().popMatrix();
            }
        }

        // Pulsierender Ring (Mythisch: Regenbogen)
        if (r.atLeast(Rarity.EPIC)) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(t / 180.0));
            int ringR = (int) (cardH * 0.62f + pulse * 6);
            for (int i = 0; i < 64; i++) {
                double a = i * Math.PI * 2 / 64 + t / 1500.0;
                int col = r == Rarity.MYTHIC ? RAINBOW[i % RAINBOW.length] : r.color;
                drawDiamond(g, cx + (int) (Math.cos(a) * ringR * 1.25f), cy + (int) (Math.sin(a) * ringR),
                        2, argb(alpha * (0.4f + 0.4f * pulse), col));
            }
        }

        // Das Girl (nach dem Reveal)
        if (t >= tShow()) {
            float bob = (float) Math.sin(t / 500.0) * 2f;
            drawCard(g, font, winner, cx, cy + bob, scale, alpha, true, t);
        }

        // Titel über dem Girl
        float titleY = cy - cardH / 2 - 22;
        float pop = easeOutBack(clamp((t - tStop()) / 500f));
        String title = "✦ " + r.label + " ✦";
        if (r == Rarity.MYTHIC) {
            waveText(g, font, title, cx, titleY, 2f * pop, alpha, t, true);
        } else {
            scaledCentered(g, font, Component.literal(title), cx, titleY, 2f * pop, argb(alpha, r.color), true);
        }

        // Divan's Alloy: Riesiger Extra-Schriftzug
        if (result.has(RunResult.Special.DIVANS_ALLOY) && t >= tShow()) {
            float p = easeOutBack(clamp((t - tShow()) / 700f));
            float jitter = (float) Math.sin(t / 40.0) * 1.5f;
            waveText(g, font, "DIVAN'S ALLOY!!!", cx + jitter, Math.max(14, titleY - 26), 2.6f * p, alpha, t, true);
        }

        // Kreisende Items
        if (t >= tShow()) {
            List<RunResult.Entry> items = result.orbitItems();
            int n = items.size();
            float rx = cardW / 2 + 70, ry = cardH / 2 - 4;
            for (int i = 0; i < Math.min(n, itemsPopped); i++) {
                RunResult.Entry e = items.get(i);
                long since = t - tShow() - 200 - i * 260L;
                float p = easeOutBack(clamp(since / 400f));
                double ang = -Math.PI / 2 + i * 2 * Math.PI / n + t / 3200.0;
                float ix = cx + (float) Math.cos(ang) * rx * Math.min(1f, p);
                float iy = cy + (float) Math.sin(ang) * ry * Math.min(1f, p);
                drawOrbitItem(g, font, e, ix, iy, p, alpha * Math.min(1f, p * 1.5f), t);
            }
        }

        // Fußzeile
        if (t >= tShow()) {
            int more = result.notShownCount();
            String foot = (more > 0 ? "+" + more + " weitere Rewards  •  " : "")
                    + (result.specials.isEmpty() && result.others.isEmpty() ? "Rewards im Chat  •  " : "")
                    + "Abholen am Nucleus Vault  •  /nucleusanim stop";
            g.text(font, foot, cx - font.width(foot) / 2, sh - 14, argb(alpha * showP * 0.8f, 0xAAAAAA), true);
        }
    }

    private void drawOrbitItem(GuiGraphicsExtractor g, Font font, RunResult.Entry e, float x, float y,
                               float pop, float a, long t) {
        boolean special = e.special() != null;
        int col = e.rarity().color;
        float iconScale = (special ? 2f : 1.5f) * Math.max(0.01f, pop);

        // Leuchten hinter besonderen Items
        if (special) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(t / 150.0));
            int glowCol = e.special() == RunResult.Special.DIVANS_ALLOY
                    ? RAINBOW[(int) (t / 120) % RAINBOW.length] : col;
            drawDiamond(g, (int) x, (int) y, (int) (14 + pulse * 4), argb(a * 0.25f, glowCol));
            drawDiamond(g, (int) x, (int) y, (int) (9 + pulse * 2), argb(a * 0.35f, glowCol));
        }

        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(iconScale, iconScale);
        g.blit(RenderPipelines.GUI_TEXTURED, e.icon(), -8, -8, 0, 0, 16, 16, 16, 16, argb(a, 0xFFFFFF));
        g.pose().popMatrix();

        // Name darunter
        scaledCentered(g, font, e.text(), x, y + 8 * iconScale + 5, 0.75f, argb(a, 0xFFFFFF), true);
    }

    // =================================================================== Karte

    private void drawCard(GuiGraphicsExtractor g, Font font, Card card, float x, float y, float scale,
                          float a, boolean glow, long t) {
        if (a < 0.02f) return;
        Rarity r = card.rarity();
        int x0 = -CW / 2, y0 = -CH / 2, x1 = CW / 2, y1 = CH / 2;

        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);

        if (glow) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(t / 160.0));
            int gc = r == Rarity.MYTHIC ? RAINBOW[(int) (t / 140) % RAINBOW.length] : r.color;
            g.fill(x0 - 6, y0 - 6, x1 + 6, y1 + 6, argb(a * (0.15f + 0.15f * pulse), gc));
            g.fill(x0 - 3, y0 - 3, x1 + 3, y1 + 3, argb(a * (0.25f + 0.2f * pulse), gc));
        }

        g.fillGradient(x0, y0, x1, y1, argb(a, darken(r.color, 0.38f)), argb(a, 0x0B0612));
        // Portrait (passt sich an jede Bildgröße an)
        GirlRegistry.Girl girl = card.girl();
        float fit = Math.min(70f / girl.width(), 84f / girl.height());
        float pw = girl.width() * fit, ph = girl.height() * fit;
        g.pose().pushMatrix();
        g.pose().translate(-pw / 2f, y0 + 4 + (84 - ph));
        g.pose().scale(fit, fit);
        g.blit(RenderPipelines.GUI_TEXTURED, girl.texture(), 0, 0, 0, 0, girl.width(), girl.height(),
                girl.width(), girl.height(), argb(a, 0xFFFFFF));
        g.pose().popMatrix();

        // Glanz-Streifen
        int shine = (int) ((t / 6) % (CW + CH)) - CH / 2;
        if (r.atLeast(Rarity.EPIC)) {
            for (int i = 0; i < 6; i++) {
                int sx = x0 + shine + i;
                if (sx > x0 + 2 && sx < x1 - 2) g.verticalLine(sx, y0 + 2, y1 - 22, argb(a * 0.12f, 0xFFFFFF));
            }
        }

        // Namensfeld
        g.fill(x0 + 2, y1 - 22, x1 - 2, y1 - 2, argb(a * 0.9f, darken(r.color, 0.22f)));
        String name = girl.name();
        g.text(font, name, -font.width(name) / 2, y1 - 20, argb(a, 0xFFFFFF), true);
        scaledCentered(g, font, Component.literal(r.label), 0, y1 - 9, 0.65f, argb(a, r.color), true);

        // Rahmen
        g.outline(x0, y0, CW, CH, argb(a, r.color));
        g.outline(x0 + 1, y0 + 1, CW - 2, CH - 2, argb(a * 0.5f, r.color));
        drawDiamond(g, x0 + 7, y0 + 7, 3, argb(a, r.color));

        g.pose().popMatrix();
    }

    // =================================================================== Partikel

    private void burst(float x, float y, int n, int color, float speed, int kind) {
        for (int i = 0; i < n; i++) {
            Particle p = new Particle();
            double ang = rng.nextDouble() * Math.PI * 2;
            float v = speed * (0.3f + rng.nextFloat());
            p.x = x; p.y = y;
            p.vx = (float) Math.cos(ang) * v;
            p.vy = (float) Math.sin(ang) * v;
            p.gravity = 120;
            p.life = 700 + rng.nextInt(900);
            p.color = color;
            p.size = kind == 1 ? 2 + rng.nextInt(2) : 1 + rng.nextInt(2);
            p.kind = kind;
            particles.add(p);
        }
    }

    private void sparkleAround(int cx, int cy, int color) {
        Particle p = new Particle();
        float sc = showScale();
        p.x = cx + (rng.nextFloat() - 0.5f) * CW * sc * 1.6f;
        p.y = cy + (rng.nextFloat() - 0.5f) * CH * sc * 1.3f;
        p.vx = (rng.nextFloat() - 0.5f) * 10;
        p.vy = -10 - rng.nextFloat() * 20;
        p.life = 600 + rng.nextInt(600);
        p.color = rng.nextFloat() < 0.3f ? 0xFFFFFF : color;
        p.size = 1 + rng.nextInt(2);
        p.kind = 1;
        particles.add(p);
    }

    private void confetti(int sw, int color) {
        Particle p = new Particle();
        p.x = rng.nextFloat() * sw;
        p.y = -5;
        p.vx = (rng.nextFloat() - 0.5f) * 30;
        p.vy = 40 + rng.nextFloat() * 50;
        p.gravity = 15;
        p.life = 4000;
        p.color = color;
        p.size = 2;
        p.kind = 2;
        particles.add(p);
    }

    private void updateAndDrawParticles(GuiGraphicsExtractor g, float dt, int sh, float alpha) {
        if (particles.size() > 1500) particles.subList(0, particles.size() - 1500).clear();
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.age += (long) (dt * 1000);
            if (p.age >= p.life || p.y > sh + 10) {
                it.remove();
                continue;
            }
            p.vy += p.gravity * dt;
            p.vx *= (1f - 1.2f * dt);
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            float a = alpha * (1f - p.age / (float) p.life);
            int ix = (int) p.x, iy = (int) p.y;
            switch (p.kind) {
                case 1 -> {
                    drawDiamond(g, ix, iy, (int) p.size + 1, argb(a * 0.35f, p.color));
                    drawDiamond(g, ix, iy, (int) p.size, argb(a, p.color));
                }
                case 2 -> {
                    boolean flip = ((p.age / 120) & 1) == 0;
                    g.fill(ix, iy, ix + (flip ? 3 : 1), iy + (flip ? 1 : 3), argb(a, p.color));
                }
                default -> g.fill(ix, iy, ix + (int) p.size, iy + (int) p.size, argb(a, p.color));
            }
        }
    }

    // =================================================================== Helfer

    private void shake(float amp, long dur) {
        shakeAmp = amp;
        shakeDur = dur;
        shakeStart = System.currentTimeMillis();
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private static void playH(Holder<SoundEvent> sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.value(), pitch, volume));
    }

    private static void scaledCentered(GuiGraphicsExtractor g, Font font, Component text, float x, float y,
                                       float scale, int color, boolean shadow) {
        if ((color >>> 24) < 4 || scale <= 0.01f) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(font, text, -font.width(text) / 2, -4, color, shadow);
        g.pose().popMatrix();
    }

    /** Regenbogen-Text, dessen Buchstaben auf und ab wippen. */
    private static void waveText(GuiGraphicsExtractor g, Font font, String text, float x, float y,
                                 float scale, float alpha, long t, boolean rainbow) {
        if (scale <= 0.01f || alpha < 0.02f) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        int px = -font.width(text) / 2;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            int col = rainbow ? RAINBOW[(int) ((t / 90 + i) % RAINBOW.length)] : 0xFFFFFF;
            int dy = (int) Math.round(Math.sin(t / 120.0 + i * 0.6) * 1.5);
            g.text(font, ch, px, -4 + dy, argb(alpha, col), true);
            px += font.width(ch);
        }
        g.pose().popMatrix();
    }

    private static void drawDiamond(GuiGraphicsExtractor g, int cx, int cy, int r, int argb) {
        if (r <= 0 || (argb >>> 24) == 0) return;
        for (int dy = -r; dy <= r; dy++) {
            int w = r - Math.abs(dy);
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, argb);
        }
    }

    private static int darken(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 0xFF) * f), gr = (int) (((rgb >> 8) & 0xFF) * f), b = (int) ((rgb & 0xFF) * f);
        return (r << 16) | (gr << 8) | b;
    }

    private static int argb(float alpha, int rgb) {
        return ((int) (clamp(alpha) * 255f) << 24) | (rgb & 0xFFFFFF);
    }

    private static float clamp(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    private static float lerp(float a, float b, float f) { return a + (b - a) * f; }
    private static float easeOutCubic(float x) { float i = 1f - x; return 1f - i * i * i; }
    private static float easeOutQuint(float x) { float i = 1f - x; return 1f - i * i * i * i * i; }
    private static float easeInOutCubic(float x) {
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }
    private static float easeOutBack(float x) {
        float c1 = 1.70158f, c3 = c1 + 1f, m = x - 1f;
        return 1f + c3 * m * m * m + c1 * m * m;
    }
}
