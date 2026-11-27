package com.nexus;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class Nexus implements ClientModInitializer {

    public static final List<Module> MODULES = new ArrayList<>();
    private static KeyMapping guiKey;

    @Override
    public void onInitializeClient() {
        MODULES.add(new CoordsModule());
        MODULES.add(new FpsModule());

        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("nexus", "main"));
        guiKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.nexus.gui", GLFW.GLFW_KEY_RIGHT_SHIFT, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (guiKey.consumeClick()) {
                mc.setScreen(new NexusScreen());
            }
        });

        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            Identifier.fromNamespaceAndPath("nexus", "hud"),
            (graphics, tickCounter) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null || mc.options.hideGui) return;
                int lineY = 4;
                for (Module module : MODULES) {
                    if (!module.enabled) continue;
                    String text = module.text(mc);
                    graphics.drawString(mc.font, text, module.x, module.y + lineY, 0xFFFFFFFF, true);
                    lineY += 12;
                }
            }
        );
    }

    public static abstract class Module {
        public final String name;
        public boolean enabled;
        public int x;
        public int y;

        protected Module(String name, int x, int y, boolean enabled) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.enabled = enabled;
        }

        public abstract String text(Minecraft mc);
    }

    public static class CoordsModule extends Module {
        public CoordsModule() {
            super("Koordinat", 4, 4, true);
        }

        @Override
        public String text(Minecraft mc) {
            return "XYZ: " + mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ();
        }
    }

    public static class FpsModule extends Module {
        public FpsModule() {
            super("FPS", 4, 4, true);
        }

        @Override
        public String text(Minecraft mc) {
            return "FPS: " + mc.getFps();
        }
    }

    public static class NexusScreen extends Screen {

        public NexusScreen() {
            super(Component.literal("Nexus"));
        }

        @Override
        protected void init() {
            for (int i = 0; i < MODULES.size(); i++) {
                final Module m = MODULES.get(i);
                String label = (m.enabled ? "[ACIK] " : "[KAPALI] ") + m.name;
                addRenderableWidget(Button.builder(Component.literal(label), b -> {
                    m.enabled = !m.enabled;
                    rebuildWidgets();
                }).bounds(10, 40 + i * 24, 160, 20).build());
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            super.render(graphics, mouseX, mouseY, delta);
            graphics.drawString(font, "Nexus", 10, 10, 0xFFFFFFFF, true);
            graphics.drawString(font, "Modüle tıkla: aç/kapat. Çıkmak için ESC.",
                10, height - 14, 0xFFAAAAAA, true);
        }
    }
}
