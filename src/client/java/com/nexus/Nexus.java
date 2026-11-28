package com.nexus;

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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class Nexus implements ClientModInitializer {

    // ==================== Moduller ====================

    static final class Mod {
        final String cat;
        final String key;
        final String name;
        boolean on;
        KeyMapping km;

        Mod(String cat, String key, String name, boolean on) {
            this.cat = cat;
            this.key = key;
            this.name = name;
            this.on = on;
        }
    }

    static final List<Mod> MODS = new ArrayList<>();

    static Mod add(String cat, String key, String name, boolean on) {
        Mod m = new Mod(cat, key, name, on);
        MODS.add(m);
        return m;
    }

    // Combat
    static final Mod KEYS = add("Combat", "keys", "Keystrokes", true);
    static final Mod CPS = add("Combat", "cps", "CPS", true);
    static final Mod COMBO = add("Combat", "combo", "Vurus sayaci", true);
    static final Mod TARGET = add("Combat", "target", "Hedef paneli", true);
    static final Mod TESP = add("Combat", "tesp", "Target ESP", true);
    static final Mod CROSS = add("Combat", "cross", "Kirmizi crosshair", true);
    static final Mod ARMOR = add("Combat", "armor", "Zirh gostergesi", true);
    static final Mod LOWHP = add("Combat", "lowhp", "Dusuk can uyarisi", true);
    static final Mod HITFX = add("Combat", "hitfx", "Vurus efekti", true);

    // Visual
    static final Mod BADGE = add("Visual", "badge", "Rozet", true);
    static final Mod JCIRC = add("Visual", "jcirc", "Jump circles", true);
    static final Mod TINT = add("Visual", "tint", "Ekran filtresi", false);
    static final Mod VIGN = add("Visual", "vign", "Vignette", false);

    // Player
    static final Mod FPS = add("Player", "fps", "FPS", true);
    static final Mod PING = add("Player", "ping", "Ping", true);
    static final Mod COORDS = add("Player", "coords", "Koordinat", true);
    static final Mod DIR = add("Player", "dir", "Yon", false);
    static final Mod SPEED = add("Player", "speed", "Hiz", false);
    static final Mod CLOCK = add("Player", "clock", "Saat", false);
    static final Mod SPRINT = add("Player", "sprint", "Oto kosu", false);
    static final Mod HALO = add("Player", "halo", "Halo (sapka)", false);
    static final Mod SNOW = add("Player", "snow", "Kar halkasi", false);
    static final Mod AURA = add("Player", "aura", "Parilti aurasi", false);

    // Elytra
    static final Mod ESPEED = add("Elytra", "espeed", "Ucus hizi", true);
    static final Mod EALT = add("Elytra", "ealt", "Yukseklik", true);
    static final Mod ETIME = add("Elytra", "etime", "Ucus suresi", true);
    static final Mod ETRAIL = add("Elytra", "etrail", "Elytra izi", false);
    static final Mod ERING = add("Elytra", "ering", "Elytra halkasi", false);

    static final String[] TABS = {"Combat", "Visual", "Player", "Elytra", "Duzen"};
    static final String LAYOUT_TAB = "Duzen";

    // ==================== Ayarlar ====================

    static int hudX = 6, hudY = 6;
    static int keysX = 6, keysY = 130;
    static int armorX = 6, armorY = 200;
    static int targetX = 6, targetY = 232;
    static int colorIdx = 0;
    static int filterIdx = 0;

    static final int[] COLORS = {0xFFFFFFFF, 0xFF55FF55, 0xFF55FFFF, 0xFFFFFF55, 0xFFB56CFF};
    static final String[] COLOR_NAMES = {"Beyaz", "Yesil", "Camgobegi", "Sari", "Mor"};
    static final int[] FILTERS = {0x30FF8030, 0x302060FF, 0x30A040FF, 0x55000028};
    static final String[] FILTER_NAMES = {"Gun batimi", "Soguk mavi", "Mor", "Gece"};

    // ==================== Durum ====================

    private static KeyMapping openKey;
    private static final Deque<Long> leftClicks = new ArrayDeque<>();
    private static final Deque<Long> rightClicks = new ArrayDeque<>();
    private static boolean wasLeft = false;
    private static boolean wasRight = false;
    private static int hits = 0;
    private static int combo = 0;
    private static LivingEntity lastTarget = null;
    private static long lastHitMs = 0L;
    private static long glideStart = 0L;
    private static int ticks = 0;
    private static boolean wasOnGround = true;
    private static float espFocus = 0f;

    private static final List<Ring> rings = new ArrayList<>();

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

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String[] DIRS = {
        "Guney", "Guneybati", "Bati", "Kuzeybati",
        "Kuzey", "Kuzeydogu", "Dogu", "Guneydogu"
    };

    @Override
    public void onInitializeClient() {
        load();

        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("nexus", "main"));
        openKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.nexus.menu", GLFW.GLFW_KEY_P, category));
        for (Mod m : MODS) {
            m.km = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.nexus.mod." + m.key, GLFW.GLFW_KEY_UNKNOWN, category));
        }

        ClientTickEvents.END_CLIENT_TICK.register(Nexus::tick);

        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            Identifier.fromNamespaceAndPath("nexus", "hud"),
            (graphics, tickCounter) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null || mc.level == null || mc.options.hideGui) return;
                renderAll(graphics, mc);
            }
        );
    }

    // ==================== Tick ====================

    private static void tick(Minecraft mc) {
        ticks++;

        while (openKey.consumeClick()) {
            mc.setScreen(mc.screen instanceof NexusMenu ? null : new NexusMenu());
        }
        for (Mod m : MODS) {
            while (m.km.consumeClick()) {
                m.on = !m.on;
                save();
            }
        }

        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            rings.clear();
            return;
        }

        long now = System.currentTimeMillis();
        boolean left = mc.options.keyAttack.isDown();
        boolean right = mc.options.keyUse.isDown();

        if (mc.screen == null) {
            if (left && !wasLeft) {
                leftClicks.addLast(now);
                Entity e = mc.crosshairPickEntity;
                if (e instanceof LivingEntity le) {
                    hits++;
                    combo++;
                    lastHitMs = now;
                    lastTarget = le;
                    if (HITFX.on) hitBurst(mc, le);
                }
            }
            if (right && !wasRight) rightClicks.addLast(now);
        }
        wasLeft = left;
        wasRight = right;

        if (p.hurtTime > 0) combo = 0;

        if (SPRINT.on && mc.screen == null && mc.options.keyUp.isDown()
                && !p.isUsingItem() && p.getFoodData().getFoodLevel() > 6) {
            p.setSprinting(true);
        }

        boolean gliding = p.isFallFlying();
        if (gliding && glideStart == 0L) glideStart = now;
        if (!gliding) glideStart = 0L;

        // ---- Jump circles ----
        if (JCIRC.on) {
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
            wasOnGround = p.onGround();
        }

        // ---- Target ESP ----
        LivingEntity espTarget = null;
        Entity pick = mc.crosshairPickEntity;
        if (pick instanceof LivingEntity le) {
            espTarget = le;
        } else if (lastTarget != null && lastTarget.isAlive() && now - lastHitMs < 3000L) {
            espTarget = lastTarget;
        }
        boolean aiming = TESP.on && espTarget != null;
        espFocus += ((aiming ? 1f : 0f) - espFocus) * 0.25f;

        if (aiming && ticks % 2 == 0) {
            double r = espTarget.getBbWidth() * 0.9 + 0.2;
            double midY = espTarget.getY() + espTarget.getBbHeight() * 0.5;
            for (int i = 0; i < 8; i++) {
                double a = ticks * 0.2 + i * Math.PI / 4.0;
                mc.level.addParticle(ParticleTypes.GLOW,
                    espTarget.getX() + Math.cos(a) * r, midY, espTarget.getZ() + Math.sin(a) * r,
                    0.0, 0.0, 0.0);
            }
            mc.level.addParticle(ParticleTypes.END_ROD,
                espTarget.getX(), espTarget.getY() + espTarget.getBbHeight() + 0.4,
                espTarget.getZ(), 0.0, -0.02, 0.0);
        }

        // ---- Parcacik animasyonlari ----
        if (HALO.on && ticks % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                double a = ticks * 0.15 + i * Math.PI / 3.0;
                mc.level.addParticle(ParticleTypes.END_ROD,
                    p.getX() + Math.cos(a) * 0.4, p.getY() + 2.15, p.getZ() + Math.sin(a) * 0.4,
                    0.0, 0.0, 0.0);
            }
        }
        if (SNOW.on && ticks % 3 == 0) {
            for (int i = 0; i < 5; i++) {
                double a = ticks * 0.12 + i * (Math.PI * 2.0 / 5.0);
                mc.level.addParticle(ParticleTypes.SNOWFLAKE,
                    p.getX() + Math.cos(a) * 0.35, p.getY() + 2.1, p.getZ() + Math.sin(a) * 0.35,
                    0.0, 0.0, 0.0);
            }
        }
        if (AURA.on && ticks % 3 == 0) {
            double a = ticks * 0.3;
            double y = p.getY() + (ticks % 20) / 20.0 * 1.8;
            mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                p.getX() + Math.cos(a) * 0.5, y, p.getZ() + Math.sin(a) * 0.5,
                0.0, 0.0, 0.0);
        }
        if (gliding && ETRAIL.on) {
            mc.level.addParticle(ParticleTypes.END_ROD,
                p.getX(), p.getY() + 0.2, p.getZ(), 0.0, 0.0, 0.0);
        }
        if (gliding && ERING.on && ticks % 2 == 0) {
            for (int i = 0; i < 8; i++) {
                double a = (Math.PI * 2.0 * i) / 8.0;
                mc.level.addParticle(ParticleTypes.FIREWORK,
                    p.getX() + Math.cos(a) * 1.4, p.getY() + 0.8, p.getZ() + Math.sin(a) * 1.4,
                    0.0, 0.0, 0.0);
            }
        }
    }

    private static void hitBurst(Minecraft mc, LivingEntity target) {
        for (int i = 0; i < 10; i++) {
            double a = (Math.PI * 2.0 * i) / 10.0;
            mc.level.addParticle(ParticleTypes.CRIT,
                target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                Math.cos(a) * 0.14, 0.08, Math.sin(a) * 0.14);
        }
    }

    private static int cps(Deque<Long> q) {
        long now = System.currentTimeMillis();
        while (!q.isEmpty() && now - q.peekFirst() > 1000L) {
            q.removeFirst();
        }
        return q.size();
    }

    // ==================== Cizim ====================

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

    private static void vignette(GuiGraphics g, int w, int h) {
        for (int i = 0; i < 10; i++) {
            int a = (70 - i * 7) << 24;
            int t = i * 3;
            g.fill(0, t, w, t + 3, a);
            g.fill(0, h - t - 3, w, h - t, a);
            g.fill(t, 0, t + 3, h, a);
            g.fill(w - t - 3, 0, w - t, h, a);
        }
    }

    private static void espFrame(GuiGraphics g, int cx, int cy, float focus) {
        int size = Math.round(22f - 8f * focus);
        int len = 6;
        int c = 0xFF38E8FF;
        int x0 = cx - size;
        int x1 = cx + size;
        int y0 = cy - size;
        int y1 = cy + size;
        // sol ust
        g.fill(x0, y0, x0 + len, y0 + 2, c);
        g.fill(x0, y0, x0 + 2, y0 + len, c);
        // sag ust
        g.fill(x1 - len, y0, x1, y0 + 2, c);
        g.fill(x1 - 2, y0, x1, y0 + len, c);
        // sol alt
        g.fill(x0, y1 - 2, x0 + len, y1, c);
        g.fill(x0, y1 - len, x0 + 2, y1, c);
        // sag alt
        g.fill(x1 - len, y1 - 2, x1, y1, c);
        g.fill(x1 - 2, y1 - len, x1, y1, c);
    }

    private static void renderAll(GuiGraphics g, Minecraft mc) {
        LocalPlayer p = mc.player;
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int textColor = COLORS[colorIdx];
        boolean gliding = p.isFallFlying();

        if (TINT.on) g.fill(0, 0, sw, sh, FILTERS[filterIdx]);
        if (VIGN.on) vignette(g, sw, sh);

        // ---- Rozet ----
        if (BADGE.on) {
            int bw = 110;
            int bx = (sw - bw) / 2;
            int by = 6;
            g.fill(bx, by, bx + bw, by + 18, 0x99181C28);
            g.fill(bx, by, bx + bw, by + 2, 0xFFFF77B7);
            g.drawString(mc.font, "NEXUS CLIENT", bx + 14, by + 7, 0xFF8CF7FF, true);
        }

        // ---- Bilgi satirlari ----
        List<String> lines = new ArrayList<>();
        if (FPS.on) lines.add("FPS: " + mc.getFps());
        if (CPS.on) lines.add("CPS: " + cps(leftClicks) + " | " + cps(rightClicks));
        if (PING.on) {
            int ping = 0;
            var conn = mc.getConnection();
            if (conn != null) {
                var info = conn.getPlayerInfo(p.getUUID());
                if (info != null) ping = info.getLatency();
            }
            lines.add("Ping: " + ping + " ms");
        }
        if (COORDS.on) {
            lines.add("XYZ: " + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ());
        }
        if (DIR.on) {
            float yaw = ((p.getYRot() % 360f) + 360f) % 360f;
            lines.add("Yon: " + DIRS[((int) ((yaw + 22.5f) / 45f)) % 8]);
        }
        if (SPEED.on) {
            double dx = p.getX() - p.xo;
            double dz = p.getZ() - p.zo;
            lines.add(String.format("Hiz: %.1f b/s", Math.sqrt(dx * dx + dz * dz) * 20.0));
        }
        if (CLOCK.on) lines.add("Saat: " + LocalTime.now().format(TIME_FMT));
        if (COMBO.on) lines.add("Vurus: " + hits + "  Combo: " + combo);
        if (gliding) {
            if (ESPEED.on) {
                lines.add(String.format("Elytra: %.1f b/s", p.getDeltaMovement().length() * 20.0));
            }
            if (EALT.on) lines.add("Yukseklik: " + p.getBlockY());
            if (ETIME.on && glideStart > 0L) {
                lines.add("Ucus: " + (System.currentTimeMillis() - glideStart) / 1000L + " sn");
            }
        }

        if (!lines.isEmpty()) {
            int maxW = 0;
            for (String s : lines) maxW = Math.max(maxW, mc.font.width(s));
            int w = maxW + 12;
            int h = lines.size() * 11 + 8;
            g.fill(hudX, hudY, hudX + w, hudY + h, 0x990D1424);
            border(g, hudX, hudY, w, h, 0xFF233554);
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(mc.font, lines.get(i), hudX + 6, hudY + 5 + i * 11, textColor, true);
            }
        }

        // ---- Kirmizi crosshair ----
        Entity looked = mc.crosshairPickEntity;
        if (CROSS.on && looked instanceof LivingEntity && mc.screen == null) {
            int cx = sw / 2;
            int cy = sh / 2;
            g.fill(cx - 6, cy - 1, cx + 7, cy + 1, 0xFFFF2020);
            g.fill(cx - 1, cy - 6, cx + 1, cy + 7, 0xFFFF2020);
        }

        // ---- Target ESP cercevesi ----
        if (TESP.on && espFocus > 0.05f && mc.screen == null) {
            espFrame(g, sw / 2, sh / 2, espFocus);
        }

        // ---- Hedef paneli ----
        LivingEntity target = null;
        if (looked instanceof LivingEntity le) {
            target = le;
        } else if (lastTarget != null && lastTarget.isAlive()
                && System.currentTimeMillis() - lastHitMs < 3000L) {
            target = lastTarget;
        }
        if (TARGET.on && target != null) {
            int tw = 142;
            int th = 42;
            g.fill(targetX, targetY, targetX + tw, targetY + th, 0xA8111827);
            border(g, targetX, targetY, tw, th, 0xFFEF4444);

            float hp = Math.max(0f, target.getHealth());
            float maxHp = Math.max(1f, target.getMaxHealth());
            float ratio = Math.max(0f, Math.min(1f, hp / maxHp));

            g.drawString(mc.font, target.getName().getString(),
                targetX + 7, targetY + 6, 0xFFF8FAFC, true);
            g.drawString(mc.font,
                String.format("%.1f HP - %.1fm", hp, target.distanceTo(p)),
                targetX + 7, targetY + 18, 0xFFCBD5E1, false);

            int bx = targetX + 7;
            int by = targetY + 30;
            int bw = tw - 14;
            g.fill(bx, by, bx + bw, by + 6, 0xFF1F2937);
            g.fill(bx, by, bx + Math.round(bw * ratio), by + 6, 0xFF22C55E);
        }

        // ---- Zirh ----
        if (ARMOR.on) {
            int aw = 96;
            int ah = 24;
            g.fill(armorX, armorY, armorX + aw, armorY + ah, 0x990D1424);
            border(g, armorX, armorY, aw, ah, 0xFF233554);

            EquipmentSlot[] slots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET
            };
            for (int i = 0; i < slots.length; i++) {
                ItemStack st = p.getItemBySlot(slots[i]);
                if (st.isEmpty()) continue;
                int ix = armorX + 5 + i * 22;
                int iy = armorY + 4;
                g.renderItem(st, ix, iy);
                if (st.isDamageableItem()) {
                    int max = st.getMaxDamage();
                    int pct = Math.round(((max - st.getDamageValue()) / (float) max) * 100f);
                    g.drawString(mc.font, pct + "%", ix, iy + 14,
                        pct > 25 ? 0xFF94A3B8 : 0xFFEF4444, false);
                }
            }
        }

        // ---- Keystrokes ----
        if (KEYS.on) {
            int kw = 76;
            int kh = 52;
            g.fill(keysX, keysY, keysX + kw, keysY + kh, 0x880D1424);
            border(g, keysX, keysY, kw, kh, 0xFF233554);

            keyBox(g, mc, keysX + 28, keysY + 4, 20, 14, "W", mc.options.keyUp.isDown());
            keyBox(g, mc, keysX + 6, keysY + 20, 20, 14, "A", mc.options.keyLeft.isDown());
            keyBox(g, mc, keysX + 28, keysY + 20, 20, 14, "S", mc.options.keyDown.isDown());
            keyBox(g, mc, keysX + 50, keysY + 20, 20, 14, "D", mc.options.keyRight.isDown());
            keyBox(g, mc, keysX + 6, keysY + 36, 31, 12, "LMB", mc.options.keyAttack.isDown());
            keyBox(g, mc, keysX + 39, keysY + 36, 31, 12, "RMB", mc.options.keyUse.isDown());
        }

        // ---- Dusuk can uyarisi ----
        if (LOWHP.on && p.getHealth() <= 6.0f && p.isAlive()) {
            int t = 6;
            int c = 0x50FF0000;
            g.fill(0, 0, sw, t, c);
            g.fill(0, sh - t, sw, sh, c);
            g.fill(0, 0, t, sh, c);
            g.fill(sw - t, 0, sw, sh, c);
        }
    }

    // ==================== Kayit / yukleme ====================

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("nexus.properties");
    }

    static void save() {
        Properties p = new Properties();
        for (Mod m : MODS) {
            p.setProperty("m." + m.key, String.valueOf(m.on));
        }
        p.setProperty("hud.x", String.valueOf(hudX));
        p.setProperty("hud.y", String.valueOf(hudY));
        p.setProperty("keys.x", String.valueOf(keysX));
        p.setProperty("keys.y", String.valueOf(keysY));
        p.setProperty("armor.x", String.valueOf(armorX));
        p.setProperty("armor.y", String.valueOf(armorY));
        p.setProperty("target.x", String.valueOf(targetX));
        p.setProperty("target.y", String.valueOf(targetY));
        p.setProperty("color", String.valueOf(colorIdx));
        p.setProperty("filter", String.valueOf(filterIdx));
        try (OutputStream out = Files.newOutputStream(configPath())) {
            p.store(out, "Nexus Client");
        } catch (IOException e) {
            System.err.println("[Nexus] Kaydedilemedi: " + e.getMessage());
        }
    }

    static void load() {
        Path path = configPath();
        if (!Files.exists(path)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            p.load(in);
        } catch (IOException e) {
            System.err.println("[Nexus] Okunamadi: " + e.getMessage());
            return;
        }
        try {
            for (Mod m : MODS) {
                m.on = Boolean.parseBoolean(p.getProperty("m." + m.key, String.valueOf(m.on)));
            }
            hudX = Integer.parseInt(p.getProperty("hud.x", String.valueOf(hudX)));
            hudY = Integer.parseInt(p.getProperty("hud.y", String.valueOf(hudY)));
            keysX = Integer.parseInt(p.getProperty("keys.x", String.valueOf(keysX)));
            keysY = Integer.parseInt(p.getProperty("keys.y", String.valueOf(keysY)));
            armorX = Integer.parseInt(p.getProperty("armor.x", String.valueOf(armorX)));
            armorY = Integer.parseInt(p.getProperty("armor.y", String.valueOf(armorY)));
            targetX = Integer.parseInt(p.getProperty("target.x", String.valueOf(targetX)));
            targetY = Integer.parseInt(p.getProperty("target.y", String.valueOf(targetY)));
            colorIdx = Math.floorMod(Integer.parseInt(p.getProperty("color", "0")), COLORS.length);
            filterIdx = Math.floorMod(Integer.parseInt(p.getProperty("filter", "0")), FILTERS.length);
        } catch (NumberFormatException ignored) {
        }
    }

    static void resetPositions() {
        hudX = 6; hudY = 6;
        keysX = 6; keysY = 130;
        armorX = 6; armorY = 200;
        targetX = 6; targetY = 232;
        save();
    }

    // ==================== Menu ====================

    public static class NexusMenu extends Screen {

        private static int tab = 0;
        private static int page = 0;
        private static String query = "";

        private final List<Button> dyn = new ArrayList<>();
        private int px, py, pw, ph;
        private int pages = 1;

        public NexusMenu() {
            super(Component.literal("Nexus Client"));
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        protected void init() {
            pw = Math.min(300, width - 16);
            ph = Math.min(220, height - 16);
            px = (width - pw) / 2;
            py = (height - ph) / 2;
            dyn.clear();

            for (int i = 0; i < TABS.length; i++) {
                final int idx = i;
                String label = (i == tab ? "> " : "") + TABS[i];
                addRenderableWidget(Button.builder(Component.literal(label), b -> {
                    tab = idx;
                    page = 0;
                    rebuildWidgets();
                }).bounds(px + 6, py + 32 + i * 24, 62, 20).build());
            }

            int cx = px + 74;
            int cw = pw - 80;

            if (!TABS[tab].equals(LAYOUT_TAB)) {
                EditBox box = new EditBox(font, cx, py + 30, cw, 18, Component.literal("Ara"));
                box.setValue(query);
                box.setHint(Component.literal("Ara..."));
                box.setResponder(s -> {
                    query = s;
                    page = 0;
                    refreshRows();
                });
                addRenderableWidget(box);
            }
            refreshRows();
        }

        private Button addDyn(Button b) {
            addRenderableWidget(b);
            dyn.add(b);
            return b;
        }

        private void refreshRows() {
            for (Button b : dyn) removeWidget(b);
            dyn.clear();
            if (TABS[tab].equals(LAYOUT_TAB)) {
                buildLayout();
            } else {
                buildModules();
            }
        }

        private List<Mod> filtered() {
            List<Mod> list = new ArrayList<>();
            String q = query.trim().toLowerCase();
            for (Mod m : MODS) {
                if (q.isEmpty()) {
                    if (m.cat.equals(TABS[tab])) list.add(m);
                } else if (m.name.toLowerCase().contains(q)) {
                    list.add(m);
                }
            }
            return list;
        }

        private void buildModules() {
            List<Mod> list = filtered();
            int cx = px + 74;
            int cw = pw - 80;
            int rows = Math.max(1, (ph - 52 - 30) / 22);
            pages = Math.max(1, (list.size() + rows - 1) / rows);
            page = Math.min(page, pages - 1);

            for (int i = 0; i < rows; i++) {
                int idx = page * rows + i;
                if (idx >= list.size()) break;
                final Mod m = list.get(idx);
                addDyn(Button.builder(Component.literal(label(m)), b -> {
                    m.on = !m.on;
                    save();
                    b.setMessage(Component.literal(label(m)));
                }).bounds(cx, py + 52 + i * 22, cw, 20).build());
            }

            if (pages > 1) {
                int y = py + ph - 26;
                addDyn(Button.builder(Component.literal("<"), b -> {
                    page = Math.max(0, page - 1);
                    refreshRows();
                }).bounds(cx, y, 22, 18).build());
                addDyn(Button.builder(Component.literal(">"), b -> {
                    page = Math.min(pages - 1, page + 1);
                    refreshRows();
                }).bounds(cx + cw - 22, y, 22, 18).build());
            }
        }

        private static String label(Mod m) {
            return m.name + ": " + (m.on ? "ACIK" : "KAPALI");
        }

        private void buildLayout() {
            int cx = px + 74;
            int y = py + 32;

            addDyn(Button.builder(Component.literal("Yazi rengi: " + COLOR_NAMES[colorIdx]), b -> {
                colorIdx = (colorIdx + 1) % COLORS.length;
                save();
                b.setMessage(Component.literal("Yazi rengi: " + COLOR_NAMES[colorIdx]));
            }).bounds(cx, y, 170, 20).build());
            y += 22;

            addDyn(Button.builder(Component.literal("Filtre: " + FILTER_NAMES[filterIdx]), b -> {
                filterIdx = (filterIdx + 1) % FILTERS.length;
                save();
                b.setMessage(Component.literal("Filtre: " + FILTER_NAMES[filterIdx]));
            }).bounds(cx, y, 170, 20).build());
            y += 22;

            mover(cx, y, "Istatistik", () -> hudX, v -> hudX = v, () -> hudY, v -> hudY = v);
            y += 22;
            mover(cx, y, "Tuslar", () -> keysX, v -> keysX = v, () -> keysY, v -> keysY = v);
            y += 22;
            mover(cx, y, "Zirh", () -> armorX, v -> armorX = v, () -> armorY, v -> armorY = v);
            y += 22;
            mover(cx, y, "Hedef", () -> targetX, v -> targetX = v, () -> targetY, v -> targetY = v);
            y += 22;

            addDyn(Button.builder(Component.literal("Konumlari sifirla"),
                b -> resetPositions()).bounds(cx, y, 170, 20).build());
        }

        private void mover(int x, int y, String label,
                           IntSupplier gx, IntConsumer sx, IntSupplier gy, IntConsumer sy) {
            Button name = Button.builder(Component.literal(label), b -> { })
                .bounds(x, y, 70, 20).build();
            name.active = false;
            addDyn(name);
            addDyn(Button.builder(Component.literal("<"), b -> {
                sx.accept(Math.max(0, gx.getAsInt() - 10));
                save();
            }).bounds(x + 72, y, 22, 20).build());
            addDyn(Button.builder(Component.literal(">"), b -> {
                sx.accept(gx.getAsInt() + 10);
                save();
            }).bounds(x + 96, y, 22, 20).build());
            addDyn(Button.builder(Component.literal("^"), b -> {
                sy.accept(Math.max(0, gy.getAsInt() - 10));
                save();
            }).bounds(x + 120, y, 22, 20).build());
            addDyn(Button.builder(Component.literal("v"), b -> {
                sy.accept(gy.getAsInt() + 10);
                save();
            }).bounds(x + 144, y, 22, 20).build());
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
            g.fill(px, py, px + pw, py + ph, 0xE00B1020);
            border(g, px, py, pw, ph, 0xFF2B3F66);
            g.fill(px, py, px + pw, py + 24, 0xFF162138);
            g.fill(px, py + 24, px + pw, py + 25, 0xFF38BDF8);
            g.fill(px + 4, py + 28, px + 70, py + ph - 4, 0xFF0C121E);

            super.render(g, mouseX, mouseY, delta);

            g.drawString(font, "NEXUS CLIENT", px + 8, py + 8, 0xFFB56CFF, true);
            g.drawString(font, "P / ESC: kapat", px + pw - 78, py + 8, 0xFF94A3B8, false);

            if (!TABS[tab].equals(LAYOUT_TAB)) {
                if (pages > 1) {
                    g.drawString(font, (page + 1) + "/" + pages,
                        px + pw / 2 + 24, py + ph - 22, 0xFFCBD5E1, false);
                }
                g.drawString(font, "Tus: Ayarlar > Kontroller > Nexus",
                    px + 74, py + ph - 12, 0xFF64748B, false);
            }
        }
    }
}
