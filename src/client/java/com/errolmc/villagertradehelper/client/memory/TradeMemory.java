package com.errolmc.villagertradehelper.client.memory;

import com.errolmc.villagertradehelper.client.config.TradeHelperConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.HolderLookup;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Each villager's offers as they were when you last closed its trade screen, per world or server, stored as
 * {@code config/villager-trade-helper-trades.json}. Feeds the crosshair HUD.
 */
public final class TradeMemory {
	private static final Logger LOGGER = LoggerFactory.getLogger("villager-trade-helper");
	private static final Gson GSON = new GsonBuilder().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("villager-trade-helper-trades.json");
	/** How long after right-clicking a villager its trade screen may open and still count as that villager's. */
	private static final long OPEN_WINDOW_MILLIS = 10_000;

	/** World key, then villager UUID. */
	private static Map<String, Map<UUID, Remembered>> worlds = new HashMap<>();
	private static boolean loaded;

	private static @Nullable Entity lastInteracted;
	private static long lastInteractedAt;
	/** The villager the open trade screen belongs to, by container id. */
	private static @Nullable Entity screenVillager;
	private static int screenContainerId = -1;

	private TradeMemory() {
	}

	/** One remembered villager. Field names are the file format. */
	public static final class Remembered {
		public String title = "";
		public int level;
		public String profession = "";
		/** When the trade screen closed, epoch millis. */
		public long seen;
		public JsonElement offers;
		transient @Nullable MerchantOffers decoded;

		public @Nullable MerchantOffers offers() {
			if (decoded == null && offers != null) {
				HolderLookup.Provider registries = registries();
				if (registries != null) {
					decoded = MerchantOffers.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), offers)
						.resultOrPartial(error -> LOGGER.warn("Could not read remembered offers: {}", error))
						.orElse(null);
				}
			}
			return decoded;
		}
	}

	/** {@code UseEntityCallback}: notes which villager the next trade screen is for. Never changes the interaction. */
	public static InteractionResult onInteract(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (level.isClientSide() && entity instanceof AbstractVillager) {
			lastInteracted = entity;
			lastInteractedAt = Util.getMillis();
		}
		return InteractionResult.PASS;
	}

	public static void onTradeScreenOpened(MerchantScreen screen) {
		int containerId = screen.getMenu().containerId;
		if (containerId == screenContainerId) {
			// Re-initialised after a resize; still the same villager.
			return;
		}
		screenContainerId = containerId;
		Minecraft minecraft = Minecraft.getInstance();
		if (lastInteracted != null && lastInteracted.isAlive() && Util.getMillis() - lastInteractedAt < OPEN_WINDOW_MILLIS) {
			screenVillager = lastInteracted;
		} else {
			screenVillager = minecraft.crosshairPickEntity instanceof AbstractVillager villager ? villager : null;
		}
		lastInteracted = null;
	}

	public static void onTradeScreenClosed(MerchantScreen screen) {
		Entity entity = screenVillager;
		boolean same = screen.getMenu().containerId == screenContainerId;
		screenVillager = null;
		screenContainerId = -1;
		MerchantOffers offers = screen.getMenu().getOffers();
		if (!same || entity == null || offers.isEmpty() || !TradeHelperConfig.get().rememberTrades) {
			return;
		}
		HolderLookup.Provider registries = registries();
		Map<UUID, Remembered> world = world();
		if (registries == null || world == null) {
			return;
		}
		JsonElement json = MerchantOffers.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), offers)
			.resultOrPartial(error -> LOGGER.warn("Could not save offers: {}", error))
			.orElse(null);
		if (json == null) {
			return;
		}
		Remembered villager = new Remembered();
		villager.title = screen.getTitle().getString();
		villager.level = screen.getMenu().getTraderLevel();
		villager.profession = profession(entity);
		villager.seen = System.currentTimeMillis();
		villager.offers = json;
		villager.decoded = offers.copy();
		world.put(entity.getUUID(), villager);
		save();
	}

	public static @Nullable Remembered get(Entity entity) {
		Map<UUID, Remembered> world = world();
		return world != null ? world.get(entity.getUUID()) : null;
	}

	/** Every second: forget villagers that changed profession, since their old offers are gone for good. */
	public static void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.level.getGameTime() % 20 != 0) {
			return;
		}
		Map<UUID, Remembered> world = world();
		if (world == null || world.isEmpty()) {
			return;
		}
		boolean changed = false;
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (!(entity instanceof AbstractVillager)) {
				continue;
			}
			Remembered villager = world.get(entity.getUUID());
			if (villager == null) {
				continue;
			}
			if (!villager.profession.isEmpty() && !profession(entity).equals(villager.profession)) {
				world.remove(entity.getUUID());
				changed = true;
			}
		}
		if (changed) {
			save();
		}
	}

	/** Leaving a world: the next one has other villagers. */
	public static void reset() {
		lastInteracted = null;
		screenVillager = null;
		screenContainerId = -1;
		for (Map<UUID, Remembered> world : worlds.values()) {
			for (Remembered villager : world.values()) {
				villager.decoded = null;
			}
		}
	}

	private static String profession(Entity entity) {
		if (entity instanceof Villager villager) {
			return villager.getVillagerData().profession().unwrapKey().map(key -> key.identifier().toString()).orElse("");
		}
		return "";
	}

	/** This world's or server's villagers, or null outside a world. */
	private static @Nullable Map<UUID, Remembered> world() {
		String key = worldKey();
		if (key == null) {
			return null;
		}
		load();
		return worlds.computeIfAbsent(key, k -> new LinkedHashMap<>());
	}

	/** {@code singleplayer:<save folder>} or {@code server:<address>}. */
	private static @Nullable String worldKey() {
		Minecraft minecraft = Minecraft.getInstance();
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server != null) {
			Path folder = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
			return "singleplayer:" + (folder != null ? folder : "world");
		}
		ServerData data = minecraft.getCurrentServer();
		return data != null ? "server:" + data.ip.toLowerCase(Locale.ROOT) : null;
	}

	private static HolderLookup.@Nullable Provider registries() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.getConnection() != null ? minecraft.getConnection().registryAccess() : null;
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		if (!Files.exists(FILE)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(FILE)) {
			Map<String, Map<UUID, Remembered>> read = GSON.fromJson(reader, new TypeToken<Map<String, LinkedHashMap<UUID, Remembered>>>() {
			}.getType());
			if (read != null) {
				worlds = new HashMap<>(read);
			}
		} catch (IOException | JsonParseException e) {
			LOGGER.error("Could not read {}, starting with no remembered trades", FILE, e);
		}
	}

	private static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(worlds, writer);
			}
		} catch (IOException e) {
			LOGGER.error("Could not write {}", FILE, e);
		}
	}
}
