package com.stretchres;

import com.mojang.blaze3d.platform.Window;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public class StretchClient implements ClientModInitializer {
    public static boolean stretched = false;
    // 4:3 = 1.3333, 1:1 = 1.0, 5:4 = 1.25
    public static final float TARGET_RATIO = 4f / 3f;

    private static KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category cat = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("stretchres", "main"));
        toggleKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.stretchres.toggle", GLFW.GLFW_KEY_LEFT_SHIFT, cat));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (toggleKey.consumeClick()) {
                stretched = !stretched;
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                        Component.literal("Stretch: " + (stretched ? "AÇIK" : "KAPALI")), true);
                }
            }
        });
    }
}

@Mixin(GameRenderer.class)
class GameRendererMixin {
    @Inject(method = "getProjectionMatrix", at = @At("RETURN"), cancellable = true, require = 0)
    private void stretchres$stretch(float fov, CallbackInfoReturnable<Matrix4f> cir) {
        if (!StretchClient.stretched) return;
        Window w = Minecraft.getInstance().getWindow();
        float current = (float) w.getWidth() / (float) w.getHeight();
        float factor = current / StretchClient.TARGET_RATIO;
        cir.setReturnValue(new Matrix4f(cir.getReturnValue()).scale(factor, 1f, 1f));
    }
}
