package dev.nucleusanim;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * /nucleusanim test  -> zeigt die Animation mit Beispiel-Loot (zum Ausprobieren)
 * /nucleusanim stop  -> bricht eine laufende Animation ab
 */
final class NucleusCommands {

    private NucleusCommands() {}

    static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("nucleusanim")
                .then(ClientCommands.literal("test").executes(ctx -> {
                    RewardAnimation.INSTANCE.start(demoRewards(),
                            List.of("Amber", "Amethyst", "Jade", "Sapphire", "Topaz"));
                    return 1;
                }))
                .then(ClientCommands.literal("stop").executes(ctx -> {
                    RewardAnimation.INSTANCE.stop();
                    return 1;
                })));
    }

    private static List<Component> demoRewards() {
        return List.of(
                Component.literal("§5Divan Fragment"),
                Component.literal("§9Jungle Key §8x2"),
                Component.literal("§aFine Jade Gemstone"),
                Component.literal("§aFine Amber Gemstone"),
                Component.literal("§aFine Amethyst Gemstone"),
                Component.literal("§aFine Sapphire Gemstone"),
                Component.literal("§aFine Topaz Gemstone"),
                Component.literal("§aFine Ruby Gemstone"),
                Component.literal("§aFine Jasper Gemstone"),
                Component.literal("§fWishing Compass §8x3"),
                Component.literal("§9Goblin Egg §8x2"),
                Component.literal("§aSludge Juice §8x48"),
                Component.literal("§9Ascension Rope"),
                Component.literal("§6Treasurite §8x6"),
                Component.literal("§5Flawless Ruby Gemstone"),
                Component.literal("§dGemstone Powder §d+2,500")
        );
    }
}
