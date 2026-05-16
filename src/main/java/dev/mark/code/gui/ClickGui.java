package dev.mark.code.gui;

import dev.mark.code.config.Config;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;

public class ClickGui extends Screen {
    public ClickGui() {
        super(Text.translatable("screen.modelreplacer.title"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int leftX = centerX - 200 / 2;

        CheckboxWidget renderSelf = CheckboxWidget.builder(
                Text.translatable("option.modelreplacer.replace_self"), this.textRenderer)
                .pos(leftX, centerY - 24 * 2).checked(Config.replaceSelf)
                .callback((cb, checked) -> Config.replaceSelf = checked).build();

        CheckboxWidget renderOthers = CheckboxWidget.builder(
                Text.translatable("option.modelreplacer.replace_others"), this.textRenderer)
                .pos(leftX, centerY - 24).checked(Config.replaceOthers)
                .callback((cb, checked) -> Config.replaceOthers = checked).build();

        CyclingButtonWidget<Config.Models> modelPicker = CyclingButtonWidget
                .<Config.Models>builder(preset -> Text.literal(preset.displayName))
                .values(Config.Models.values()).initially(Config.activePreset)
                .build(leftX, centerY + 6, 200, 20,
                        Text.translatable("option.modelreplacer.model"),
                        (button, value) -> Config.activePreset = value);

        ButtonWidget done = ButtonWidget.builder(
                Text.translatable("button.modelreplacer.done"), b -> this.close())
                .dimensions(centerX - 50, centerY + 50, 100, 20).build();

        addDrawableChild(renderSelf);
        addDrawableChild(renderOthers);
        addDrawableChild(modelPicker);
        addDrawableChild(done);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
