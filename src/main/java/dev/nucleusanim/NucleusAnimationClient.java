package dev.nucleusanim;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

/**
 * Einstiegspunkt (nur Client).
 *
 * Ablauf:
 *  1. NucleusChatListener liest die Hypixel-Chatnachrichten mit
 *     ("✦ You placed the ... Crystal!" und den "CRYSTAL NUCLEUS LOOT BUNDLE"-Block).
 *  2. Sobald der Reward-Block komplett ist, startet SpinAnimation.
 *  3. SpinAnimation zeichnet die Animation als HUD-Element über den Bildschirm.
 */
public class NucleusAnimationClient implements ClientModInitializer {

    public static final String MOD_ID = "nucleusanimation";

    @Override
    public void onInitializeClient() {
        NucleusChatListener listener = new NucleusChatListener();

        // System-/Spielnachrichten (Hypixel schickt den Loot-Bundle-Text als Systemnachricht)
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                listener.onMessage(message);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            listener.tick();
            SpinAnimation.INSTANCE.tick(client);
        });

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "reward_animation"),
                SpinAnimation.INSTANCE::render
        );

        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, buildContext) -> NucleusCommands.register(dispatcher)
        );
    }
}
