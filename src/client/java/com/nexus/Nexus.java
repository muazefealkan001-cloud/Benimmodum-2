package com.nexus;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Properties;

public class Nexus implements ClientModInitializer {

    public static final List<Module> MODULES = new ArrayList<>();
    private static KeyMapping guiKey;

    private static int hudX = 6;
    private static int hudY = 6;
    private static boolean ksOn = true;
    private static int ksX = 6;
    private static int ksY = 120;

    private static final Deque<Long> leftClicks = new ArrayDeque<>();
    private static final Deque<Long> rightClicks = new ArrayDeque<>();
    private static boolean wasLeft = false;
    private static boolean wasRight = false;

    @Override
    public void onInitializeClient() {
        MODULES.add(new Module("coords", "Koordinat", true) {
            String text(Minecraft mc) {
                return "XYZ: " + mc.player.getBlockX() + " "
                    + mc.player.getBlockY() + " " + mc.player.getBlockZ();
            }
        });
        MODULES.add(new Module("direction", "Yon", false) {
            String text(Minecraft mc) {
                float yaw = ((mc.player.getYRot() % 360) + 360) % 360;
                String[] dirs = {"G", "B", "K", "D"};
                int index = Math.round(yaw / 90f) % 4;
                return "Yon: " + dirs[index];
            }
        });
        MODULES.add(new Module("speed", "Hiz", false) {
            String text(Minecraft mc) {
                double dx = mc.player.getX() - mc.player.xo;
                double dz = mc.player.getZ() - mc.player.zo;
                double bps = Math.sqrt(dx * dx + dz * dz) * 20.0;
                return String.format("Hiz: %.2f blok/sn", bps);
            }
        });
        MODULES.add(new Module("fps", "FPS", true) {
            String text(Minecraft mc) {
                return "FPS: " + mc.getFps();
            }
        });
        MODULES.add(new Module("ping", "Ping", false) {
            String text(Minecraft mc) {
                var conn = mc.getConnection();
                if (conn == null) return "Ping: -";
                var info = conn.getPlayerInfo(mc.player.getUUID());
                if (info == null) return "Ping: -";
                return "Ping: " + info.getLatency() + " ms";
            }
        });
        MODULES.add(new Module("memory", "Bellek", false) {
            String text(Minecraft mc) {
                Runtime rt = Runtime.getRuntime();
                long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
                long max = rt.maxMemory() / (1024 * 1024);
                return "Bellek: " + used + " / " + max + " MB";
            }
        });
        MODULES.add(new Module("realclock", "Gercek saat", false) {
            private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");

            String text(Minecraft mc) {
                return "Saat: " + LocalTime.now().format(fmt);
            }
        });
        MODULES.add(new Module("gametime", "Oyun saati", false) {
            String text(Minecraft mc) {
                long t = (mc.level.getGameTime() + 6000) % 24000;
                long hours = t / 1000;
                long minutes = (t % 1000) * 60 / 1000;
                return String.format("Oyun saati: %02d:%02d", hours, minutes);
            }
        });
        MODULES.add(new Module("health", "Can", false) {
            String text(Minecraft mc) {
                return String.format("Can: %.1f", mc.player.getHealth());
            }
        });
        MODULES.add(new Module("hunger", "Aclik", false) {
            String text(Minecraft mc) {
                return "Aclik: " + mc.player.getFoodData().getFoodLevel();
            }
        });
        MODULES.add(new Module("dimension", "Boyut", false) {
            String text(Minecraft mc) {
                String s = mc.level.dimension().toString();
                int slash = s.lastIndexOf('/');
                if (slash >= 0) s = s.substring(slash + 1);
                s = s.replace("]", "").trim();
                int colon = s.indexOf(':');
                if (colon >= 0) s = s.substring(colon + 1);
                return "Boyut: " + s;
            }
        });

        load();

        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("nexus", "main"));
        guiKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.nexus.gui", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (guiKey.consumeClick()) {
                mc.setScreen(new NexusScreen());
            }
            if (mc.player == null) return;

            boolean left = mc.options.keyAttack.isDown();
            boolean right = mc.options.keyUse.isDown();
            if (mc.screen == null) {
                long now = System.currentTimeMillis();
                if (left && !wasLeft) leftClicks.addLast(now);
                if (right && !wasRight) rightClicks.addLast(now);
            }
            wasLeft = left;
            wasRight = right;
        });

        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            Identifier.fromNamespaceAndPath("nexus", "hud"),
            (graphics, tickCounter) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null || mc.level == null || mc.options.hideGui) return;
                renderText(graphics, mc);
                if (ksOn) renderKeys(graphics, mc);
            }
        );
    }

    // ==================== Cizim ====================

    private static void renderText(GuiGraphics g, Minecraft mc) {
        int y = hudY;
        for (Module m : MODULES) {
            if (!m.enabled) continue;
            int color = m.rainbow ? rainbowColor() : 0xFFFFFFFF;
            g.drawString(mc.font, m.text(mc), hudX, y, color, true);
            y += 12;
        }
    }

    private static int cps(Deque<Long> q) {
        long now = System.currentTimeMillis();
        while (!q.isEmpty() && now - q.peekFirst() > 1000) {
            q.removeFirst();
        }
        return q.size();
    }

    private static void box(GuiGraphics g, Minecraft mc, String label,
                            int x, int y, int w, int h, boolean pressed) {
        g.fill(x, y, x + w, y + h, pressed ? 0xB0FFFFFF : 0x80000000);
        int color = pressed ? 0xFF000000 : 0xFFFFFFFF;
        int tx = x + (w - mc.font.width(label)) / 2;
        int ty = y + (h - 8) / 2;
        g.drawString(mc.font, label, tx, ty, color, false);
    }

    private static void renderKeys(GuiGraphics g, Minecraft mc) {
        int s = 22;
        int gap = 2;
        int x = ksX;
        int y = ksY;

        box(g, mc, "W", x + s + gap, y, s, s, mc.options.keyUp.isDown());
        box(g, mc, "A", x, y + s + gap, s, s, mc.options.keyLeft.isDown());
        box(g, mc, "S", x + s + gap, y + s + gap, s, s, mc.options.keyDown.isDown());
        box(g, mc, "D", x + 2 * (s + gap), y + s + gap, s, s, mc.options.keyRight.isDown());

        int fullW = 3 * s + 2 * gap;
        int spaceY = y + 2 * (s + gap);
        box(g, mc, "SPACE", x, spaceY, fullW, 14, mc.options.keyJump.isDown());

        int mouseY = spaceY + 14 + gap;
        int half = (fullW - gap) / 2;
        box(g, mc, "L " + cps(leftClicks), x, mouseY, half, s,
            mc.options.keyAttack.isDown());
        box(g, mc, "R " + cps(rightClicks), x + half + gap, mouseY, half, s,
            mc.options.keyUse.isDown());
    }

    // ==================== Renk ====================

    static int rainbowColor() {
        float hue = (System.currentTimeMillis() % 3000L) / 3000f;
        return hsvToArgb(hue, 1f, 1f);
    }

    static int hsvToArgb(float h, float s, float v) {
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000
            | ((int) (r * 255) << 16)
            | ((int) (g * 255) << 8)
            | (int) (b * 255);
    }

    // ==================== Kayit / yukleme ====================

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("nexus.properties");
    }

    static void save() {
        Properties p = new Properties();
        for (Module m : MODULES) {
            p.setProperty(m.key + ".on", String.valueOf(m.enabled));
            p.setProperty(m.key + ".rainbow", String.valueOf(m.rainbow));
        }
        p.setProperty("hud.x", String.valueOf(hudX));
        p.setProperty("hud.y", String.valueOf(hudY));
        p.setProperty("ks.on", String.valueOf(ksOn));
        p.setProperty("ks.x", String.valueOf(ksX));
        p.setProperty("ks.y", String.valueOf(ksY));
        try (OutputStream out = Files.newOutputStream(configPath())) {
            p.store(out, "Nexus ayarlari");
        } catch (IOException e) {
            System.err.println("[Nexus] Ayarlar kaydedilemedi: " + e.getMessage());
        }
    }

    static void load() {
        Path path = configPath();
        if (!Files.exists(path)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            p.load(in);
        } catch (IOException e) {
            System.err.println("[Nexus] Ayarlar okunamadi: " + e.getMessage());
            return;
        }
        try {
            for (Module m : MODULES) {
                m.enabled = Boolean.parseBoolean(
                    p.getProperty(m.key + ".on", String.valueOf(m.enabled)));
                m.rainbow = Boolean.parseBoolean(
                    p.getProperty(m.key + ".rainbow", String.valueOf(m.rainbow)));
            }
            hudX = Integer.parseInt(p.getProperty("hud.x", String.valueOf(hudX)));
            hudY = Integer.parseInt(p.getProperty("hud.y", String.valueOf(hudY)));
            ksOn = Boolean.parseBoolean(p.getProperty("ks.on", String.valueOf(ksOn)));
            ksX = Integer.parseInt(p.getProperty("ks.x", String.valueOf(ksX)));
            ksY = Integer.parseInt(p.getProperty("ks.y", String.valueOf(ksY)));
        } catch (NumberFormatException ignored) {
        }
    }

    // ==================== Modul yapisi ====================

    public static abstract class Module {
        public final String key;
        public final String name;
        public boolean enabled;
        public boolean rainbow;

        protected Module(String key, String name, boolean enabled) {
            this.key = key;
            this.name = name;
            this.enabled = enabled;
        }

        abstract String text(Minecraft mc);
    }

    // ==================== Menu ====================

    public static class NexusScreen extends Screen {

        public NexusScreen() {
            super(Component.literal("Nexus"));
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        protected void init() {
            int perCol = 6;
            for (int i = 0; i < MODULES.size(); i++) {
                final Module m = MODULES.get(i);
                int col = i / perCol;
                int row = i % perCol;
                int bx = 10 + col * 170;
                int by = 30 + row * 22;

                String label = m.name + ": " + (m.enabled ? "ACIK" : "KAPALI");
                addRenderableWidget(Button.builder(Component.literal(label), b -> {
                    m.enabled = !m.enabled;
                    save();
                    rebuildWidgets();
                }).bounds(bx, by, 110, 20).build());

                String rl = m.rainbow ? "RGB*" : "RGB";
                addRenderableWidget(Button.builder(Component.literal(rl), b -> {
                    m.rainbow = !m.rainbow;
                    save();
                    rebuildWidgets();
                }).bounds(bx + 112, by, 44, 20).build());
            }

            int y = 30 + perCol * 22 + 8;

            addRenderableWidget(Button.builder(Component.literal("HUD <"), b -> {
                hudX = Math.max(0, hudX - 10);
                save();
            }).bounds(10, y, 40, 20).build());
            addRenderableWidget(Button.builder(Component.literal("HUD >"), b -> {
                hudX += 10;
                save();
            }).bounds(52, y, 40, 20).build());
            addRenderableWidget(Button.builder(Component.literal("HUD ^"), b -> {
                hudY = Math.max(0, hudY - 10);
                save();
            }).bounds(94, y, 40, 20).build());
            addRenderableWidget(Button.builder(Component.literal("HUD v"), b -> {
                hudY += 10;
                save();
            }).bounds(136, y, 40, 20).build());
            y += 24;

            addRenderableWidget(Button.builder(
                Component.literal("Keystrokes: " + (ksOn ? "ACIK" : "KAPALI")), b -> {
                    ksOn = !ksOn;
                    save();
                    rebuildWidgets();
                }).bounds(10, y, 166, 20).build());
            y += 24;

            addRenderableWidget(Button.builder(Component.literal("Tus <"), b -> {
                ksX = Math.max(0, ksX - 10);
                save();
            }).bounds(10, y, 40, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Tus >"), b -> {
                ksX += 10;
                save();
            }).bounds(52, y, 40, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Tus ^"), b -> {
                ksY = Math.max(0, ksY - 10);
                save();
            }).bounds(94, y, 40, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Tus v"), b -> {
                ksY += 10;
                save();
            }).bounds(136, y, 40, 20).build());
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            super.render(graphics, mouseX, mouseY, delta);
            graphics.drawString(font, "Nexus", 10, 10, 0xFFB56CFF, true);
            graphics.drawString(font, "Cikmak icin ESC", 10, 20, 0xFFAAAAAA, true);
        }
    }
}
