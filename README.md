# Nucleus Animation (Fabric, Minecraft 26.1.2)

Client-Mod für Hypixel SkyBlock: Wenn du bei einem Crystal-Nucleus-Run in den
Crystal Hollows den 5. Crystal platzierst, kommt eine Animation:

1. Die 5 Crystals (Amber, Amethyst, Jade, Sapphire, Topaz) fliegen rein, kreisen und verschmelzen → Lichtblitz
2. „CRYSTAL NUCLEUS" ploppt auf, Buchstaben schimmern in den Crystal-Farben
3. Ein Panel klappt auf, deine Rewards gleiten einzeln rein (Original-Farben von Hypixel), mit Sound
4. Nach ~7 Sekunden blendet alles aus

Die Rewards werden aus Hypixels Chat-Block „CRYSTAL NUCLEUS LOOT BUNDLE → REWARDS" gelesen.
Die Mod liest nur den Chat und zeichnet ein Overlay – sie automatisiert nichts.

## Befehle
- `/nucleusanim test` – Animation mit Beispiel-Loot anzeigen (zum Ausprobieren, geht überall)
- `/nucleusanim stop` – laufende Animation abbrechen

## Voraussetzungen
- Minecraft 26.1.2, Fabric Loader 0.19.3
- Fabric API für 26.1.2 (im Modrinth-Profil mitinstallieren)

## .jar bauen

### Variante A – ohne eigene Entwicklungsumgebung (GitHub)
1. Neues GitHub-Repository erstellen und den Inhalt dieses Ordners hochladen
   (inkl. des versteckten Ordners `.github`).
2. Im Repo auf **Actions** gehen → der Workflow „Build Mod" läuft automatisch.
3. Wenn er grün ist: Lauf anklicken → unten unter **Artifacts** `nucleus-animation-jar` herunterladen.
4. Im ZIP liegt `nucleus-animation-1.0.0+26.1.2.jar` (die Datei **ohne** `-sources`).

### Variante B – lokal
Java 25 (JDK) installieren, dann im Ordner:
- Windows: `gradlew.bat build`
- macOS/Linux: `./gradlew build`

Die fertige Datei liegt in `build/libs/`.

## In der Modrinth App installieren
1. Ein Profil mit **Minecraft 26.1.2** und **Fabric** (Loader 0.19.3) anlegen bzw. öffnen.
2. Über die Inhaltssuche **Fabric API** hinzufügen.
3. Die `.jar` per **Inhalt hinzufügen → Aus Datei** importieren (oder Profilordner öffnen und in `mods/` legen).
4. Starten, auf Hypixel `/nucleusanim test` eingeben zum Testen.

## Hinweis
Falls eine andere Mod (z. B. ein Chat-Kompaktierer) den Reward-Block im Chat versteckt,
zeigt die Animation trotzdem den Titel, aber ohne Reward-Liste.
