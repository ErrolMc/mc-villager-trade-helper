package com.errolmc.villagertradehelper.client.gui;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** A small bevelled text button for the free space under the trade slots. */
public class TradeButton extends AbstractButton {
	public static final int HEIGHT = 12;

	private static final int BODY = 0xFF6F6F6F;
	private static final int LIGHT_EDGE = 0xFF9A9A9A;
	private static final int DARK_EDGE = 0xFF3F3F3F;
	private static final int BODY_HOVER = 0xFF6A78C8;
	private static final int LIGHT_EDGE_HOVER = 0xFFA3AEF0;
	private static final int DARK_EDGE_HOVER = 0xFF3B4385;
	private static final int TEXT = 0xFFE0E0E0;
	private static final int TEXT_HOVER = 0xFFFFFFFF;
	private static final int TEXT_IDLE = 0xFFA8A8A8;

	private final IntSupplier x;
	private final IntSupplier y;
	private final Supplier<Component> label;
	private final Supplier<Component> tooltip;
	/** False greys the button out: clicking it would do nothing but say why. */
	private final BooleanSupplier useful;
	private final Runnable onPress;
	private Component lastTooltip;

	/** Position, label, tooltip and greyed state are read every frame. */
	public TradeButton(IntSupplier x, IntSupplier y, int width, Supplier<Component> label, Supplier<Component> tooltip,
					   BooleanSupplier useful, Runnable onPress) {
		super(x.getAsInt(), y.getAsInt(), width, HEIGHT, label.get());
		this.x = x;
		this.y = y;
		this.label = label;
		this.tooltip = tooltip;
		this.useful = useful;
		this.onPress = onPress;
		updateTooltip();
	}

	@Override
	public void onPress(InputWithModifiers input) {
		onPress.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		setPosition(x.getAsInt(), y.getAsInt());
		setMessage(label.get());
		updateTooltip();

		// Not isHoveredOrFocused(): a clicked button keeps focus and would stay highlighted.
		boolean hovered = isHovered();
		int x0 = getX();
		int y0 = getY();
		int x1 = x0 + getWidth();
		int y1 = y0 + getHeight();
		graphics.fill(x0, y0, x1, y1, hovered ? BODY_HOVER : BODY);
		graphics.fill(x0, y0, x1 - 1, y0 + 1, hovered ? LIGHT_EDGE_HOVER : LIGHT_EDGE);
		graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, hovered ? LIGHT_EDGE_HOVER : LIGHT_EDGE);
		graphics.fill(x0 + 1, y1 - 1, x1, y1, hovered ? DARK_EDGE_HOVER : DARK_EDGE);
		graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, hovered ? DARK_EDGE_HOVER : DARK_EDGE);

		Font font = Minecraft.getInstance().font;
		int color = hovered ? TEXT_HOVER : useful.getAsBoolean() ? TEXT : TEXT_IDLE;
		graphics.centeredText(font, getMessage(), x0 + getWidth() / 2, y0 + 2, color);
	}

	private void updateTooltip() {
		Component current = tooltip.get();
		if (!current.equals(lastTooltip)) {
			lastTooltip = current;
			setTooltip(Tooltip.create(current));
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
