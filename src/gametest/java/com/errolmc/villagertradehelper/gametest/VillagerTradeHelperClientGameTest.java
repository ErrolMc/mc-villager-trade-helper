package com.errolmc.villagertradehelper.gametest;

import com.errolmc.villagertradehelper.client.config.ModMenuIntegration;
import com.errolmc.villagertradehelper.client.gui.TradeButton;
import com.errolmc.villagertradehelper.client.memory.TradeMemory;
import com.errolmc.villagertradehelper.client.mixin.AbstractContainerScreenAccessor;
import com.errolmc.villagertradehelper.client.trade.TradeRunner;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Trades with real villagers through the real trade screen, clicking with the mouse, and checks the server-side
 * inventory and offers. Screenshots land in build/run/clientGameTest/screenshots.
 */
public class VillagerTradeHelperClientGameTest implements FabricClientGameTest {
	private static final int IRON = 0;
	private static final int PAPER = 1;
	private static final int BREAD = 2;
	private static final int MENDING = 3;

	private static final String NO_XP = ",maxUses:%d,xp:0,rewardExp:0b}";
	private static final String SMITH_OFFERS = "["
		+ "{buy:{id:\"minecraft:iron_ingot\",count:4},sell:{id:\"minecraft:emerald\",count:1}" + NO_XP.formatted(12) + ","
		+ "{buy:{id:\"minecraft:paper\",count:24},sell:{id:\"minecraft:emerald\",count:1}" + NO_XP.formatted(16) + ","
		+ "{buy:{id:\"minecraft:emerald\",count:3},sell:{id:\"minecraft:bread\",count:6}" + NO_XP.formatted(16) + ","
		+ "{buy:{id:\"minecraft:emerald\",count:20},buyB:{id:\"minecraft:book\",count:1},"
		+ "sell:{id:\"minecraft:enchanted_book\",count:1,components:{\"minecraft:stored_enchantments\":{\"minecraft:mending\":1}}}"
		+ NO_XP.formatted(1)
		+ "]";
	private static final String BAKER_OFFERS = "["
		+ "{buy:{id:\"minecraft:emerald\",count:1},sell:{id:\"minecraft:bread\",count:6}" + NO_XP.formatted(64)
		+ "]";

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(MuteAudio::mute);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty peaceful");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("time set noon");
			BlockPos base = world.getServer().computeOnServer(server -> player(server).blockPosition());

			UUID smith = summon(world, base.offset(2, 0, 0), "armorer", 2, SMITH_OFFERS);
			UUID baker = summon(world, base.offset(-2, 0, 0), "farmer", 1, BAKER_OFFERS);
			context.waitTicks(5);

			tradeAllAndShiftClick(context, world, smith);
			memoryAndHud(context, world, smith);
			inventoryFull(context, world, baker);
			configScreen(context);
		}
	}

	// ------------------------------------------------------------------ scenarios

	private void tradeAllAndShiftClick(ClientGameTestContext context, TestSingleplayerContext world, UUID smith) {
		world.getServer().runCommand("clear @a");
		give(world, "iron_ingot", 102);
		give(world, "paper", 102);
		give(world, "emerald", 40);
		give(world, "book", 1);
		openTrades(context, world, smith);
		List<TradeButton> buttons = buttons(context);
		check(buttons.size() == 1, "trade screen should have just the Trade All button, has " + buttons.size());
		TradeButton tradeAll = buttons.getFirst();

		// Trade All on iron with plenty of iron: stops when the villager's 12 are used up.
		clickOffer(context, world, IRON);
		hover(context, tradeAll);
		context.takeScreenshot("trade-all-tooltip");
		click(context, world, tradeAll);
		waitForTrades(context, world);
		checkCount(world, Items.IRON_INGOT, 54, "iron after Trade All");
		checkCount(world, Items.EMERALD, 52, "emeralds after Trade All");
		check(uses(world, smith, IRON) == 12, "iron offer should be used up, uses " + uses(world, smith, IRON));
		checkPaymentSlotsEmpty(world, "after Trade All");
		checkStatus(context, "villager out of stock");

		// Shift-click the paper offer: stops when the paper runs out (102 paper, 24 a trade).
		context.getInput().holdShift();
		hoverOffer(context, PAPER);
		context.takeScreenshot("shift-tooltip");
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.getInput().releaseShift();
		waitForTrades(context, world);
		checkCount(world, Items.PAPER, 6, "paper after shift-click");
		checkCount(world, Items.EMERALD, 56, "emeralds after shift-click");
		checkStatus(context, "out of Paper");

		// Buying with emeralds works the same: 16 bread trades at 3 emeralds.
		clickOffer(context, world, BREAD);
		click(context, world, tradeAll);
		waitForTrades(context, world);
		checkCount(world, Items.EMERALD, 8, "emeralds after buying bread");
		checkCount(world, Items.BREAD, 96, "bread bought");

		// 8 emeralds can't pay for the mending book: nothing happens, the status says why.
		context.getInput().holdShift();
		hoverOffer(context, MENDING);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.getInput().releaseShift();
		context.waitTicks(3);
		checkCount(world, Items.EMERALD, 8, "emeralds after an unaffordable trade");
		checkStatus(context, "out of Emerald");
		context.takeScreenshot("cannot-afford");
		close(context, world);
	}

	private void memoryAndHud(ClientGameTestContext context, TestSingleplayerContext world, UUID smith) {
		String title = context.computeOnClient(minecraft -> {
			TradeMemory.Remembered remembered = TradeMemory.get(clientEntity(minecraft, smith));
			return remembered != null && remembered.offers() != null && remembered.offers().size() == 4
				&& remembered.offers().get(IRON).isOutOfStock() ? remembered.title + " " + remembered.level : null;
		});
		check("Armorer 2".equals(title), "the armorer's offers should be remembered, got " + title);

		lookAt(context, world, smith);
		context.takeScreenshot("hud");
	}

	private void inventoryFull(ClientGameTestContext context, TestSingleplayerContext world, UUID baker) {
		// 64 emeralds, 60 bread and stone everywhere else but one slot. Taking the emeralds out frees a second slot.
		// 11 trades (66 bread) fill the bread stack and one slot; the other is kept for the 53 emeralds left over.
		world.getServer().runCommand("clear @a");
		world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:emerald 64");
		world.getServer().runCommand("item replace entity @a hotbar.1 with minecraft:bread 60");
		for (int i = 2; i < 9; i++) {
			world.getServer().runCommand("item replace entity @a hotbar." + i + " with minecraft:stone 64");
		}
		for (int i = 1; i < 27; i++) {
			world.getServer().runCommand("item replace entity @a inventory." + i + " with minecraft:stone 64");
		}
		openTrades(context, world, baker);
		clickOffer(context, world, 0);
		click(context, world, buttons(context).getFirst());
		waitForTrades(context, world);
		checkCount(world, Items.BREAD, 126, "bread with a nearly full inventory");
		checkCount(world, Items.EMERALD, 53, "emeralds put back with a nearly full inventory");
		checkCount(world, Items.STONE, 33 * 64, "stone");
		checkPaymentSlotsEmpty(world, "with a nearly full inventory");
		checkStatus(context, "inventory full");
		context.takeScreenshot("inventory-full");
		close(context, world);
		int dropped = world.getServer().computeOnServer(server -> player(server).level()
			.getEntitiesOfClass(ItemEntity.class, player(server).getBoundingBox().inflate(16)).size());
		check(dropped == 0, dropped + " item stacks were dropped on the ground");
	}

	private void configScreen(ClientGameTestContext context) {
		context.setScreen(() -> new ModMenuIntegration().getModConfigScreenFactory().create(null));
		context.waitTicks(5);
		context.takeScreenshot("config-screen");
		context.setScreen(() -> null);
	}

	// ------------------------------------------------------------------ world helpers

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static String xyz(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	private static UUID summon(TestSingleplayerContext world, BlockPos pos, String profession, int level, String offers) {
		String tag = "vth_" + profession;
		world.getServer().runCommand("summon minecraft:villager " + xyz(pos) + " {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"" + tag
			+ "\"],VillagerData:{type:\"minecraft:plains\",profession:\"minecraft:" + profession + "\",level:" + level + "},Offers:{Recipes:" + offers + "}}");
		return world.getServer().computeOnServer(server -> player(server).level().getEntitiesOfClass(AbstractVillager.class,
				player(server).getBoundingBox().inflate(16), villager -> villager.entityTags().contains(tag))
			.getFirst().getUUID());
	}

	private static void give(TestSingleplayerContext world, String item, int count) {
		world.getServer().runCommand("give @a minecraft:" + item + " " + count);
	}

	/** Server-side count of an item in the player's inventory. */
	private static int count(TestSingleplayerContext world, Item item) {
		return world.getServer().computeOnServer(server -> {
			int total = 0;
			for (int i = 0; i < 36; i++) {
				if (player(server).getInventory().getItem(i).is(item)) {
					total += player(server).getInventory().getItem(i).getCount();
				}
			}
			return total;
		});
	}

	private static int uses(TestSingleplayerContext world, UUID villager, int offer) {
		return world.getServer().computeOnServer(server ->
			((AbstractVillager) player(server).level().getEntity(villager)).getOffers().get(offer).getUses());
	}

	private static void checkPaymentSlotsEmpty(TestSingleplayerContext world, String when) {
		boolean empty = world.getServer().computeOnServer(server -> player(server).containerMenu instanceof MerchantMenu menu
			&& menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty());
		check(empty, "payment slots should be empty " + when);
	}

	// ------------------------------------------------------------------ screen helpers

	private static Entity clientEntity(Minecraft minecraft, UUID id) {
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity.getUUID().equals(id)) {
				return entity;
			}
		}
		throw new AssertionError("no client entity " + id);
	}

	private static void lookAt(ClientGameTestContext context, TestSingleplayerContext world, UUID villager) {
		world.getServer().runCommand("execute as @a at @s anchored eyes run tp @s ~ ~ ~ facing entity " + villager + " eyes");
		context.waitTicks(5);
	}

	/** Looks at the villager and right-clicks it, like a player would. */
	private static void openTrades(ClientGameTestContext context, TestSingleplayerContext world, UUID villager) {
		lookAt(context, world, villager);
		String aim = context.computeOnClient(minecraft -> minecraft.hitResult instanceof EntityHitResult hit
			? hit.getEntity().getUUID().equals(villager) ? null : "another entity " + hit.getEntity()
			: "nothing, hit " + minecraft.hitResult + " from " + minecraft.player.position() + " yaw " + minecraft.player.getYRot()
				+ " pitch " + minecraft.player.getXRot() + ", villager at " + clientEntity(minecraft, villager).position());
		check(aim == null, "the crosshair should be on the villager, it's on " + aim);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		context.waitFor(minecraft -> minecraft.gui.screen() instanceof MerchantScreen, 100);
		context.waitTicks(5);
	}

	private static void close(ClientGameTestContext context, TestSingleplayerContext world) {
		context.runOnClient(minecraft -> minecraft.player.closeContainer());
		world.getConnection().waitForServerboundPackets();
		context.waitTicks(3);
	}

	private static List<TradeButton> buttons(ClientGameTestContext context) {
		return context.computeOnClient(minecraft -> {
			List<TradeButton> buttons = new ArrayList<>();
			for (AbstractWidget widget : Screens.getWidgets(minecraft.gui.screen())) {
				if (widget instanceof TradeButton button) {
					buttons.add(button);
				}
			}
			return buttons;
		});
	}

	private static double[] guiScale(ClientGameTestContext context) {
		return context.computeOnClient(minecraft -> {
			Window window = minecraft.getWindow();
			return new double[] {(double) window.getScreenWidth() / window.getGuiScaledWidth(), (double) window.getScreenHeight() / window.getGuiScaledHeight()};
		});
	}

	private static void hover(ClientGameTestContext context, TradeButton button) {
		double[] scale = guiScale(context);
		int[] pos = context.computeOnClient(minecraft -> new int[] {button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2});
		context.getInput().setCursorPos(pos[0] * scale[0], pos[1] * scale[1]);
		context.waitTicks(3);
	}

	private static void click(ClientGameTestContext context, TestSingleplayerContext world, TradeButton button) {
		hover(context, button);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		world.getConnection().waitForServerboundPackets();
		context.waitTicks(2);
	}

	/** Moves the mouse over an offer in the list (the list doesn't scroll in these tests). */
	private static void hoverOffer(ClientGameTestContext context, int offer) {
		double[] scale = guiScale(context);
		int[] pos = context.computeOnClient(minecraft -> {
			AbstractContainerScreenAccessor panel = (AbstractContainerScreenAccessor) minecraft.gui.screen();
			return new int[] {panel.villagerTradeHelper$leftPos() + 5 + 44, panel.villagerTradeHelper$topPos() + 18 + offer * 20 + 10};
		});
		context.getInput().setCursorPos(pos[0] * scale[0], pos[1] * scale[1]);
		context.waitTicks(3);
	}

	private static void clickOffer(ClientGameTestContext context, TestSingleplayerContext world, int offer) {
		hoverOffer(context, offer);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		world.getConnection().waitForServerboundPackets();
		context.waitTicks(2);
	}

	private static void waitForTrades(ClientGameTestContext context, TestSingleplayerContext world) {
		context.waitFor(minecraft -> !(minecraft.gui.screen() instanceof MerchantScreen screen) || !TradeRunner.isRunning(screen.getMenu()), 600);
		world.getConnection().waitForServerboundPackets();
		context.waitTicks(3);
	}

	private static void checkStatus(ClientGameTestContext context, String expected) {
		String status = context.computeOnClient(minecraft -> {
			Component text = minecraft.gui.screen() instanceof MerchantScreen screen ? TradeRunner.status(screen.getMenu()) : null;
			return text != null ? text.getString() : "";
		});
		check(status.contains(expected), "status should mention \"" + expected + "\": " + status);
	}

	private static void checkCount(TestSingleplayerContext world, Item item, int expected, String what) {
		int actual = count(world, item);
		check(actual == expected, what + ": expected " + expected + ", got " + actual);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
