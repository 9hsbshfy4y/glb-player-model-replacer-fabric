package dev.mark.code.render;

import dev.mark.code.config.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;

public class PlayerRenderHook {
    public static boolean replaceRender(PlayerEntityRenderState state, MatrixStack matrices, VertexConsumerProvider provider) {
        if (!shouldReplace(state)) return false;

        ModelRenderer renderer = ModelManager.get(Config.activePreset);
        if (renderer == null) return false;

        flushImmediate(provider);
        Config.Models preset = Config.activePreset;
        renderer.render(matrices, state.bodyYaw, preset.forwardOffset, preset.leftOffset, preset.yawOffsetDeg, preset.scaleMultiplier);
        return true;
    }

    private static boolean shouldReplace(PlayerEntityRenderState state) {
        AbstractClientPlayerEntity self = MinecraftClient.getInstance().player;
        boolean isSelf = self != null && self.getGameProfile().getName().equals(state.name);
        return isSelf ? Config.replaceSelf : Config.replaceOthers;
    }

    private static void flushImmediate(VertexConsumerProvider provider) {
        if (provider instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw();
        }
    }
}
