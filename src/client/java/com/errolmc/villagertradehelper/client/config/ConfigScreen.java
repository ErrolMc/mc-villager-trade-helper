package com.errolmc.villagertradehelper.client.config;

import java.util.Arrays;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The Cloth Config settings screen. Only loaded when Cloth Config is installed. */
final class ConfigScreen {
	private ConfigScreen() {
	}

	static Screen create(Screen parent) {
		TradeHelperConfig config = TradeHelperConfig.get();
		TradeHelperConfig defaults = new TradeHelperConfig();

		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.translatable("villager-trade-helper.config.title"))
			.setSavingRunnable(TradeHelperConfig::save);
		ConfigEntryBuilder entries = builder.entryBuilder();

		ConfigCategory trading = builder.getOrCreateCategory(Component.translatable("villager-trade-helper.config.category.trading"));
		trading.addEntry(entries.startBooleanToggle(text("show_buttons"), config.showButtons)
			.setDefaultValue(defaults.showButtons)
			.setTooltip(tooltip("show_buttons"))
			.setSaveConsumer(value -> config.showButtons = value)
			.build());
		trading.addEntry(entries.startBooleanToggle(text("shift_click_trades_all"), config.shiftClickTradesAll)
			.setDefaultValue(defaults.shiftClickTradesAll)
			.setTooltip(tooltip("shift_click_trades_all"))
			.setSaveConsumer(value -> config.shiftClickTradesAll = value)
			.build());
		trading.addEntry(entries.startIntSlider(text("ticks_between_trades"), config.ticksBetweenTrades, 1, 20)
			.setDefaultValue(defaults.ticksBetweenTrades)
			.setTooltip(tooltip("ticks_between_trades"))
			.setSaveConsumer(value -> config.ticksBetweenTrades = value)
			.build());
		trading.addEntry(entries.startBooleanToggle(text("chat_summary"), config.chatSummary)
			.setDefaultValue(defaults.chatSummary)
			.setTooltip(tooltip("chat_summary"))
			.setSaveConsumer(value -> config.chatSummary = value)
			.build());

		ConfigCategory display = builder.getOrCreateCategory(Component.translatable("villager-trade-helper.config.category.display"));
		display.addEntry(entries.startBooleanToggle(text("show_stock_bars"), config.showStockBars)
			.setDefaultValue(defaults.showStockBars)
			.setTooltip(tooltip("show_stock_bars"))
			.setSaveConsumer(value -> config.showStockBars = value)
			.build());
		display.addEntry(entries.startBooleanToggle(text("dim_unaffordable"), config.dimUnaffordable)
			.setDefaultValue(defaults.dimUnaffordable)
			.setTooltip(tooltip("dim_unaffordable"))
			.setSaveConsumer(value -> config.dimUnaffordable = value)
			.build());

		ConfigCategory memory = builder.getOrCreateCategory(Component.translatable("villager-trade-helper.config.category.memory"));
		memory.addEntry(entries.startBooleanToggle(text("remember_trades"), config.rememberTrades)
			.setDefaultValue(defaults.rememberTrades)
			.setTooltip(tooltip("remember_trades"))
			.setSaveConsumer(value -> config.rememberTrades = value)
			.build());
		memory.addEntry(entries.startEnumSelector(text("hud_mode"), HudMode.class, config.hudMode)
			.setDefaultValue(defaults.hudMode)
			.setEnumNameProvider(mode -> ((HudMode) mode).displayName())
			.setTooltip(tooltip("hud_mode"))
			.setSaveConsumer(value -> config.hudMode = value)
			.build());

		return builder.build();
	}

	private static Component text(String key) {
		return Component.translatable("villager-trade-helper.config." + key);
	}

	/** Cloth Config does not wrap tooltips, so the translations put a newline where each line should end. */
	private static Component[] tooltip(String key) {
		String text = Component.translatable("villager-trade-helper.config." + key + ".tooltip").getString();
		return Arrays.stream(text.split("\n")).map(Component::literal).toArray(Component[]::new);
	}
}
