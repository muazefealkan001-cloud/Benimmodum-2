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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class Nexus implements ClientModInitializer {

    // Elytra
    static boolean elytraControlEnabled = true;
    static double steerResponsiveness = 0.085;
    static double maxFlightSpeed = 2.45;
    static boolean autoRocketEnabled = true;
    static double minSpeedThreshold = 0.65;
    static int rocketCooldownTicks = 18;
    static boolean targetAssistEnabled = true;
    static double maxTargetRange = 48.0;

    // Gorsel efektler
    static boolean soupVisualsEnabled = true;
    static boolean hitParticlesEnabled = true;
    static boolean jumpRingsEnabled = true;
    static boolean trailParticlesEnabled = true;
    static boolean targetHudEnabled = true;
    static int effectIntensity = 12;
    static float ringRadius = 1.15f;

    // HUD
    static boolean hudMasterEnabled = true;
    static boolean showFpsHud = true;
    static boolean showCpsHud = true;
    static boolean showPingHud = true;
    static boolean showSpeedHud = true;
    static boolean showArmorHud = true;
    static boolean showKeystrokesHud = true;

    static int statsHudX = 10;
    static int statsHudY = 10;
    static int targetHudX = 10;
    static int targetHudY = 92;
    static int armorHudX = 10;
    static int armorHudY = 152;
    static int keysHudX = 10;
    static int keysHudY = 205;

    private static KeyMapping openMenuKey;
    private static int rocketCooldown = 0;
    private static Player lockedTarget = null;
    private static LivingEntity lastAttackedTarget = null;
    private static long lastAttackTimeMs = 0L;
    private static boolean wasOnGround = true;
    private static boolean wasLeft = false;
    private static boolean wasRight = false;

    private static final Deque<Long> leftClicksMs = new ArrayDeque<>();
    private static final Deque<Long> rightClicksMs = new ArrayDeque<>();
    private static final List<JumpRing> activeRings = new ArrayList<>();

    private record JumpRing(Vec3 pos, float radius, int maxAge, int age) {
        JumpRing advance() {
            return new JumpRing(pos, radius + (effectIntensity * 0.004f), maxAge, age + 1);
        }
    }

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("nexus", "main"));
        openMenuKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.nexus.menu", GLFW.GLFW_KEY_RIGHT_SHIFT, category));

        ClientTickEvents.END_CLIENT_TICK.register(Nexus::onClientTick);

        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            Identifier.fromNamespaceAndPath("nexus", "hud"),
            (graphics, tickCounter) -> {
                Minecraft mc = Minecraft.getInstance();
                if (!hudMasterEnabled || mc.player == null || mc.options.hideGui) return;
                renderHud(graphics, mc);
            }
        );
    }

    // ==================== Tick ====================

    private static int getCps(Deque<Long> clicks) {
        long now = System.currentTimeMillis();
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000L) {
            clicks.pollFirst();
        }
        return clicks.size();
    }

    private static void onClientTick(Minecraft mc) {
        if (rocketCooldown > 0) rocketCooldown--;

        while (openMenuKey.consumeClick()) {
            mc.setScreen(mc.screen instanceof NexusMenu ? null : new NexusMenu());
        }

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            lockedTarget = null;
            activeRings.clear();
            return;
        }

        long now = System.currentTimeMillis();
        boolean left = mc.options.keyAttack.isDown();
        boolean right = mc.options.keyUse.isDown();
        if (mc.screen == null) {
            if (left && !wasLeft) leftClicksMs.addLast(now);
            if (right && !wasRight) rightClicksMs.addLast(now);
        }
        wasLeft = left;
        wasRight = right;

        if (elytraControlEnabled && player.isFallFlying()) {
            applyElytraControl(player);
        }

        if (autoRocketEnabled && player.isFallFlying()
                && rocketCooldown == 0 && mc.gameMode != null) {
            tryAutoRocket(mc, player);
        }

        if (targetAssistEnabled) {
            updateLockedTarget(mc, player);
        } else {
            lockedTarget = null;
        }

        if (soupVisualsEnabled) {
            Entity attacked = mc.crosshairPickEntity;
            if (attacked instanceof LivingEntity living && left) {
                lastAttackedTarget = living;
                lastAttackTimeMs = now;
                if (hitParticlesEnabled) spawnHitBurst(mc, living);
            }

            if (jumpRingsEnabled && wasOnGround && !player.onGround()
                    && player.getDeltaMovement().y > 0.1) {
                activeRings.add(new JumpRing(
                    new Vec3(player.getX(), player.getY() + 0.05, player.getZ()),
                    ringRadius, 18, 0));
            }
            wasOnGround = player.onGround();

            if (trailParticlesEnabled
                    && (player.isFallFlying() || player.getDeltaMovement().horizontalDistance() > 0.25)) {
                mc.level.addParticle(
                    player.isFallFlying() ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK,
                    player.getX(), player.getY() + 0.4, player.getZ(),
                    0.0, 0.01, 0.0);
            }

            updateJumpRings(mc);
        }
    }

    private static void applyElytraControl(LocalPlayer player) {
        Vec3 vel = player.getDeltaMovement();
        double speed = vel.length();
        if (speed < 0.05) return;

        Vec3 look = player.getLookAngle().normalize();
        Vec3 desired = look.scale(Math.min(speed, maxFlightSpeed));
        player.setDeltaMovement(new Vec3(
            vel.x + (desired.x - vel.x) * steerResponsiveness,
            vel.y + (desired.y - vel.y) * steerResponsiveness,
            vel.z + (desired.z - vel.z) * steerResponsiveness));
    }

    private static void tryAutoRocket(Minecraft mc, LocalPlayer player) {
        if (player.getDeltaMovement().horizontalDistance() >= minSpeedThreshold) return;

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(Items.FIREWORK_ROCKET)) {
                mc.gameMode.useItem(player, hand);
                rocketCooldown = rocketCooldownTicks;
                return;
            }
        }
    }

    private static void updateLockedTarget(Minecraft mc, LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();

        lockedTarget = mc.level.players().stream()
            .filter(p -> p != player && p.isAlive() && !p.isSpectator())
            .filter(p -> p.distanceTo(player) <= maxTargetRange)
            .min(Comparator.comparingDouble(p -> angle(eye, look, p)))
            .orElse(null);
    }

    private static double angle(Vec3 eye, Vec3 look, Player target) {
        Vec3 to = target.getBoundingBox().getCenter().subtract(eye).normalize();
        double dot = Math.max(-1.0, Math.min(1.0, look.dot(to)));
        return Math.acos(dot);
    }

    private static void spawnHitBurst(Minecraft mc, LivingEntity target) {
        int count = Math.max(4, Math.min(32, effectIntensity));
        for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2.0 * i) / count;
            mc.level.addParticle(ParticleTypes.CRIT,
                target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                Math.cos(a) * 0.14, 0.08, Math.sin(a) * 0.14);
        }
    }

    private static void updateJumpRings(Minecraft mc) {
        List<JumpRing> next = new ArrayList<>();
        for (JumpRing ring : activeRings) {
            if (ring.age() >= ring.maxAge()) continue;
            int points = 16;
            for (int i = 0; i < points; i++) {
                double t = (Math.PI * 2.0 * i) / points;
                mc.level.addParticle(ParticleTypes.GLOW,
                    ring.pos().x + Math.cos(t) * ring.radius(),
                    ring.pos().y,
                    ring.pos().z + Math.sin(t) * ring.radius(),
                    0.0, 0.0, 0.0);
            }
            next.add(ring.advance());
        }
        activeRings.clear();
        activeRings.addAll(next);
    }

    // ==================== HUD ====================

    private static void border(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static void keyBox(GuiGraphics g, Minecraft mc, int x, int y,
                               int w, int h, String label, boolean pressed) {
        g.fill(x, y, x + w, y + h, pressed ? 0xCC38BDF8 : 0x881E293B);
        border(g, x, y, w, h, pressed ? 0xFFE0F2FE : 0xFF475569);
        int tx = x + (w - mc.font.width(label)) / 2;
        int ty = y + (h - 7) / 2;
        g.drawString(mc.font, label, tx, ty, pressed ? 0xFF0F172A : 0xFFF8FAFC, false);
    }

    private static void renderHud(GuiGraphics g, Minecraft mc) {
        LocalPlayer p = mc.player;

        // Istatistikler
        List<String> lines = new ArrayList<>();
        if (showFpsHud) lines.add("FPS: " + mc.getFps());
        if (showCpsHud) {
            lines.add("CPS: " + getCps(leftClicksMs) + " | " + getCps(rightClicksMs));
        }
        if (showPingHud) {
            int ping = 0;
            var conn = mc.getConnection();
            if (conn != null) {
                var info = conn.getPlayerInfo(p.getUUID());
                if (info != null) ping = info.getLatency();
            }
            lines.add("Ping: " + ping + " ms");
        }
        if (showSpeedHud) {
            double bps = p.getDeltaMovement().length() * 20.0;
            lines.add(String.format("Speed: %.1f b/s%s", bps,
                p.isFallFlying() ? " [ELYTRA]" : ""));
        }
        if (!lines.isEmpty()) {
            int w = 112;
            int h = 8 + lines.size() * 11;
            g.fill(statsHudX, statsHudY, statsHudX + w, statsHudY + h, 0x990D1424);
            border(g, statsHudX, statsHudY, w, h, 0xFF233554);
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(mc.font, lines.get(i),
                    statsHudX + 6, statsHudY + 5 + i * 11, 0xFFF8FAFC, true);
            }
        }

        // Hedef HUD
        LivingEntity target = lockedTarget;
        if (target == null && System.currentTimeMillis() - lastAttackTimeMs < 3500L) {
            target = lastAttackedTarget;
        }
        if (targetHudEnabled && target != null && target.isAlive()) {
            int tw = 142;
            int th = 42;
            g.fill(targetHudX, targetHudY, targetHudX + tw, targetHudY + th, 0xA8111827);
            border(g, targetHudX, targetHudY, tw, th, 0xFFEF4444);

            float hp = Math.max(0f, target.getHealth());
            float maxHp = Math.max(1f, target.getMaxHealth());
            float ratio = Math.max(0f, Math.min(1f, hp / maxHp));

            g.drawString(mc.font, target.getName().getString(),
                targetHudX + 7, targetHudY + 6, 0xFFF8FAFC, true);
            g.drawString(mc.font,
                String.format("%.1f HP - %.1fm", hp, target.distanceTo(p)),
                targetHudX + 7, targetHudY + 18, 0xFFCBD5E1, false);

            int bx = targetHudX + 7;
            int by = targetHudY + 30;
            int bw = tw - 14;
            g.fill(bx, by, bx + bw, by + 6, 0xFF1F2937);
            g.fill(bx, by, bx + Math.round(bw * ratio), by + 6, 0xFF22C55E);
        }

        // Zirh
        if (showArmorHud) {
            int aw = 96;
            int ah = 24;
            g.fill(armorHudX, armorHudY, armorHudX + aw, armorHudY + ah, 0x990D1424);
            border(g, armorHudX, armorHudY, aw, ah, 0xFF233554);

            EquipmentSlot[] slots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET
            };
            for (int i = 0; i < slots.length; i++) {
                ItemStack st = p.getItemBySlot(slots[i]);
                int ix = armorHudX + 5 + i * 22;
                int iy = armorHudY + 4;
                if (st.isEmpty()) continue;
                g.renderItem(st, ix, iy);
                if (st.isDamageableItem()) {
                    int max = st.getMaxDamage();
                    int pct = Math.round(((max - st.getDamageValue()) / (float) max) * 100f);
                    g.drawString(mc.font, pct + "%", ix, iy + 14,
                        pct > 25 ? 0xFF94A3B8 : 0xFFEF4444, false);
                }
            }
        }

        // Keystrokes
        if (showKeystrokesHud) {
            int kw = 76;
            int kh = 52;
            g.fill(keysHudX, keysHudY, keysHudX + kw, keysHudY + kh, 0x880D1424);
            border(g, keysHudX, keysHudY, kw, kh, 0xFF233554);

            keyBox(g, mc, keysHudX + 28, keysHudY + 4, 20, 14, "W", mc.options.keyUp.isDown());
            keyBox(g, mc, keysHudX + 6, keysHudY + 20, 20, 14, "A", mc.options.keyLeft.isDown());
            keyBox(g, mc, keysHudX + 28, keysHudY + 20, 20, 14, "S", mc.options.keyDown.isDown());
            keyBox(g, mc, keysHudX + 50, keysHudY + 20, 20, 14, "D", mc.options.keyRight.isDown());
            keyBox(g, mc, keysHudX + 6, keysHudY + 36, 31, 12, "LMB", mc.options.keyAttack.isDown());
            keyBox(g, mc, keysHudX + 39, keysHudY + 36, 31, 12, "RMB", mc.options.keyUse.isDown());
        }
    }

    // ==================== Menu ====================

    public static class NexusMenu extends Screen {

        private static int category = 0;

        public NexusMenu() {
            super(Component.literal("Nexus"));
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        protected void init() {
            String[] tabs = {"Elytra", "Gorsel", "HUD", "Konum"};
            int x0 = width / 2 - 144;
            for (int i = 0; i < tabs.length; i++) {
                final int idx = i;
                String label = (i == category ? "> " : "") + tabs[i];
                addRenderableWidget(Button.builder(Component.literal(label), b -> {
                    category = idx;
                    rebuildWidgets();
                }).bounds(x0 + i * 72, 28, 70, 20).build());
            }

            int y = 56;
            if (category == 0) {
                toggle(y, "Yon kontrolu", () -> elytraControlEnabled, v -> elytraControlEnabled = v);
                y += 22;
                stepper(y, "Donus", () -> String.format("%.3f", steerResponsiveness),
                    () -> steerResponsiveness = Math.max(0.01, steerResponsiveness - 0.01),
                    () -> steerResponsiveness = Math.min(0.25, steerResponsiveness + 0.01));
                y += 22;
                toggle(y, "Oto fisek", () -> autoRocketEnabled, v -> autoRocketEnabled = v);
                y += 22;
                stepper(y, "Maks hiz", () -> String.format("%.2f", maxFlightSpeed),
                    () -> maxFlightSpeed = Math.max(0.8, maxFlightSpeed - 0.1),
                    () -> maxFlightSpeed = Math.min(4.5, maxFlightSpeed + 0.1));
                y += 22;
                stepper(y, "Fisek esigi", () -> String.format("%.2f", minSpeedThreshold),
                    () -> minSpeedThreshold = Math.max(0.15, minSpeedThreshold - 0.05),
                    () -> minSpeedThreshold = Math.min(1.8, minSpeedThreshold + 0.05));
                y += 22;
                stepper(y, "Fisek bekleme", () -> rocketCooldownTicks + " t",
                    () -> rocketCooldownTicks = Math.max(5, rocketCooldownTicks - 2),
                    () -> rocketCooldownTicks = Math.min(60, rocketCooldownTicks + 2));
            } else if (category == 1) {
                toggle(y, "Efektler", () -> soupVisualsEnabled, v -> soupVisualsEnabled = v);
                y += 22;
                toggle(y, "Vurus parcacik", () -> hitParticlesEnabled, v -> hitParticlesEnabled = v);
                y += 22;
                toggle(y, "Ziplama halka", () -> jumpRingsEnabled, v -> jumpRingsEnabled = v);
                y += 22;
                toggle(y, "Iz parcacik", () -> trailParticlesEnabled, v -> trailParticlesEnabled = v);
                y += 22;
                toggle(y, "Hedef HUD", () -> targetHudEnabled, v -> targetHudEnabled = v);
                y += 22;
                stepper(y, "Yogunluk", () -> String.valueOf(effectIntensity),
                    () -> effectIntensity = Math.max(4, effectIntensity - 2),
                    () -> effectIntensity = Math.min(32, effectIntensity + 2));
            } else if (category == 2) {
                toggle(y, "HUD ana", () -> hudMasterEnabled, v -> hudMasterEnabled = v);
                y += 22;
                toggle(y, "FPS", () -> showFpsHud, v -> showFpsHud = v);
                y += 22;
                toggle(y, "CPS", () -> showCpsHud, v -> showCpsHud = v);
                y += 22;
                toggle(y, "Ping", () -> showPingHud, v -> showPingHud = v);
                y += 22;
                toggle(y, "Hiz", () -> showSpeedHud, v -> showSpeedHud = v);
                y += 22;
                toggle(y, "Zirh", () -> showArmorHud, v -> showArmorHud = v);
                y += 22;
                toggle(y, "Keystrokes", () -> showKeystrokesHud, v -> showKeystrokesHud = v);
            } else {
                toggle(y, "Hedef kilidi", () -> targetAssistEnabled, v -> targetAssistEnabled = v);
                y += 22;
                stepper(y, "Menzil", () -> (int) maxTargetRange + " m",
                    () -> maxTargetRange = Math.max(12, maxTargetRange - 4),
                    () -> maxTargetRange = Math.min(96, maxTargetRange + 4));
                y += 22;
                mover(y, "Istatistik", () -> statsHudX, v -> statsHudX = v,
                    () -> statsHudY, v -> statsHudY = v);
                y += 22;
                mover(y, "Hedef", () -> targetHudX, v -> targetHudX = v,
                    () -> targetHudY, v -> targetHudY = v);
                y += 22;
                mover(y, "Zirh", () -> armorHudX, v -> armorHudX = v,
                    () -> armorHudY, v -> armorHudY = v);
                y += 22;
                mover(y, "Tuslar", () -> keysHudX, v -> keysHudX = v,
                    () -> keysHudY, v -> keysHudY = v);
            }
        }

        private void toggle(int y, String label, BooleanSupplier get, Consumer<Boolean> set) {
            int x = width / 2 - 100;
            String text = label + ": " + (get.getAsBoolean() ? "ACIK" : "KAPALI");
            addRenderableWidget(Button.builder(Component.literal(text), b -> {
                set.accept(!get.getAsBoolean());
                rebuildWidgets();
            }).bounds(x, y, 200, 20).build());
        }

        private void stepper(int y, String label, Supplier<String> value,
                             Runnable dec, Runnable inc) {
            int x = width / 2 - 100;
            Button name = Button.builder(
                Component.literal(label + ": " + value.get()), b -> { })
                .bounds(x, y, 150, 20).build();
            name.active = false;
            addRenderableWidget(name);
            addRenderableWidget(Button.builder(Component.literal("-"), b -> {
                dec.run();
                rebuildWidgets();
            }).bounds(x + 152, y, 22, 20).build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> {
                inc.run();
                rebuildWidgets();
            }).bounds(x + 176, y, 22, 20).build());
        }

        private void mover(int y, String label, IntSupplier gx, IntConsumer sx,
                           IntSupplier gy, IntConsumer sy) {
            int x = width / 2 - 100;
            Button name = Button.builder(Component.literal(label), b -> { })
                .bounds(x, y, 100, 20).build();
            name.active = false;
            addRenderableWidget(name);
            addRenderableWidget(Button.builder(Component.literal("<"),
                b -> sx.accept(Math.max(0, gx.getAsInt() - 10)))
                .bounds(x + 102, y, 22, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"),
                b -> sx.accept(gx.getAsInt() + 10))
                .bounds(x + 126, y, 22, 20).build());
            addRenderableWidget(Button.builder(Component.literal("^"),
                b -> sy.accept(Math.max(0, gy.getAsInt() - 10)))
                .bounds(x + 150, y, 22, 20).build());
            addRenderableWidget(Button.builder(Component.literal("v"),
                b -> sy.accept(gy.getAsInt() + 10))
                .bounds(x + 174, y, 22, 20).build());
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            super.render(graphics, mouseX, mouseY, delta);
            graphics.drawString(font, "NEXUS", width / 2 - 144, 12, 0xFFB56CFF, true);
            graphics.drawString(font, "Kapat: ESC", width / 2 + 60, 12, 0xFFAAAAAA, true);
        }
    }
}
