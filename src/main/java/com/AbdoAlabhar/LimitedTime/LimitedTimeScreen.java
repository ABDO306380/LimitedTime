package com.AbdoAlabhar.LimitedTime;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LimitedTimeScreen extends Screen {
    private Button toggleButton;
    private EditBox timeInput;
    private boolean enabled = true;

    protected LimitedTimeScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        toggleButton = Button.builder(
                        Component.literal("Enabled: " + enabled),
                        button -> {
                            enabled = !enabled;
                            button.setMessage(Component.literal("Enabled: " + enabled));
                        })
                .pos(centerX - 50, centerY - 20)   // set x,y
                .size(100, 20)                     // set width,height
                .build();
        this.addRenderableWidget(toggleButton);

        // Integer input
        timeInput = new EditBox(this.font, centerX - 50, centerY + 10, 100, 20, Component.literal("Time"));
        timeInput.setValue("0");
        this.addRenderableWidget(timeInput);
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics) {
        super.renderBackground(graphics);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);
        graphics.drawString(this.font, "Set Time:", this.width / 2 - 50, this.height / 2, 0xFFFFFF);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getTime() {
        try {
            return Integer.parseInt(timeInput.getValue());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
