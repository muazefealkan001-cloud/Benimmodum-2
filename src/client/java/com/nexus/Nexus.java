package com.nexus;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
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
import net.minecraft.util.Identifier;
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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class NexusMod implements ClientModInitializer {

    public static final String MOD_ID = "nexus";
    public static final String MOD_NAME = "nexus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Elytra PvP Modülleri
    public static boolean elytraControlEnabled = true;
    public static double steerResponsiveness = 0.085;
    public static double maxFlightSpeed = 2.45;

    public static boolean autoRocketEnabled = true;
    public static double minSpeedThreshold = 0.65;
    public static int rocketCooldownTicks = 18;

    public static boolean targetAssistEnabled = true;
    public static double maxTargetRange = 48.0;

    // Soup Visual Tarzı Efekt Ayarları
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

    // HUD Kutularının Ekran Koordinatları
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

        HudElementRegistry.addLast(
                Identifier.of(MOD_ID, "hud_overlay"),
                this::onHudRender
        );
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

        private static final int COLOR_BACKDROP = 0x88060A13;
        private static final int COLOR_PANEL = 0xF2101726;
        private static final int COLOR_PANEL_HEADER = 0xFF162138;
        private static final int COLOR_CARD = 0xFF172238;
        private static final int COLOR_CARD_HOVER = 0xFF1E2C47;
        private static final int COLOR_BORDER = 0xFF2B3F66;
        private static final int COLOR_ACCENT = 0xFF38BDF8;
        private static final int COLOR_ACCENT_SOFT = 0xFF0EA5E9;
        private static final int COLOR_ON = 0xFF22C55E;
        private static final int COLOR_OFF = 0xFFEF4444;
        private static final int COLOR_TEXT = 0xFFF8FAFC;
        private static final int COLOR_MUTED = 0xFF94A3B8;

        private int selectedCategory = 0;
        private final List<ControlRow> rows = new ArrayList<>();
        private String activeSliderId = null;
        private String draggingHudId = null;
        private int dragOffsetX = 0;
        private int dragOffsetY = 0;

        protected NexusMenuScreen() {
            super(Text.literal("Nexus Menu"));
        }

        @Override
        protected void init() {
            rebuildRows();
        }

        private void rebuildRows() {
            rows.clear();
            if (selectedCategory == 0) {
                rows.add(new ToggleRow("elytra", "Elytra Yön Kontrolü", "Hız vektörünü bakış yönüne hizalar",
                        () -> elytraControlEnabled, v -> elytraControlEnabled = v));
                rows.add(new SliderRow("steer", "Dönüş Hassasiyeti", "Yüksek değer daha keskin dönüş sağlar",
                        0.01, 0.25, () -> steerResponsiveness, v -> steerResponsiveness = v, "%.3f"));
                rows.add(new ToggleRow("rocket", "Otomatik Havai Fişek", "Hız düştüğünde hotbard'daki fişeği kullanır",
                        () -> autoRocketEnabled, v -> autoRocketEnabled = v));
                rows.add(new SliderRow("max_spd", "Maksimum Uçuş Hızı", "Süzülme hız tavanı",
                        0.80, 4.50, () -> maxFlightSpeed, v -> maxFlightSpeed = v, "%.2f"));
            } else if (selectedCategory == 1) {
                rows.add(new ToggleRow("soup", "Soup Visual Efektleri", "Vuruş parçacıkları, halkalar ve izleri açar",
                        () -> soupVisualsEnabled, v -> soupVisualsEnabled = v));
                rows.add(new ToggleRow("thud", "Soup Target HUD", "Hedef can, mesafe ve bar göstergesi",
                        () -> targetHudEnabled, v -> targetHudEnabled = v));
                rows.add(new ToggleRow("rings", "Zıplama Halkaları & İz", "Zıplama halkası ve hareket izi parçacıkları",
                        () -> jumpRingsEnabled && trailParticlesEnabled,
                        v -> {
                            jumpRingsEnabled = v;
                            trailParticlesEnabled = v;
                        }));
                rows.add(new SliderRow("intens", "Efekt Yoğunluğu", "Parçacık ve halka genleşme şiddeti",
                        4.0, 32.0, () -> (double) effectIntensity, v -> effectIntensity = (int) Math.round(v), "%.0f"));
            } else if (selectedCategory == 2) {
                rows.add(new ToggleRow("hud_m", "Axolotl HUD Ana Anahtarı", "Tüm ekran panellerini açar veya kapatır",
                        () -> hudMasterEnabled, v -> hudMasterEnabled = v));
                rows.add(new ToggleRow("fps_cps", "FPS / CPS / Ping / Speed", "Canlı performans ve hız metrikleri",
                        () -> showFpsHud && showCpsHud && showPingHud && showSpeedHud,
                        v -> {
                            showFpsHud = v;
                            showCpsHud = v;
                            showPingHud = v;
                            showSpeedHud = v;
                        }));
                rows.add(new ToggleRow("armor_h", "Zırh Göstergesi", "Kuşanılan zırhların dayanıklılık yüzdeleri",
                        () -> showArmorHud, v -> showArmorHud = v));
                rows.add(new ToggleRow("keys_h", "Keystrokes Paneli", "WASD ve fare tıklama göstergesi",
                        () -> showKeystrokesHud, v -> showKeystrokesHud = v));
            } else {
                rows.add(new ToggleRow("target_a", "Hedef Kilit Analizi", "Bakışa en yakın rakibi otomatik seçer",
                        () -> targetAssistEnabled, v -> targetAssistEnabled = v));
                rows.add(new SliderRow("t_range", "Hedef Tarama Menzili", "Maksimum hedef mesafesi (blok)",
                        12.0, 96.0, () -> maxTargetRange, v -> maxTargetRange = v, "%.0f m"));
                rows.add(new SliderRow("min_spd", "Oto Fişek Hız Eşiği", "Bu hızın altına inince fişek tetiklenir",
                        0.15, 1.80, () -> minSpeedThreshold, v -> minSpeedThreshold = v, "%.2f"));
                rows.add(new SliderRow("r_cd", "Fişek Bekleme (Tick)", "Fişekler arası bekleme süresi",
                        5.0, 60.0, () -> (double) rocketCooldownTicks, v -> rocketCooldownTicks = (int) Math.round(v), "%.0f t"));
            }
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, this.width, this.height, COLOR_BACKDROP);

            if (hudMasterEnabled && this.client != null) {
                renderAllHudModules(context, this.textRenderer, this.client, true);
            }

            int panelW = Math.min(540, this.width - 32);
            int panelH = Math.min(316, this.height - 32);
            int panelX = (this.width - panelW) / 2;
            int panelY = (this.height - panelH) / 2;

            context.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_PANEL);
            drawRectBorder(context, panelX, panelY, panelW, panelH, COLOR_BORDER);

            int headerH = 36;
            context.fill(panelX, panelY, panelX + panelW, panelY + headerH, COLOR_PANEL_HEADER);
            context.fill(panelX, panelY + headerH - 1, panelX + panelW, panelY + headerH, COLOR_ACCENT);

            context.drawText(this.textRenderer, "NEXUS // ELYTRA + SOUP + AXOLOTL", panelX + 12, panelY + 10, COLOR_TEXT, true);
            context.drawText(this.textRenderer, "1.21.11  •  [R-SHIFT]", panelX + panelW - 114, panelY + 13, COLOR_ACCENT, false);

            int sidebarW = 128;
            int bodyY = panelY + headerH + 8;
            int bodyH = panelH - headerH - 36;
            context.fill(panelX + 10, bodyY, panelX + 10 + sidebarW, bodyY + bodyH, 0xFF0C121E);
            drawRectBorder(context, panelX + 10, bodyY, sidebarW, bodyH, 0xFF1E2C47);

            String[] tabs = {"Elytra PvP", "Soup Visual", "Axolotl HUD", "İnce Ayar"};
            for (int i = 0; i < tabs.length; i++) {
                int tx = panelX + 15;
                int ty = bodyY + 8 + i * 32;
                int tw = sidebarW - 10;
                int th = 26;
                boolean active = (i == selectedCategory);
                boolean hovered = mouseX >= tx && mouseX <= tx + tw && mouseY >= ty && mouseY <= ty + th;

                if (active) {
                    context.fill(tx, ty, tx + tw, ty + th, 0xFF172844);
                    context.fill(tx, ty, tx + 3, ty + th, COLOR_ACCENT);
                } else if (hovered) {
                    context.fill(tx, ty, tx + tw, ty + th, 0xFF131D31);
                }

                context.drawText(this.textRenderer, tabs[i], tx + 9, ty + 9, active ? COLOR_TEXT : COLOR_MUTED, active);
            }

            int contentX = panelX + 10 + sidebarW + 10;
            int contentW = panelX + panelW - 10 - contentX;

            for (int i = 0; i < rows.size(); i++) {
                ControlRow row = rows.get(i);
                int ry = bodyY + 4 + i * 56;
                int rh = 52;
                row.render(context, this.textRenderer, contentX, ry, contentW, rh, mouseX, mouseY);
            }

            int footerY = panelY + panelH - 22;
            context.fill(panelX + 10, footerY, panelX + panelW - 10, footerY + 15, 0xFF0C121E);
            context.drawText(this.textRenderer, "HUD kutularını ekranda sürükleyebilirsin • Kapatmak için Sağ Shift",
                    panelX + 14, footerY + 4, COLOR_MUTED, false);

            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(Click click, boolean doubled) {
            double mouseX = click.x();
            double mouseY = click.y();
            int button = click.button();

            if (button == 0) {
                int panelW = Math.min(540, this.width - 32);
                int panelH = Math.min(316, this.height - 32);
                int panelX = (this.width - panelW) / 2;
                int panelY = (this.height - panelH) / 2;

                boolean insideMainPanel = mouseX >= panelX && mouseX <= panelX + panelW
                        && mouseY >= panelY && mouseY <= panelY + panelH;

                if (insideMainPanel) {
                    int headerH = 36;
                    int sidebarW = 128;
                    int bodyY = panelY + headerH + 8;

                    for (int i = 0; i < 4; i++) {
                        int tx = panelX + 15;
                        int ty = bodyY + 8 + i * 32;
                        int tw = sidebarW - 10;
                        int th = 26;
                        if (mouseX >= tx && mouseX <= tx + tw && mouseY >= ty && mouseY <= ty + th) {
                            selectedCategory = i;
                            activeSliderId = null;
                            rebuildRows();
                            return true;
                        }
                    }

                    int contentX = panelX + 10 + sidebarW + 10;
                    int contentW = panelX + panelW - 10 - contentX;
                    for (int i = 0; i < rows.size(); i++) {
                        ControlRow row = rows.get(i);
                        int ry = bodyY + 4 + i * 56;
                        int rh = 52;
                        String draggedId = row.onClick(contentX, ry, contentW, rh, mouseX, mouseY);
                        if (draggedId != null) {
                            activeSliderId = draggedId;
                            return true;
                        }
                    }
                    return true;
                }

                if (mouseX >= statsHudX && mouseX <= statsHudX + 116 && mouseY >= statsHudY && mouseY <= statsHudY + 56) {
                    draggingHudId = "stats";
                    dragOffsetX = (int) mouseX - statsHudX;
                    dragOffsetY = (int) mouseY - statsHudY;
                    return true;
                }
                if (mouseX >= targetHudX && mouseX <= targetHudX + 144 && mouseY >= targetHudY && mouseY <= targetHudY + 42) {
                    draggingHudId = "target";
                    dragOffsetX = (int) mouseX - targetHudX;
                    dragOffsetY = (int) mouseY - targetHudY;
                    return true;
                }
                if (mouseX >= armorHudX && mouseX <= armorHudX + 104 && mouseY >= armorHudY && mouseY <= armorHudY + 26) {
                    draggingHudId = "armor";
                    dragOffsetX = (int) mouseX - armorHudX;
                    dragOffsetY = (int) mouseY - armorHudY;
                    return true;
                }
                if (mouseX >= keysHudX && mouseX <= keysHudX + 76 && mouseY >= keysHudY && mouseY <= keysHudY + 52) {
                    draggingHudId = "keys";
                    dragOffsetX = (int) mouseX - keysHudX;
                    dragOffsetY = (int) mouseY - keysHudY;
                    return true;
                }
            }
            return super.mouseClicked(click, doubled);
        }

        @Override
        public boolean mouseDragged(Click click, double deltaX, double deltaY) {
            if (click.button() == 0) {
                if (draggingHudId != null) {
                    int nx = MathHelper.clamp((int) click.x() - dragOffsetX, 2, Math.max(2, this.width - 80));
                    int ny = MathHelper.clamp((int) click.y() - dragOffsetY, 2, Math.max(2, this.height - 30));
                    switch (draggingHudId) {
                        case "stats" -> {
                            statsHudX = nx;
                            statsHudY = ny;
                        }
                        case "target" -> {
                            targetHudX = nx;
                            targetHudY = ny;
                        }
                        case "armor" -> {
                            armorHudX = nx;
                            armorHudY = ny;
                        }
                        case "keys" -> {
                            keysHudX = nx;
                            keysHudY = ny;
                        }
                    }
                    return true;
                }

                if (activeSliderId != null) {
                    int panelW = Math.min(540, this.width - 32);
                    int panelH = Math.min(316, this.height - 32);
                    int panelX = (this.width - panelW) / 2;
                    int panelY = (this.height - panelH) / 2;

                    int headerH = 36;
                    int sidebarW = 128;
                    int bodyY = panelY + headerH + 8;
                    int contentX = panelX + 10 + sidebarW + 10;
                    int contentW = panelX + panelW - 10 - contentX;

                    for (int i = 0; i < rows.size(); i++) {
                        ControlRow row = rows.get(i);
                        int ry = bodyY + 4 + i * 56;
                        int rh = 52;
                        if (row.onDrag(contentX, ry, contentW, rh, click.x(), activeSliderId)) {
                            return true;
                        }
                    }
                }
            }
            return super.mouseDragged(click, deltaX, deltaY);
        }

        @Override
        public boolean mouseReleased(Click click) {
            if (click.button() == 0) {
                activeSliderId = null;
                draggingHudId = null;
            }
            return super.mouseReleased(click);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        private interface ControlRow {
            void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, int mx, int my);

            String onClick(int x, int y, int w, int h, double mx, double my);

            boolean onDrag(int x, int y, int w, int h, double mx, String activeSliderId);
        }

        private static class ToggleRow implements ControlRow {
            private final String id;
            private final String title;
            private final String desc;
            private final BooleanSupplier getter;
            private final Consumer<Boolean> setter;

            ToggleRow(String id, String title, String desc, BooleanSupplier getter, Consumer<Boolean> setter) {
                this.id = id;
                this.title = title;
                this.desc = desc;
                this.getter = getter;
                this.setter = setter;
            }

            @Override
            public void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, int mx, int my) {
                boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + h;
                ctx.fill(x, y, x + w, y + h, hovered ? COLOR_CARD_HOVER : COLOR_CARD);
                drawRectBorder(ctx, x, y, w, h, COLOR_BORDER);

                ctx.drawText(tr, title, x + 11, y + 9, COLOR_TEXT, true);
                ctx.drawText(tr, desc, x + 11, y + 25, COLOR_MUTED, false);

                boolean enabled = getter.getAsBoolean();
                int pillW = 40;
                int pillH = 16;
                int px = x + w - pillW - 12;
                int py = y + (h - pillH) / 2;

                ctx.fill(px, py, px + pillW, py + pillH, enabled ? 0xFF14532D : 0xFF450A0A);
                drawRectBorder(ctx, px, py, pillW, pillH, enabled ? COLOR_ON : COLOR_OFF);

                int knob = 10;
                int kx = enabled ? (px + pillW - knob - 3) : (px + 3);
                ctx.fill(kx, py + 3, kx + knob, py + 3 + knob, enabled ? COLOR_ON : COLOR_OFF);
            }

            @Override
            public String onClick(int x, int y, int w, int h, double mx, double my) {
                if (mx >= x && mx <= x + w && my >= y && my <= y + h) {
                    setter.accept(!getter.getAsBoolean());
                }
                return null;
            }

            @Override
            public boolean onDrag(int x, int y, int w, int h, double mx, String activeSliderId) {
                return false;
            }
        }

        private static class SliderRow implements ControlRow {
            private final String id;
            private final String title;
            private final String desc;
            private final double min;
            private final double max;
            private final DoubleSupplier getter;
            private final DoubleConsumer setter;
            private final String format;

            SliderRow(String id, String title, String desc, double min, double max,
                      DoubleSupplier getter, DoubleConsumer setter, String format) {
                this.id = id;
                this.title = title;
                this.desc = desc;
                this.min = min;
                this.max = max;
                this.getter = getter;
                this.setter = setter;
                this.format = format;
            }

            @Override
            public void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, int mx, int my) {
                boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + h;
                ctx.fill(x, y, x + w, y + h, hovered ? COLOR_CARD_HOVER : COLOR_CARD);
                drawRectBorder(ctx, x, y, w, h, COLOR_BORDER);

                double val = getter.getAsDouble();
                String valStr = String.format(format, val);

                ctx.drawText(tr, title, x + 11, y + 7, COLOR_TEXT, true);
                ctx.drawText(tr, valStr, x + w - tr.getWidth(valStr) - 12, y + 7, COLOR_ACCENT, true);
                ctx.drawText(tr, desc, x + 11, y + 19, COLOR_MUTED, false);

                int barX = x + 11;
                int barY = y + 36;
                int barW = w - 22;
                int barH = 6;

                ctx.fill(barX, barY, barX + barW, barY + barH, 0xFF0B101B);
                double ratio = Math.max(0.0, Math.min(1.0, (val - min) / (max - min)));
                int fillW = (int) Math.round(barW * ratio);
                ctx.fill(barX, barY, barX + fillW, barY + barH, COLOR_ACCENT_SOFT);
                ctx.fill(barX + Math.max(0, fillW - 2), barY - 2, barX + Math.min(barW, fillW + 2), barY + barH + 2, COLOR_TEXT);
            }

            @Override
            public String onClick(int x, int y, int w, int h, double mx, double my) {
                int barX = x + 11;
                int barY = y + 32;
                int barW = w - 22;
                int barH = 14;
                if (mx >= barX - 4 && mx <= barX + barW + 4 && my >= barY && my <= barY + barH) {
                    applyRatio(barX, barW, mx);
                    return id;
                }
                return null;
            }

            @Override
            public boolean onDrag(int x, int y, int w, int h, double mx, String activeSliderId) {
                if (id.equals(activeSliderId)) {
                    int barX = x + 11;
                    int barW = w - 22;
                    applyRatio(barX, barW, mx);
                    return true;
                }
                return false;
            }

            private void applyRatio(int barX, int barW, double mx) {
                double ratio = Math.max(0.0, Math.min(1.0, (mx - barX) / (double) barW));
                setter.accept(min + ratio * (max - min));
            }
        }
    }
}
