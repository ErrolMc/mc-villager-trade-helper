# Villager Trade Helper

A client-side Fabric mod for Minecraft 26.3 that makes trading with villagers faster.

## In the trade screen

| What | What it does |
|---|---|
| **Trade All** button | Trades the selected offer again and again until the villager is out of stock, you run out of payment, or your inventory is full. All your iron for as many emeralds as the villager will give, in one click |
| **Shift-click an offer** | Same as Trade All, straight from the offer list. Hold Shift over an offer first to see what it would do: how many trades, what you'd pay and get, and what stops it |
| **Stock bars** | A bar along the bottom of each offer shows how much stock the villager has left, green to red |
| **Dimmed offers** | Offers you don't have the items for are greyed out |

While trading, the button turns into **Stop**. Above the trade window a line shows the progress, then a summary like "Traded 12 times: 48 Iron Ingot → 12 Emerald · villager out of stock".

Trading never loses items: a trade whose result wouldn't fit is not made (vanilla's shift-click would lose part of it), and room is always kept to put the leftover payment back into your inventory, so nothing gets dropped on the ground when you close the screen.

It all uses ordinary trade-screen clicks, so it works on vanilla and Paper servers with nothing installed server-side.

## Remembered trades

Each villager's offers are saved when you close its trade screen, per world or server. Look at a villager you've traded with to see its offers next to the crosshair, with what was sold out and how long ago you saw it. Handy for finding the librarian with Mending.

A villager that changes profession is forgotten on its own, since its old offers are gone.

## Options

Open them with **Mod Menu → Villager Trade Helper → Configure** (needs Cloth Config), or edit `config/villager-trade-helper.json`.

- **Trade All button**, **shift-click an offer to trade all**: on by default
- **Ticks between batches**: 1 by default. Raise it if a server's anti-cheat complains about fast clicking
- **Summary in chat**: also put the trade summary in chat
- **Stock bars**, **dim offers you can't pay for**
- **Remember villagers' trades**, and when to **show trades at the crosshair**: always, while sneaking, or off

Remembered trades live in `config/villager-trade-helper-trades.json`.

## Install

1. Build it (below) or take `villager-trade-helper-<version>.jar` from `build/libs` (not the `-sources` jar).
2. Put it in the instance's `mods` folder next to Fabric API. Mod Menu and Cloth Config are optional and only needed for the settings screen.
3. Restart the game.

Requires Minecraft 26.3, Fabric Loader 0.20.1+, Fabric API and Java 25.

## Development (VS Code)

1. Install JDK 25 (this machine has it at `C:\Program Files\Java\jdk-25.0.5.0.2`; `.vscode/settings.json` points there).
2. Open the folder in VS Code and install the recommended extensions when asked (Extension Pack for Java, Gradle for Java). The Java extension imports the Gradle project on its own; the first import downloads Minecraft and takes a few minutes.
3. Run **Terminal → Run Task → Decompile Minecraft sources** once, then **Java: Clean Java Language Server Workspace** from the command palette. After that, Go to Definition and hover docs work for Minecraft and Fabric API classes.
4. Press **F5** (launch config "Minecraft Client") to start a dev client through Gradle with the debugger attached.

Gradle tasks, from VS Code's task menu or a terminal:

```
.\gradlew.bat build               # compile, unit tests, jar in build\libs
.\gradlew.bat runClient           # dev client without the debugger
.\gradlew.bat runClientGameTest   # in-game test (muted): trades with villagers through the real screen, checks the results
.\gradlew.bat genSources          # decompiled Minecraft sources for the editor
```

The Gradle wrapper picks JDK 25 by itself (`gradle/gradle-daemon-jvm.properties`), even though `JAVA_HOME` on this machine is JDK 17.

The icon is drawn by `python -I tools/make-icon.py`.
