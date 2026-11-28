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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Nexus implements ClientModInitializer {

    static final String TITLE = "NEXUS x AXOLOTL";

    static boolean badge = true;
    static boolean crossSpin = true;
    static boolean jumpCircles = true;
    static boolean hatSnow = true;

    private static KeyMapping openKey;
    private static final List<Ring> rings = new ArrayList<>();
    private static boolean wasOnGround = true;
    private static float spinAngle = 0f;
    private static float focus = 0f;
    private static int ticks = 0;

    private static final class Ring {
        final double x, y, z;
        final float start, end;
        final int maxAge;
        int age = 0;
        float radius;

        Ring(double x, double y, double z, float start, float end, int maxAge) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.start = start;
            this.end = end;
            this.maxAge = maxAge;
            this.radius = start;
        }

        void tick() {
            age++;
            float t = Math.min(1f, (float) age / maxAge);
            radius = start + (end - start) * t;
        }

        boolean expired() {
            return age >= maxAge;
        }
    }

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("nexus", "main"));
        openKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.nexus.menu", GLFW.GLFW_KEY_P, category));

        ClientTickEvents.END_CLIENT_TICK.register(Nexus::tick);

        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            Identifier.fromNamespaceAndPath("nexus", "hud"),
            (graphics, tickCounter) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null || mc.level == null || mc.options.hideGui) return;
                renderHud(graphics, mc);
            }
        );
    }

    // ==================== Tick ====================

    private static void tick(Minecraft mc) {
        ticks++;

        while (openKey.consumeClick()) {
            mc.setScreen(mc.screen instanceof NexusMenu ? null : new NexusMenu());
        }

        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            rings.clear();
            return;
        }

        // Jump circles (parcacik halkalari)
        if (jumpCircles) {
            boolean onGround = p.onGround();
            if (wasOnGround && !onGround && p.getDeltaMovement().y > 0.05) {
                rings.add(new Ring(p.getX(), p.getY() + 0.05, p.getZ(), 0.25f, 1.35f, 18));
            } else if (!wasOnGround && onGround) {
                rings.add(new Ring(p.getX(), p.getY() + 0.05, p.getZ(), 0.20f, 1.60f, 22));
            }
            wasOnGround = onGround;

            Iterator<Ring> it = rings.iterator();
            while (it.hasNext()) {
                Ring r = it.next();
                r.tick();
                if (r.expired()) {
                    it.remove();
                    continue;
                }
                int points = 14;
                for (int i = 0; i < points; i++) {
                    double a = (Math.PI * 2.0 * i) / points;
                    mc.level.addParticle(ParticleTypes.GLOW,
                        r.x + Math.cos(a) * r.radius, r.y, r.z + Math.sin(a) * r.radius,
                        0.0, 0.0, 0.0);
                }
            }
        } else {
            rings.clear();
        }

        // Sapka yerine bas ustunde donen kar halkasi
        if (hatSnow && ticks % 3 == 0) {
            for (int i = 0; i < 5; i++) {
                double a = ticks * 0.12 + i * (Math.PI * 2.0 / 5.0);
                mc.level.addParticle(ParticleTypes.SNOWFLAKE,
                    p.getX() + Math.cos(a) * 0.35, p.getY() + 2.1, p.getZ() + Math.sin(a) * 0.35,
                    0.0, 0.0, 0.0);
            }
        }

        // Crosshair spin animasyonu
        if (crossSpin) {
            boolean aiming = mc.crosshairPickEntity instanceof LivingEntity;
            float target = aiming ? 1.0f : 0.25f;
            focus += (target - focus) * 0.22f;
            spinAngle = (spinAngle + (aiming ? 6.5f : 2.0f)) % 360f;
        } else {
            focus = 0f;
        }
    }

    // ==================== HUD ====================

    private static void renderHud(GuiGraphics g, Minecraft mc) {
        int w = g.guiWidth();
        int h = g.guiHeight();

        if (badge) {
            int x = 10;
            int y = 10;
            g.fill(x, y, x + 120, y + 22, 0x99181C28);
            g.fill(x, y, x + 120, y + 2, 0xFFFF77B7);
            g.drawString(mc.font, TITLE, x + 8, y + 8, 0xFF8CF7FF, true);
        }

        if (crossSpin && focus > 0.02f) {
            int cx = w / 2;
            int cy = h / 2;
            float radius = 18f + (11f - 18f) * focus;

            for (int i = 0; i < 4; i++) {
                double a = Math.toRadians(spinAngle + i * 90.0);
                int color = (i % 2 == 0) ? 0xFFFF77B7 : 0xFF7CF8FF;
                for (int k = 0; k < 4; k++) {
                    int px = cx + (int) Math.round(Math.cos(a) * (radius + k * 3));
                    int py = cy + (int) Math.round(Math.sin(a) * (radius + k * 3));
                    g.fill(px - 1, py - 1, px + 1, py + 1, color);
                }
            }
        }
    }

    // ==================== Menu ====================

    public static class NexusMenu extends Screen {

        public NexusMenu() {
            super(Component.literal(TITLE));
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        protected void init() {
            int x = width / 2 - 100;
            int y = height / 2 - 50;
            toggle(x, y, "Rozet", () -> badge, v -> badge = v);
            toggle(x, y + 24, "Crosshair spin", () -> crossSpin, v -> crossSpin = v);
            toggle(x, y + 48, "Jump circles", () -> jumpCircles, v -> jumpCircles = v);
            toggle(x, y + 72, "Kar halkasi (sapka)", () -> hatSnow, v -> hatSnow = v);
        }

        private void toggle(int x, int y, String label,
                            java.util.function.BooleanSupplier get,
                            java.util.function.Consumer<Boolean> set) {
            addRenderableWidget(Button.builder(
                Component.literal(label + ": " + (get.getAsBoolean() ? "ACIK" : "KAPALI")), b -> {
                    set.accept(!get.getAsBoolean());
                    rebuildWidgets();
                }).bounds(x, y, 200, 20).build());
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
            int px = width / 2 - 112;
            int py = height / 2 - 82;
            g.fill(px, py, px + 224, py + 172, 0xCC121622);
            g.fill(px, py, px + 224, py + 2, 0xFFFF77B7);
            g.fill(px, py + 170, px + 224, py + 172, 0xFF7CF8FF);

            super.render(g, mouseX, mouseY, delta);

            g.drawString(font, TITLE, px + 10, py + 10, 0xFFFFFFFF, true);
            g.drawString(font, "P / ESC: kapat", px + 10, py + 158, 0xFFA0A8C0, false);
        }
    }
}
