package com.errolmc.villagertradehelper.client.config;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** When a remembered villager's offers show next to the crosshair. */
public enum HudMode {
	ALWAYS,
	SNEAKING,
	OFF;

	public Component displayName() {
		return Component.translatable("villager-trade-helper.hud_mode." + name().toLowerCase(Locale.ROOT));
	}
}
