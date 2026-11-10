package dev.nucleusanim;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * Die eigentliche Animation.
 *
 * Phase 1 (Intro):  Die 5 Crystals (Amber, Amethyst, Jade, Sapphire, Topaz) fliegen
 *                   von außen herein, kreisen und verschmelzen in der Mitte -> Lichtblitz.
 * Phase 2 (Titel):  "CRYSTAL NUCLEUS" ploppt mit schimmernden Crystal-Farben auf.
 * Phase 3 (Loot):   Ein Panel klappt auf, die Rewards gleiten nacheinander rein
 *                   (mit den Original-Farben von Hypixel), jeweils mit Sound.
 * Phase 4 (Outro):  Alles blendet aus.
 */
public final class RewardAnimation {

    public static final RewardAnimation INSTANCE = new RewardAnimation();

    // ---- Timing (Millisekunden) ----
    private static final long INTRO_MS = 1400;
    private static final long FLASH_MS = 350;
    private static final long TITLE_MS = 550;
    private static final long PANEL_OPEN_MS = 250;
    private static final long REWARD_STEP_MS = 120;
    private static final long REWARD_SLIDE_MS = 260;
    private static final long HOLD_MS = 7000;
    private static final long OUTRO_MS = 650;

    // Crystal-Farben (RGB): Amber, Amethyst, Jade, Sapphire, Topaz
    private static final int[] CRYSTAL_COLORS = {
            0xFFAA00, 0xB24DFF, 0x55FF55, 0x5577FF, 0xFFFF55
    };

    private static final int LINE_HEIGHT = 11;

    private List<Component> rewards = List.of();
    private long startedAt = -1;
    private boolean introSoundPlayed;
    private boolean titleSoundPlayed;
    private int rewardsSounded;

    private RewardAnimation() {}

    // ------------------------------------------------------------------ Steuerung

    public void start(List<Component> rewards, List<String> placedCrystals) {
        this.rewards = List.copyOf(rewards);
        this.startedAt = System.currentTimeMillis();
        this.introSoundPlayed = false;
        this.titleSoundPlayed = false;
        this.rewardsSounded = 0;
    }

    public void stop() {
        startedAt = -1;
    }

    public boolean isActive() {
        return startedAt >= 0;
    }

    private long rewardsStart() {
        return INTRO_MS + TITLE_MS;
    }

    private long totalDuration() {
        return rewardsStart() + PANEL_OPEN_MS + (long) rewards.size() * REWARD_STEP_MS + HOLD_MS + OUTRO_MS;
    }

    /** Sounds laufen im Tick, nicht im Rendern. */
    void tick(Minecraft mc) {
        if (!isActive()) return;
        long t = System.currentTimeMillis() - startedAt;

        if (t > totalDuration()) {
            stop();
            return;
        }

        if (!introSoundPlayed) {
            introSoundPlayed = true;
            play(mc, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f);
        }
        if (!titleSoundPlayed && t >= INTRO_MS) {
            titleSoundPlayed = true;
            play(mc, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);
        }

        long sinceRewards = t - rewardsStart() - PANEL_OPEN_MS;
        if (sinceRewards >= 0) {
            int revealed = (int) Math.min(rewards.size(), sinceRewards / REWARD_STEP_MS + 1);
            if (revealed > rewardsSounded) {
                rewardsSounded = revealed;
                play(mc, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f + 0.04f * revealed);
            }
        }
    }

    private static void play(Minecraft mc, SoundEvent sound, float pitch) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
    }

    // ------------------------------------------------------------------ Rendern

    void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        if (!isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        long t = System.currentTimeMillis() - startedAt;
        long total = totalDuration();

        // Ausblenden am Ende
        float fade = clamp((total - t) / (float) OUTRO_MS);
        float fadeIn = clamp(t / 300f);
        float alpha = fade * fadeIn;
        int yShift = (int) ((1f - fade) * -20f);

        // Abgedunkelter Hintergrund
        int dim = (int) (alpha * 0x90);
        g.fillGradient(0, 0, sw, sh, (dim << 24) | 0x0A0418, (dim << 24) | 0x1A0830);

        int cx = sw / 2;
        int titleY = Math.max(20, sh / 2 - 70) + yShift;

        // ---- Phase 1: Crystals ----
        if (t < INTRO_MS) {
            renderCrystalIntro(g, cx, sh / 2 - 20, t);
        }

        // ---- Lichtblitz beim Verschmelzen ----
        if (t >= INTRO_MS && t < INTRO_MS + FLASH_MS) {
            float f = 1f - (t - INTRO_MS) / (float) FLASH_MS;
            g.fill(0, 0, sw, sh, argb(f * 0.85f, 0xFFFFFF));
        }

        // ---- Phase 2: Titel ----
        long tTitle = t - INTRO_MS;
        if (tTitle >= 0) {
            float pop = easeOutBack(clamp(tTitle / (float) TITLE_MS));
            renderTitle(g, font, cx, titleY, pop, alpha, t);
        }

        // ---- Phase 3: Rewards ----
        long tPanel = t - rewardsStart();
        if (tPanel >= 0) {
            renderRewardPanel(g, font, cx, titleY + 30, sh, tPanel, alpha, t);
        }

        // ---- Crystals kreisen nach dem Intro klein um den Titel ----
        if (t >= INTRO_MS) {
            renderOrbit(g, cx, titleY + 4, t, alpha);
        }
    }

    private void renderCrystalIntro(GuiGraphicsExtractor g, int cx, int cy, long t) {
        float flyIn = clamp(t / (INTRO_MS * 0.7f));
        float collapse = clamp((t - INTRO_MS * 0.7f) / (INTRO_MS * 0.3f));

        float radius = lerp(260f, 42f, easeOutCubic(flyIn));
        radius = lerp(radius, 0f, easeInCubic(collapse));
        double spin = (t / 1000.0) * (1.5 + 6.0 * collapse);

        for (int i = 0; i < 5; i++) {
            double ang = -Math.PI / 2 + i * (2 * Math.PI / 5) + spin;
            int x = cx + (int) (Math.cos(ang) * radius);
            int y = cy + (int) (Math.sin(ang) * radius);
            int size = (int) lerp(4f, 9f, flyIn);
            drawCrystal(g, x, y, size, CRYSTAL_COLORS[i], 1f);
        }

        // Kern wächst beim Verschmelzen
        if (collapse > 0) {
            int core = (int) (collapse * 14);
            drawDiamond(g, cx, cy, core + 4, argb(0.35f * collapse, 0xFFFFFF));
            drawDiamond(g, cx, cy, core, argb(collapse, 0xFFFFFF));
        }
    }

    private void renderOrbit(GuiGraphicsExtractor g, int cx, int cy, long t, float alpha) {
        double spin = t / 1400.0;
        for (int i = 0; i < 5; i++) {
            double ang = -Math.PI / 2 + i * (2 * Math.PI / 5) + spin;
            int x = cx + (int) (Math.cos(ang) * 118);
            int y = cy + (int) (Math.sin(ang) * 16);
            drawCrystal(g, x, y, 4, CRYSTAL_COLORS[i], alpha);
        }
    }

    private void renderTitle(GuiGraphicsExtractor g, Font font, int cx, int y, float pop, float alpha, long t) {
        String title = "CRYSTAL NUCLEUS";
        float scale = 2f * Math.max(0.01f, pop);

        g.pose().pushMatrix();
        g.pose().translate(cx, y);
        g.pose().scale(scale, scale);

        // Jeder Buchstabe schimmert durch die 5 Crystal-Farben
        int x = -font.width(title) / 2;
        for (int i = 0; i < title.length(); i++) {
            String ch = String.valueOf(title.charAt(i));
            float phase = (float) ((t / 900.0) + i * 0.12);
            int color = cycleCrystalColor(phase);
            g.text(font, ch, x, -4, argb(alpha, color), true);
            x += font.width(ch);
        }
        g.pose().popMatrix();

        String sub = "✦ Loot Bundle erhalten! ✦";
        float subAlpha = alpha * clamp(pop);
        g.text(font, sub, cx - font.width(sub) / 2, y + 14, argb(subAlpha, 0xFFD966), true);
    }

    private void renderRewardPanel(GuiGraphicsExtractor g, Font font, int cx, int top, int sh,
                                   long tPanel, float alpha, long t) {
        int n = rewards.size();
        boolean empty = n == 0;

        int cols = n > 10 ? 2 : 1;
        int rows = empty ? 1 : (n + cols - 1) / cols;

        int colWidth = 0;
        if (empty) {
            colWidth = font.width("Rewards stehen im Chat – abholen am Nucleus Vault!") + 8;
        } else {
            for (Component c : rewards) colWidth = Math.max(colWidth, font.width(c) + 18);
        }
        int panelW = Math.max(160, cols * colWidth + 16);
        int panelH = rows * LINE_HEIGHT + 30;

        // Panel muss auf den Bildschirm passen
        if (top + panelH > sh - 8) top = Math.max(4, sh - 8 - panelH);

        float open = easeOutCubic(clamp(tPanel / (float) PANEL_OPEN_MS));
        int curH = Math.max(2, (int) (panelH * open));
        int x0 = cx - panelW / 2;
        int y0 = top;

        // Hintergrund + Rahmen in wechselnder Crystal-Farbe
        g.fill(x0, y0, x0 + panelW, y0 + curH, argb(alpha * 0.85f, 0x120A20));
        g.outline(x0, y0, panelW, curH, argb(alpha, cycleCrystalColor((float) (t / 1200.0))));
        g.outline(x0 + 1, y0 + 1, panelW - 2, curH - 2, argb(alpha * 0.3f, 0xFFFFFF));
        if (open < 1f) return;

        String header = "REWARDS";
        g.text(font, header, cx - font.width(header) / 2, y0 + 5, argb(alpha, 0xFFAA00), true);
        g.horizontalLine(x0 + 8, x0 + panelW - 9, y0 + 15, argb(alpha * 0.5f, 0xFFFFFF));

        if (empty) {
            String msg = "Rewards stehen im Chat – abholen am Nucleus Vault!";
            g.text(font, msg, cx - font.width(msg) / 2, y0 + 20, argb(alpha, 0xAAAAAA), true);
            return;
        }

        long tRewards = tPanel - PANEL_OPEN_MS;
        for (int i = 0; i < n; i++) {
            long since = tRewards - i * REWARD_STEP_MS;
            if (since < 0) break;
            float p = easeOutCubic(clamp(since / (float) REWARD_SLIDE_MS));
            float a = alpha * p;
            if (a < 0.05f) continue;

            int col = i / rows;
            int row = i % rows;
            int rx = x0 + 8 + col * colWidth + (int) ((1f - p) * 24f);
            int ry = y0 + 20 + row * LINE_HEIGHT;

            drawCrystal(g, rx + 4, ry + 3, 3, CRYSTAL_COLORS[i % 5], a);
            g.text(font, rewards.get(i), rx + 12, ry, argb(a, 0xFFFFFF), true);
        }

        // Fußzeile
        String foot = "Abholen am Nucleus Vault  •  /nucleusanim stop";
        int fy = y0 + panelH + 4;
        g.text(font, foot, cx - font.width(foot) / 2, fy, argb(alpha * 0.7f, 0x888888), false);
    }

    // ------------------------------------------------------------------ Zeichen-Helfer

    /** Ein Crystal: leuchtender Rand + Raute + heller Kern. */
    private static void drawCrystal(GuiGraphicsExtractor g, int x, int y, int r, int rgb, float alpha) {
        drawDiamond(g, x, y, r + 2, argb(alpha * 0.25f, rgb));
        drawDiamond(g, x, y, r, argb(alpha, rgb));
        drawDiamond(g, x, y - r / 3, Math.max(1, r / 3), argb(alpha * 0.8f, 0xFFFFFF));
    }

    /** Raute aus waagrechten 1px-Streifen. */
    private static void drawDiamond(GuiGraphicsExtractor g, int cx, int cy, int r, int argb) {
        if (r <= 0 || (argb >>> 24) == 0) return;
        for (int dy = -r; dy <= r; dy++) {
            int w = r - Math.abs(dy);
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, argb);
        }
    }

    private static int cycleCrystalColor(float phase) {
        float p = phase - (float) Math.floor(phase);
        float scaled = p * CRYSTAL_COLORS.length;
        int i = (int) scaled;
        int a = CRYSTAL_COLORS[i % CRYSTAL_COLORS.length];
        int b = CRYSTAL_COLORS[(i + 1) % CRYSTAL_COLORS.length];
        return lerpRgb(a, b, scaled - i);
    }

    private static int lerpRgb(int a, int b, float f) {
        int r = (int) lerp((a >> 16) & 0xFF, (b >> 16) & 0xFF, f);
        int gr = (int) lerp((a >> 8) & 0xFF, (b >> 8) & 0xFF, f);
        int bl = (int) lerp(a & 0xFF, b & 0xFF, f);
        return (r << 16) | (gr << 8) | bl;
    }

    private static int argb(float alpha, int rgb) {
        int a = (int) (clamp(alpha) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }

    private static float easeOutCubic(float x) {
        float i = 1f - x;
        return 1f - i * i * i;
    }

    private static float easeInCubic(float x) {
        return x * x * x;
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float m = x - 1f;
        return 1f + c3 * m * m * m + c1 * m * m;
    }
}
