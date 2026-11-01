# Villager Trade Helper

Client-only Fabric mod for Minecraft 26.3: a Trade All button and shift-click-to-trade-all in the villager trade screen, stock bars and dimming on the offer list, and remembered villager offers shown at the crosshair. Sell All and `/trades` commands were removed at the user's request; don't add them back. Built for the user's MultiMC instance at `E:\MultiMC\instances\26.3` (mods in `.minecraft\mods`). Sibling of `../inventory-sorter`, same setup.

## Commands

- `.\gradlew.bat build`: compile, run unit tests, jar in `build\libs` (ship `villager-trade-helper-<version>.jar`, not `-sources`)
- `.\gradlew.bat runClientGameTest`: real client in a test world; summons villagers with fixed offers, right-clicks them, clicks Trade All / shift-click with real input, fills the inventory to check nothing is lost or dropped, checks the server-side inventory and offer uses and the remembered offers. Screenshots in `build\run\clientGameTest\screenshots`. Muted by `src/gametest/.../MuteAudio`.
- `.\gradlew.bat runClient`: dev client (Mod Menu and Cloth Config are on the dev runtime classpath)
- `.\gradlew.bat genSources`: decompiled Minecraft sources, needed for editor navigation and for checking 26.x names
- `.\tools\check-deps.ps1 -ModsDir 'E:\MultiMC\instances\26.3\.minecraft\mods' -Staged build\libs\villager-trade-helper-<version>.jar` (PowerShell 7): checks the jar's dependencies against the pack; must print `PROBLEMS: 0`
- `python -I tools/make-icon.py`: redraws the icon

## Gotchas

- Needs JDK 25; `gradle/gradle-daemon-jvm.properties` makes the wrapper use it although `JAVA_HOME` is JDK 17.
- 26.x is unobfuscated with Mojang names and renames versus 1.21: `GuiGraphicsExtractor`, `extractContents`/`extractRenderState`, `ContainerInput` (was ClickType), `MultiPlayerGameMode.handleContainerInput`, `Identifier`, `minecraft.gui.screen()` (no `minecraft.screen` field), `ResourceKey.identifier()`, `Entity.entityTags()`, villager classes in `world.entity.npc.villager` / `npc.wanderingtrader`. SDL mouse buttons: `InputConstants.MOUSE_BUTTON_LEFT = 1`, `RIGHT = 3`. Check names in the genSources output instead of guessing.
- Loom plugin is `net.fabricmc.fabric-loom` (no remapping): plain `implementation`/`compileOnly`/`localRuntime`.
- Don't run `gradlew vscode`; it overwrites the hand-written `.vscode/launch.json`.
- `MerchantMenu` slots: 0/1 payment, 2 result, 3–29 inventory, 30–38 hotbar. Selecting an offer (`MerchantScreen.postButtonClick`, via `MerchantScreenAccessor`) moves the old payment back and up to a stack of each cost in; shift-clicking the result repeats the trade. A multi-item result that only partly fits loses the rest (vanilla), and payment left in the slots is dropped on the ground on close when the inventory is full, so `TradePlanner.next` only shift-clicks a batch whose results and leftover payment all fit (simulated by `TradePlanner.fits`), takes fewer trades one at a time onto the cursor, or stops.
- The client predicts trades (`ClientSideMerchant.notifyTrade` bumps uses, the menu updates slots at once), so `TradeRunner` decides each tick from the client's menu; one batch per tick.
- The overlay is a mixin at the TAIL of `MerchantScreen.extractContents`, not Fabric's after-extract event: tooltips are queued there and the first one wins, so the Shift tooltip passes `replaceExisting = true`; `graphics.nextStratum()` draws over the offer items.
- Per-screen Fabric events (`ScreenMouseEvents.allowMouseClick(screen)`, `ScreenEvents.remove(screen)`) are reset on every init, so `TradeScreenHooks.attach` re-registers from `AFTER_INIT`. inventory-sorter puts buttons in the "Inventory" label row; ours sit in the strip above it (y 58–70).
- Which villager a trade screen belongs to: `UseEntityCallback` on the client (also fires server-side in singleplayer, hence the `isClientSide` check), falling back to `crosshairPickEntity`. Offers are saved with `MerchantOffers.CODEC` through `RegistryOps` over the connection's registries.

## Layout

- `src/main`: `trade/TradePlanner` is pure Java: next step of a trade run, the inventory-fit simulation, affordability estimate. Unit tests in `src/test`.
- `src/client`: `trade/Offers` reads offers and slots; `trade/TradeRunner` runs Trade All and keeps the status line; `gui/` has the button (`TradeButton`, `TradeScreenHooks`) and `TradeOverlay`; `memory/TradeMemory` stores offers per world/server, `memory/TradeHud` the crosshair list; `config/` holds the Gson config, Mod Menu entrypoint and Cloth Config screen (only loaded when Cloth Config is present).
- `src/gametest`: the client game test.

## Working with the user

Terse; wants things done and recommended, not surveyed. Ask before committing, pushing, or touching the MultiMC `mods` folder.
