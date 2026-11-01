package com.errolmc.villagertradehelper.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Settings, stored as {@code config/villager-trade-helper.json}. */
public final class TradeHelperConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("villager-trade-helper");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("villager-trade-helper.json");

	private static TradeHelperConfig instance = new TradeHelperConfig();

	/** The Trade All button under the trade slots. */
	public boolean showButtons = true;
	/** Shift-clicking an offer in the list trades it all. */
	public boolean shiftClickTradesAll = true;
	/** A bar along the bottom of each offer showing how much stock the villager has left. */
	public boolean showStockBars = true;
	/** Grey out offers you can't pay for. */
	public boolean dimUnaffordable = true;
	/** Ticks between batches; 1 is as fast as the game allows a refill to be seen. */
	public int ticksBetweenTrades = 1;
	/** Also put the summary in chat, not only above the trade window. */
	public boolean chatSummary = false;

	/** Remember each villager's offers when its trade screen closes. */
	public boolean rememberTrades = true;
	/** When to show a remembered villager's offers next to the crosshair. */
	public HudMode hudMode = HudMode.ALWAYS;

	public static TradeHelperConfig get() {
		return instance;
	}

	public static void load() {
		if (!Files.exists(FILE)) {
			instance = new TradeHelperConfig();
			save();
			return;
		}
		try (Reader reader = Files.newBufferedReader(FILE)) {
			TradeHelperConfig loaded = GSON.fromJson(reader, TradeHelperConfig.class);
			instance = loaded != null ? loaded.sanitized() : new TradeHelperConfig();
		} catch (IOException | JsonParseException e) {
			LOGGER.error("Could not read {}, using defaults", FILE, e);
			instance = new TradeHelperConfig();
		}
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			LOGGER.error("Could not write {}", FILE, e);
		}
	}

	private TradeHelperConfig sanitized() {
		if (hudMode == null) {
			hudMode = HudMode.ALWAYS;
		}
		ticksBetweenTrades = Math.clamp(ticksBetweenTrades, 1, 20);
		return this;
	}
}
