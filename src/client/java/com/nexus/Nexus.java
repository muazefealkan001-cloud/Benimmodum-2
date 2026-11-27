package com.nexus;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

public class NexusMod implements ClientModInitializer {

    public static final String MOD_ID = "nexus";
    public static final String MOD_NAME = "nexus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Elytra PvP Ayarları
    public static boolean elytraControlEnabled = true;
    public static double steerResponsiveness = 0.085;
    public static double maxFlightSpeed = 2.45;

    public static boolean autoRocketEnabled = true;
    public static double minSpeedThreshold = 0.65;
    public static int rocketCooldownTicks = 18;

    public static boolean targetAssistEnabled = true;
    public static double maxTargetRange = 48.0;

    // Soup Visual Tarzı Ayarlar
    public static boolean soupVisualsEnabled = true;
    public static boolean hitParticlesEnabled = true;
    public static boolean jumpRingsEnabled = true;
    public static boolean trailParticlesEnabled = true;
    public static boolean targetHudEnabled = true;
    public static int effectIntensity = 12;
    public static float ringRadius = 1.15f;

    // AxolotlClient Tarzı HUD Ayarları
    public static boolean hudMasterEnabled = true;
    public static boolean showFpsHud = true;
    public static boolean showCpsHud = true;
    public static boolean showPingHud = true;
    public static boolean showSpeedHud = true;
    public static boolean showArmorHud = true;
    public static boolean showKeystrokesHud = true;

    public static int statsHudX = 10;
    public static int statsHudY = 10;
    public static int targetHudX = 10;
    public static int targetHudY = 92;
    public static int armorHudX = 10;
    public static int armorHudY = 152;
    public static int keysHudX = 10;
    public static int keysHudY = 205;

    private static KeyBinding openMenuKey;
    private static int rocketCooldown = 0;
    private static PlayerEntity lockedTarget = null;
    private static LivingEntity lastAttackedTarget = null;
    private static long lastAttackTimeMs = 0L;
    private static boolean wasOnGround = true;
    private static boolean prevAttackPressed = false;
    private static boolean prevUsePressed = false;

    private static final Deque<Long> leftClicksMs = new ArrayDeque<>();
    private static final Deque<Long> rightClicksMs = new ArrayDeque<>();
    private static final List<JumpRing> activeRings = new ArrayList<>();

    private record JumpRing(Vec3d pos, float radius, int maxAge, int age) {
        JumpRing advance(int intensity) {
            return new JumpRing(pos, radius + (intensity * 0.004f), maxAge, age + 1);
        }
    }

    public static PlayerEntity getLockedTarget() {
        return lockedTarget;
    }

    public static int getCps(Deque<Long> clicks) {
        long now = System.currentTimeMillis();
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000L) {
            clicks.pollFirst();
        }
        return clicks.size();
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("[{}] 1.21.11 Elytra PvP + Soup Visual + Axolotl HUD yüklendi.", MOD_NAME);

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.nexus.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        HudRenderCallback.EVENT.register(this::onHudRender);
    }

    private void onClientTick(MinecraftClient client) {
        if (rocketCooldown > 0) {
            rocketCooldown--;
        }

        while (openMenuKey.wasPressed()) {
            if (client.currentScreen instanceof NexusMenuScreen) {
                client.setScreen(null);
            } else {
                client.setScreen(new NexusMenuScreen());
            }
        }

        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            lockedTarget = null;
            activeRings.clear();
            return;
        }

        long now = System.currentTimeMillis();
        boolean attackDown = client.options.attackKey.isPressed();
        boolean useDown = client.options.useKey.isPressed();

        if (attackDown && !prevAttackPressed) {
            leftClicksMs.addLast(now);
        }
        if (useDown && !prevUsePressed) {
            rightClicksMs.addLast(now);
        }
        prevAttackPressed = attackDown;
        prevUsePressed = useDown;

        if (elytraControlEnabled && player.isGliding()) {
            applyElytraControl(player);
        }

        if (autoRocketEnabled && player.isGliding() && rocketCooldown == 0 && client.interactionManager != null) {
            tryAutoRocket(client, player);
        }

        if (targetAssistEnabled) {
            updateLockedTarget(client, player);
        } else {
            lockedTarget = null;
        }

        if (soupVisualsEnabled) {
            Entity attacked = client.targetedEntity;
            if (attacked instanceof LivingEntity living && attackDown) {
                lastAttackedTarget = living;
                lastAttackTimeMs = now;
                if (hitParticlesEnabled) {
                    spawnHitBurst(client, living);
                }
            }

            if (jumpRingsEnabled && wasOnGround && !player.isOnGround() && player.getVelocity().y > 0.1) {
                activeRings.add(new JumpRing(
                        new Vec3d(player.getX(), player.getY() + 0.05, player.getZ()),
                        ringRadius,
                        18,
                        0
                ));
            }
            wasOnGround = player.isOnGround();

            if (trailParticlesEnabled && (player.isGliding() || player.getVelocity().horizontalLength() > 0.25)) {
                client.world.addParticleClient(
                        player.isGliding() ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK,
                        player.getX(), player.getY() + 0.4, player.getZ(),
                        0.0, 0.01, 0.0
                );
            }

            updateJumpRings(client);
        }
    }

    private void applyElytraControl(ClientPlayerEntity player) {
        Vec3d currentVel = player.getVelocity();
        double currentSpeed = currentVel.length();
        if (currentSpeed < 0.05 || Double.isNaN(currentSpeed)) {
            return;
        }

        Vec3d lookDir = player.getRotationVector().normalize();
        double targetSpeed = Math.min(currentSpeed, maxFlightSpeed);
        Vec3d desiredVel = lookDir.multiply(targetSpeed);

        double alpha = MathHelper.clamp(steerResponsiveness, 0.01, 0.50);
        player.setVelocity(new Vec3d(
                currentVel.x + (desiredVel.x - currentVel.x) * alpha,
                currentVel.y + (desiredVel.y - currentVel.y) * alpha,
                currentVel.z + (desiredVel.z - currentVel.z) * alpha
        ));
    }

    private void tryAutoRocket(MinecraftClient client, ClientPlayerEntity player) {
        if (player.getVelocity().horizontalLength() >= minSpeedThreshold) {
            return;
        }

        int rocketSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(Items.FIREWORK_ROCKET)) {
                rocketSlot = i;
                break;
            }
        }

        if (rocketSlot == -1 || client.interactionManager == null) {
            return;
        }

        int previousSlot = player.getInventory().selectedSlot;
        player.getInventory().selectedSlot = rocketSlot;
        client.interactionManager.interactItem(player, Hand.MAIN_HAND);
        player.getInventory().selectedSlot = previousSlot;

        rocketCooldown = rocketCooldownTicks;
    }

    private void updateLockedTarget(MinecraftClient client, ClientPlayerEntity player) {
        if (client.world == null) {
            lockedTarget = null;
            return;
        }

        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = player.getRotationVector().normalize();

        lockedTarget = client.world.getPlayers().stream()
                .filter(p -> p != player && p.isAlive() && !p.isSpectator())
                .filter(p -> p.distanceTo(player) <= maxTargetRange)
                .min(Comparator.comparingDouble(p -> angularDistance(eyePos, lookVec, p)))
                .orElse(null);
    }

    private double angularDistance(Vec3d eyePos, Vec3d lookVec, PlayerEntity target) {
        Vec3d toTarget = target.getBoundingBox().getCenter().subtract(eyePos).normalize();
        double dot = Math.max(-1.0, Math.min(1.0, lookVec.dotProduct(toTarget)));
        return Math.acos(dot);
    }

    private void spawnHitBurst(MinecraftClient client, LivingEntity target) {
        if (client.world == null) {
            return;
        }
        int count = Math.max(4, Math.min(32, effectIntensity));
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 * i) / count;
            double vx = Math.cos(angle) * 0.14;
            double vz = Math.sin(angle) * 0.14;
            client.world.addParticleClient(
                    ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getHeight() * 0.6, target.getZ(),
                    vx, 0.08, vz
            );
        }
    }

    private void updateJumpRings(MinecraftClient client) {
        if (client.world == null) {
            activeRings.clear();
            return;
        }

        List<JumpRing> nextRings = new ArrayList<>();
        Iterator<JumpRing> it = activeRings.iterator();
        while (it.hasNext()) {
            JumpRing ring = it.next();
            if (ring.age() >= ring.maxAge()) {
                continue;
            }
            int points = 16;
            for (int i = 0; i < points; i++) {
                double theta = (Math.PI * 2.0 * i) / points;
                double px = ring.pos().x + Math.cos(theta) * ring.radius();
                double pz = ring.pos().z + Math.sin(theta) * ring.radius();
                client.world.addParticleClient(ParticleTypes.GLOW, px, ring.pos().y, pz, 0.0, 0.0, 0.0);
            }
            nextRings.add(ring.advance(effectIntensity));
        }
        activeRings.clear();
        activeRings.addAll(nextRings);
    }

    private void onHudRender(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!hudMasterEnabled || mc.player == null || mc.currentScreen instanceof NexusMenuScreen) {
            return;
        }
        renderAllHudModules(ctx, mc.textRenderer, mc, false);
    }

    private static void renderAllHudModules(DrawContext ctx, TextRenderer tr, MinecraftClient mc, boolean previewMode) {
        if (mc.player == null) {
            return;
        }

        List<String> lines = new ArrayList<>();
        if (showFpsHud) {
            lines.add("FPS: " + mc.getCurrentFps());
        }
        if (showCpsHud) {
            lines.add("CPS: " + getCps(leftClicksMs) + " | " + getCps(rightClicksMs));
        }
        if (showPingHud) {
            int ping = 0;
            if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) != null) {
                ping = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()).getLatency();
            }
            lines.add("Ping: " + ping + " ms");
        }
        if (showSpeedHud) {
            double bps = mc.player.getVelocity().length() * 20.0;
            lines.add(String.format("Speed: %.1f b/s%s", bps, mc.player.isGliding() ? " [ELYTRA]" : ""));
        }

        if (!lines.isEmpty()) {
            int boxW = 116;
            int boxH = 8 + lines.size() * 11;
            ctx.fill(statsHudX, statsHudY, statsHudX + boxW, statsHudY + boxH, 0x990D1424);
            drawRectBorder(ctx, statsHudX, statsHudY, boxW, boxH, previewMode ? 0xFF38BDF8 : 0xFF233554);
            for (int i = 0; i < lines.size(); i++) {
                ctx.drawText(tr, lines.get(i), statsHudX + 6, statsHudY + 5 + i * 11, 0xFFF8FAFC, true);
            }
        }

        LivingEntity target = lockedTarget;
        if (target == null && System.currentTimeMillis() - lastAttackTimeMs < 3500L) {
            target = lastAttackedTarget;
        }
        if (target == null && previewMode) {
            target = mc.player;
        }

        if (targetHudEnabled && target != null) {
            int tw = 144;
            int th = 42;
            ctx.fill(targetHudX, targetHudY, targetHudX + tw, targetHudY + th, 0xA8111827);
            drawRectBorder(ctx, targetHudX, targetHudY, tw, th, previewMode ? 0xFF38BDF8 : 0xFFEF4444);

            String name = target.getName().getString();
            float hp = Math.max(0f, target.getHealth());
            float maxHp = Math.max(1f, target.getMaxHealth());
            float ratio = MathHelper.clamp(hp / maxHp, 0f, 1f);

            ctx.drawText(tr, name, targetHudX + 7, targetHudY + 6, 0xFFF8FAFC, true);
            ctx.drawText(tr, String.format("%.1f HP  •  %.1fm", hp, target.distanceTo(mc.player)),
                    targetHudX + 7, targetHudY + 18, 0xFFCBD5E1, false);

            int barX = targetHudX + 7;
            int barY = targetHudY + 30;
            int barW = tw - 14;
            int barH = 6;
            ctx.fill(barX, barY, barX + barW, barY + barH, 0xFF1F2937);
            ctx.fill(barX, barY, barX + Math.round(barW * ratio), barY + barH, 0xFF22C55E);
        }

        if (showArmorHud) {
            int aw = 104;
            int ah = 26;
            ctx.fill(armorHudX, armorHudY, armorHudX + aw, armorHudY + ah, 0x990D1424);
            drawRectBorder(ctx, armorHudX, armorHudY, aw, ah, previewMode ? 0xFF38BDF8 : 0xFF233554);

            EquipmentSlot[] slots = {
                    EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.FEET
            };
            for (int i = 0; i < slots.length; i++) {
                ItemStack st = mc.player.getEquippedStack(slots[i]);
                int ix = armorHudX + 5 + i * 24;
                int iy = armorHudY + 4;
                if (st != null && !st.isEmpty()) {
                    ctx.drawItem(st, ix, iy);
                    if (st.isDamageable() && st.getMaxDamage() > 0) {
                        int pct = Math.round(((st.getMaxDamage() - st.getDamage()) / (float) st.getMaxDamage()) * 100f);
                        ctx.drawText(tr, pct + "%", ix, iy + 15, pct > 25 ? 0xFF94A3B8 : 0xFFEF4444, false);
                    }
                }
            }
        }

        if (showKeystrokesHud) {
            int kw = 76;
            int kh = 52;
            ctx.fill(keysHudX, keysHudY, keysHudX + kw, keysHudY + kh, 0x880D1424);
            drawRectBorder(ctx, keysHudX, keysHudY, kw, kh, previewMode ? 0xFF38BDF8 : 0xFF233554);

            drawKeyBox(ctx, tr, keysHudX + 28, keysHudY + 4, 20, 14, "W", mc.options.forwardKey.isPressed());
            drawKeyBox(ctx, tr, keysHudX + 6, keysHudY + 20, 20, 14, "A", mc.options.leftKey.isPressed());
            drawKeyBox(ctx, tr, keysHudX + 28, keysHudY + 20, 20, 14, "S", mc.options.backKey.isPressed());
            drawKeyBox(ctx, tr, keysHudX + 50, keysHudY + 20, 20, 14, "D", mc.options.rightKey.isPressed());
            drawKeyBox(ctx, tr, keysHudX + 6, keysHudY + 36, 31, 12, "LMB", mc.options.attackKey.isPressed());
            drawKeyBox(ctx, tr, keysHudX + 39, keysHudY + 36, 31, 12, "RMB", mc.options.useKey.isPressed());
        }
    }

    private static void drawKeyBox(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String label, boolean pressed) {
        ctx.fill(x, y, x + w, y + h, pressed ? 0xCC38BDF8 : 0x881E293B);
        drawRectBorder(ctx, x, y, w, h, pressed ? 0xFFE0F2FE : 0xFF475569);
        int tx = x + (w - tr.getWidth(label)) / 2;
        int ty = y + (h - 7) / 2;
        ctx.drawText(tr, label, tx, ty, pressed ? 0xFF0F172A : 0xFFF8FAFC, false);
    }

    private static void drawRectBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static class NexusMenuScreen extends Screen {

        protected NexusMenuScreen() {
            super(Text.literal("Nexus Menu"));
        }

        @Override
        protected void init() {
            int cx = this.width / 2 - 110;
            int cy = 40;
            int w = 220;
            int h = 20;
            int gap = 24;

            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Elytra Kontrol: " + (elytraControlEnabled ? "AÇIK" : "KAPALI")),
                    btn -> {
                        elytraControlEnabled = !elytraControlEnabled;
                        btn.setMessage(Text.literal("Elytra Kontrol: " + (elytraControlEnabled ? "AÇIK" : "KAPALI")));
                    }).dimensions(cx, cy, w, h).build());

            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Oto Fişek: " + (autoRocketEnabled ? "AÇIK" : "KAPALI")),
                    btn -> {
                        autoRocketEnabled = !autoRocketEnabled;
                        btn.setMessage(Text.literal("Oto Fişek: " + (autoRocketEnabled ? "AÇIK" : "KAPALI")));
                    }).dimensions(cx, cy + gap, w, h).build());

            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Hedef Takip: " + (targetAssistEnabled ? "AÇIK" : "KAPALI")),
                    btn -> {
                        targetAssistEnabled = !targetAssistEnabled;
                        btn.setMessage(Text.literal("Hedef Takip: " + (targetAssistEnabled ? "AÇIK" : "KAPALI")));
                    }).dimensions(cx, cy + gap * 2, w, h).build());

            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Soup Visual: " + (soupVisualsEnabled ? "AÇIK" : "KAPALI")),
                    btn -> {
                        soupVisualsEnabled = !soupVisualsEnabled;
                        btn.setMessage(Text.literal("Soup Visual: " + (soupVisualsEnabled ? "AÇIK" : "KAPALI")));
                    }).dimensions(cx, cy + gap * 3, w, h).build());

            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Target HUD: " + (targetHudEnabled ? "AÇIK" : "KAPALI")),
                    btn -> {
                        targetHudEnabled = !targetHudEnabled;
                        btn.setMessage(Text.literal("Target HUD: " + (targetHudEnabled ? "AÇIK" : "KAPALI")));
                    }).dimensions(cx, cy + gap * 4, w, h).build());

            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Axolotl HUD: " + (hudMasterEnabled ? "AÇIK" : "KAPALI")),
                    btn -> {
                        hudMasterEnabled = !hudMasterEnabled;
                        btn.setMessage(Text.literal("Axolotl HUD: " + (hudMasterEnabled ? "AÇIK" : "KAPALI")));
                    }).dimensions(cx, cy + gap * 5, w, h).build());

            addDrawableChild(ButtonWidget.builder(
                    Text.literal("HUD Konumlarını Sıfırla"),
                    btn -> {
                        statsHudX = 10; statsHudY = 10;
                        targetHudX = 10; targetHudY = 92;
                        armorHudX = 10; armorHudY = 152;
                        keysHudX = 10; keysHudY = 205;
                    }).dimensions(cx, cy + gap * 6, w, h).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, this.width, this.height, 0x88060A13);
            if (hudMasterEnabled && this.client != null) {
                renderAllHudModules(context, this.textRenderer, this.client, true);
            }
            context.drawCenteredTextWithShadow(this.textRenderer, "NEXUS // ELYTRA + SOUP + AXOLOTL (1.21.11)",
                    this.width / 2, 18, 0xFF38BDF8);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }
}

