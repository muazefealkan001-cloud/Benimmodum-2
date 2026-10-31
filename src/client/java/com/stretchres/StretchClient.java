package com.stretchres;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class StretchClient implements ClientModInitializer {
    public static boolean stretched = false;
    public static final float TARGET_RATIO = 4f / 3f;

    private static KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category cat = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("stretchres", "main"));
toggleKey = KeyBindingHelper.registerKeyBinding(
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
