package com.fpsboost;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class FpsBoostClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            Identifier.fromNamespaceAndPath("fpsboost", "fps_counter"),
            (graphics, tickCounter) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;

                int fps = mc.getFps();
                int color = fps >= 50 ? 0xFF55FF55 : fps >= 25 ? 0xFFFFFF55 : 0xFFFF5555;
                graphics.drawString(mc.font, fps + " FPS", 4, 4, color, true);
            }
        );
    }
}
