# Mob Resistance Lock (NeoForge 1.21.1, Tensura: Reincarnated)

Befehl (nur OPs / Permission-Level 2):

| Befehl | Wirkung |
| --- | --- |
| `/edit resistances true` | Mobs können Resistenzen lernen (normales Tensura-Verhalten, Standard) |
| `/edit resistances false` | Mobs können nach dem Spawnen keine neuen Resistenzen mehr lernen |
| `/edit resistances` | zeigt den aktuellen Wert |
| `/edit resistances debug` | zeigt, was die Mod beim nächsten Mob (10 Blöcke) erkennt |

Spieler sind nicht betroffen. Der Wert wird im Weltordner gespeichert.

## Jar mit GitHub bauen

1. Neues Repository auf GitHub anlegen und den Inhalt dieses Ordners hochladen (inkl. `.github/`).
2. Tab **Actions** öffnen, den Lauf **build** abwarten (oder per **Run workflow** starten).
3. Im fertigen Lauf unter **Artifacts** `mobresistlock-jar` herunterladen, die `.jar` in den `mods`-Ordner legen.

## So funktioniert es

Beim ersten Schaden speichert die Mod pro Mob eine Liste seiner Resistenzen (Baseline).
Ist `false` gesetzt, werden danach neu gelernte Resistenzen wieder entfernt.

Hinweis: Der Zugriff auf Tensuras Skill-System läuft per Reflection (`TensuraBridge.java`),
weil Tensura nicht quelloffen ist. Wenn `/edit resistances debug` meldet, dass Methoden nicht
gefunden wurden, müssen die Namen in `TensuraBridge.java` angepasst werden.
