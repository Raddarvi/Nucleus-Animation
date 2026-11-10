package dev.nucleusanim;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /nucleusanim test              -> zufälliger Beispiel-Run
 * /nucleusanim test <seltenheit> -> common | uncommon | rare | epic | legendary | mythic
 * /nucleusanim stop              -> Animation abbrechen
 * /nucleusanim reload            -> eigene Bilder aus config/nucleusanimation/girls neu laden
 */
final class NucleusCommands {

    private NucleusCommands() {}

    static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> test = ClientCommands.literal("test")
                .executes(ctx -> {
                    Rarity[] all = Rarity.values();
                    SpinAnimation.INSTANCE.start(demo(all[(int) (Math.random() * all.length)]));
                    return 1;
                });
        for (Rarity r : Rarity.values()) {
            test.then(ClientCommands.literal(r.name().toLowerCase(Locale.ROOT)).executes(ctx -> {
                SpinAnimation.INSTANCE.start(demo(r));
                return 1;
            }));
        }

        dispatcher.register(ClientCommands.literal("nucleusanim")
                .then(test)
                .then(ClientCommands.literal("stop").executes(ctx -> {
                    SpinAnimation.INSTANCE.stop();
                    return 1;
                }))
                .then(ClientCommands.literal("reload").executes(ctx -> {
                    int n = GirlRegistry.reload();
                    ctx.getSource().sendFeedback(Component.literal("§d[Nucleus Animation] §f" + n
                            + " eigene Bilder geladen (Ordner: config/nucleusanimation/girls/<seltenheit>/)"));
                    return 1;
                })));
    }

    /** Beispiel-Rewards, die zur gewünschten Seltenheit passen. */
    private static List<Component> demo(Rarity r) {
        List<Component> list = new ArrayList<>();
        switch (r) {
            case MYTHIC -> {
                list.add(Component.literal("§6Divan's Alloy"));
                list.add(Component.literal("§5Divan Fragment §8x2"));
                list.add(Component.literal("§5Flawless Amethyst Gemstone"));
            }
            case LEGENDARY -> {
                list.add(Component.literal("§6Jade Dye"));
                list.add(Component.literal("§6Jaderald"));
                list.add(Component.literal("§5Divan Fragment"));
            }
            case EPIC -> {
                list.add(Component.literal("§6Quick Claw"));
                list.add(Component.literal("§5Flawless Ruby Gemstone"));
                list.add(Component.literal("§5Flawless Sapphire Gemstone"));
            }
            case RARE -> list.add(Component.literal("§5Divan Fragment"));
            default -> { }
        }
        list.add(Component.literal("§9Jungle Key §8x2"));
        list.add(Component.literal("§aFine Jade Gemstone"));
        list.add(Component.literal("§aFine Amber Gemstone"));
        list.add(Component.literal("§aFine Topaz Gemstone"));
        list.add(Component.literal("§fWishing Compass §8x3"));
        list.add(Component.literal("§9Goblin Egg §8x2"));
        list.add(Component.literal("§aSludge Juice §8x48"));
        list.add(Component.literal("§dGemstone Powder §d+2,500"));
        return list;
    }
}
