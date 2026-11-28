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
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class Nexus implements ClientModInitializer {

    // ==================== Ozellik listesi ====================

    static final class Feature {
        final String tab;
        final String key;
        final String name;
        final String desc;
        boolean on;
        int keyCode; // GLFW key code, -1 = atanmamis

        Feature(String tab, String key, String name, String desc, boolean on, int keyCode) {
            this.tab = tab;
            this.key = key;
            this.name = name;
            this.desc = desc;
            this.on = on;
            this.keyCode = keyCode;
        }
    }

    static final List<Feature> FEATURES = new ArrayList<>();

    static Feature add(String tab, String key, String name, String desc, boolean on, int keyCode) {
        Feature f = new Feature(tab, key, name, desc, on, keyCode);
        FEATURES.add(f);
        return f;
    }

    // Combat (PvP gorsel ve geri bildirim)
    static final Feature HITMARKER = add("Combat", "hitmarker", "Hitmarker", "Vurus aninda carpraz isaret", true, -1);
    static final Feature SWING_VIS = add("Combat", "swing_vis", "Swing Gosterge", "Saldiri ritmi ve kol animasyon bar", true, -1);
    static final Feature CRIT_FX = add("Combat", "crit_fx", "Kritik Efekt", "Kritik vurus gorsel vurgusu", true, -1);
    static final Feature TARGET = add("Combat", "target", "Hedef Paneli", "Rakip can ve mesafe kutusu", true, -1);
    static final Feature COMBO = add("Combat", "combo", "Vurus Sayaci", "Vurus ve combo sayaci", true, -1);
    static final Feature CPS = add("Combat", "cps", "CPS Sayaci", "Sol/sag tiklama hizi", true, -1);
    static final Feature KEYS = add("Combat", "keys", "Keystrokes", "Hareket ve tiklama tuslari", true, -1);
    static final Feature ARMOR = add("Combat", "armor", "Zirh Durumu", "Zirh parcalari ve dayaniklilik", true, -1);

    // Visual (Sky, Shader, Elytra animasyon, HUD)
    static final Feature SKY_TINT = add("Visual", "sky_tint", "Sky Atmosfer", "Gokyuzu ton filtre katmani", false, -1);
    static final Feature SHADER_FX = add("Visual", "shader_fx", "Shader Modu", "Sinematik kontrast ve derinlik tonu", false, -1);
    static final Feature ELYTRA_ANIM = add("Visual", "elytra_anim", "Elytra Animasyon", "Kanat acilma ve akis animasyonu", true, -1);
    static final Feature CROSS = add("Visual", "cross", "Kirmizi Crosshair", "Hedef uzerinde kirmizi nisangah", true, -1);
    static final Feature LOWHP = add("Visual", "lowhp", "Dusuk Can Uyari", "Can azalinca kenar uyari cercevesi", true, -1);
    static final Feature HUD_BOX = add("Visual", "hud_box", "HUD Bilgileri", "FPS, Ping, XYZ, Yon, Hiz, Saat", true, -1);

    // Player (Kozmetik sapka, animasyonlar, hareket)
    static final Feature HAT = add("Player", "hat", "Kozmetik Sapka", "Karakter basinda silindir sapka", true, GLFW.GLFW_KEY_H);
    static final Feature HALO = add("Player", "halo", "Kozmetik Hale", "Bas uzerinde donen isik halkasi", false, -1);
    static final Feature WALK_ANIM = add("Player", "walk_anim", "Adim Animasyonu", "Akici yurume ritim gostergesi", true, -1);
    static final Feature GLIDE_POSE = add("Player", "glide_pose", "Suzulme Pozu", "Havada suzulme durum efekti", true, -1);
    static final Feature SPRINT = add("Player", "sprint", "Oto Kosu", "Ileri tusunda otomatik kosu", false, -1);

    // Elytra (Ucus gorsel ve telemetri)
    static final Feature ELYTRA_TRAIL = add("Elytra", "elytra_trail", "Kanat Izi", "Elytra ucusunda iz cizgileri", true, GLFW.GLFW_KEY_G);
    static final Feature ELYTRA_HUD = add("Elytra", "elytra_hud", "Ucus Telemetri", "Yukseklik, hiz ve aci bilgisi", true, -1);
    static final Feature BOOST_WARN = add("Elytra", "boost_warn", "Alicak Ucus Uyari", "Yere yaklasinca gorsel uyari", true, -1);

    // Ayar tabinda kontrol edilen watermark
    static boolean showWatermark = true;

    static final String[] TABS = {"Combat", "Visual", "Player", "Elytra", "Ayar"};

    // ==================== Konum ve tema ayarlari ====================

    static int wmX = 8, wmY = 8;
    static int hudX = 8, hudY = 34;
    static int keysX = 8, keysY = 152;
    static int armorX = 8, armorY = 212;
    static int targetX = 8, targetY = 244;
    static int colorIdx = 0;

    static final int[] COLORS = {0xFFFFFFFF, 0xFF38BDF8, 0xFF4ADE80, 0xFFFACC15, 0xFFC084FC};
    static final String[] COLOR_NAMES = {"Beyaz", "Camgobegi", "Yesil", "Sari", "Mor"};

    // ==================== Durum ====================

    private static KeyMapping openKey;
    private static final Deque<Long> leftClicks = new ArrayDeque<>();
    private static final Deque<Long> rightClicks = new ArrayDeque<>();
    private static boolean wasLeft = false;
    private static boolean wasRight = false;
    private static boolean[] prevBoundKeys = new boolean[0];

    private static int hits = 0;
    private static int combo = 0;
    private static LivingEntity lastTarget = null;
    private static long lastHitMs = 0L;
    private static long lastSwingMs = 0L;
    private static long lastCritMs = 0L;

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

    // ==================== Tick ve tus atamalari ====================

    private static void tick(Minecraft mc) {
        while (openKey.consumeClick()) {
            mc.setScreen(mc.screen instanceof NexusMenu ? null : new NexusMenu());
        }

        long window = mc.getWindow().handle();
        if (prevBoundKeys.length != FEATURES.size()) {
            prevBoundKeys = new boolean[FEATURES.size()];
        }
        if (mc.screen == null) {
            for (int i = 0; i < FEATURES.size(); i++) {
                Feature f = FEATURES.get(i);
                if (f.keyCode > 0) {
                    boolean down = GLFW.glfwGetKey(window, f.keyCode) == GLFW.GLFW_PRESS;
                    if (down && !prevBoundKeys[i]) {
                        f.on = !f.on;
                        save();
                    }
                    prevBoundKeys[i] = down;
                } else {
                    prevBoundKeys[i] = false;
                }
            }
        }

        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;

        long now = System.currentTimeMillis();
        boolean left = mc.options.keyAttack.isDown();
        boolean right = mc.options.keyUse.isDown();

        if (mc.screen == null) {
            if (left && !wasLeft) {
                leftClicks.addLast(now);
                lastSwingMs = now;
                Entity e = mc.crosshairPickEntity;
                if (e instanceof LivingEntity le) {
                    hits++;
                    combo++;
                    lastHitMs = now;
                    lastTarget = le;
                    if (p.fallDistance > 0.0f && !p.onGround()) {
                        lastCritMs = now;
                    }
                }
            }
            if (right && !wasRight) {
                rightClicks.addLast(now);
            }
        }
        wasLeft = left;
        wasRight = right;

        if (p.hurtTime > 0) {
            combo = 0;
        }
        if (combo > 0 && now - lastHitMs > 4500L) {
            combo = 0;
        }

        if (SPRINT.on && mc.screen == null && mc.options.keyUp.isDown()
                && !p.isUsingItem() && !p.horizontalCollision
                && !p.isInWater() && p.getFoodData().getFoodLevel() > 6) {
            p.setSprinting(true);
        }
    }

    private static int cps(Deque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000L) {
            q.removeFirst();
        }
        return q.size();
    }

    private static String keyName(int code) {
        if (code <= 0) return "YOK";
        String name = GLFW.glfwGetKeyName(code, 0);
        if (name != null && !name.isBlank()) {
            return name.toUpperCase(Locale.ROOT);
        }
        return switch (code) {
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "L-SHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "L-CTRL";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            default -> "KEY-" + code;
        };
    }

    // ==================== Cizim yardimcilari ====================

    private static void border(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h, int bg, int bd) {
        g.fill(x, y, x + w, y + h, bg);
        border(g, x, y, w, h, bd);
    }

    private static void keyBox(GuiGraphics g, Minecraft mc, int x, int y,
                               int w, int h, String label, boolean pressed) {
        g.fill(x, y, x + w, y + h, pressed ? 0xDD38BDF8 : 0x99111827);
        border(g, x, y, w, h, pressed ? 0xFFE0F2FE : 0xFF334155);
        int tx = x + (w - mc.font.width(label)) / 2;
        int ty = y + (h - 7) / 2;
        g.drawString(mc.font, label, tx, ty, pressed ? 0xFF090D16 : 0xFFF8FAFC, false);
    }

    private static void renderAll(GuiGraphics g, Minecraft mc) {
        LocalPlayer p = mc.player;
        long now = System.currentTimeMillis();
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int accent = COLORS[colorIdx];

        // ---- Visual: Sky / Shader / Dusuk can katmanlari ----
        if (SKY_TINT.on) {
            g.fill(0, 0, sw, sh / 3, 0x1838BDF8);
        }
        if (SHADER_FX.on) {
            g.fill(0, 0, sw, sh, 0x14090D16);
            border(g, 0, 0, sw, sh, 0x26C084FC);
        }
        if (LOWHP.on && p.getHealth() <= 6.0f && p.isAlive()) {
            int t = 6;
            int c = 0x55EF4444;
            g.fill(0, 0, sw, t, c);
            g.fill(0, sh - t, sw, sh, c);
            g.fill(0, 0, t, sh, c);
            g.fill(sw - t, 0, sw, sh, c);
        }

        // ---- Sol ust Watermark ----
        if (showWatermark) {
            String wm = "NEXUS CLIENT";
            int ww = mc.font.width(wm) + 14;
            int wh = 18;
            panel(g, wmX, wmY, ww, wh, 0xB80B101B, accent);
            g.fill(wmX, wmY, wmX + 3, wmY + wh, accent);
            g.drawString(mc.font, wm, wmX + 8, wmY + 5, accent, true);
        }

        // ---- Ana HUD listesi ----
        List<String> lines = new ArrayList<>();
        if (HUD_BOX.on) {
            lines.add("FPS: " + mc.getFps());
            int ping = 0;
            var conn = mc.getConnection();
            if (conn != null) {
                var info = conn.getPlayerInfo(p.getUUID());
                if (info != null) ping = info.getLatency();
            }
            lines.add("Ping: " + ping + " ms");
            lines.add("XYZ: " + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ());

            float yaw = ((p.getYRot() % 360f) + 360f) % 360f;
            int idx = ((int) ((yaw + 22.5f) / 45f)) % 8;
            lines.add("Yon: " + DIRS[idx]);

            double dx = p.getX() - p.xo;
            double dz = p.getZ() - p.zo;
            lines.add(String.format(Locale.ROOT, "Hiz: %.1f b/s", Math.sqrt(dx * dx + dz * dz) * 20.0));
            lines.add("Saat: " + LocalTime.now().format(TIME_FMT));
        }
        if (CPS.on) {
            lines.add("CPS: " + cps(leftClicks, now) + " | " + cps(rightClicks, now));
        }
        if (COMBO.on) {
            lines.add("Vurus: " + hits + "  Combo: " + combo);
        }

        if (!lines.isEmpty()) {
            int maxW = 0;
            for (String s : lines) maxW = Math.max(maxW, mc.font.width(s));
            int w = maxW + 12;
            int h = lines.size() * 11 + 8;
            panel(g, hudX, hudY, w, h, 0xA80B101B, 0xFF1E293B);
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(mc.font, lines.get(i), hudX + 6, hudY + 5 + i * 11, accent, true);
            }
        }

        // ---- Combat: Hitmarker, Kritik ve Kirmizi Crosshair ----
        Entity looked = mc.crosshairPickEntity;
        int cx = sw / 2;
        int cy = sh / 2;

        if (CROSS.on && looked instanceof LivingEntity && mc.screen == null) {
            g.fill(cx - 6, cy - 1, cx + 7, cy + 1, 0xFFFF2020);
            g.fill(cx - 1, cy - 6, cx + 1, cy + 7, 0xFFFF2020);
        }
        if (HITMARKER.on && now - lastHitMs < 220L) {
            int hmColor = (CRIT_FX.on && now - lastCritMs < 220L) ? 0xFFFACC15 : 0xFFF8FAFC;
            for (int d = 3; d <= 6; d++) {
                g.fill(cx - d, cy - d, cx - d + 1, cy - d + 2, hmColor);
                g.fill(cx + d, cy - d, cx + d + 1, cy - d + 2, hmColor);
                g.fill(cx - d, cy + d, cx - d + 1, cy + d + 2, hmColor);
                g.fill(cx + d, cy + d, cx + d + 1, cy + d + 2, hmColor);
            }
        }
        if (SWING_VIS.on && now - lastSwingMs < 400L) {
            float prog = 1.0f - ((now - lastSwingMs) / 400.0f);
            int bw = 40;
            int bx = cx - bw / 2;
            int by = cy + 14;
            g.fill(bx, by, bx + bw, by + 3, 0x881E293B);
            g.fill(bx, by, bx + Math.round(bw * prog), by + 3, accent);
        }

        // ---- Combat: Hedef Paneli ----
        LivingEntity target = null;
        if (looked instanceof LivingEntity le) {
            target = le;
        } else if (lastTarget != null && lastTarget.isAlive() && now - lastHitMs < 3500L) {
            target = lastTarget;
        }
        if (TARGET.on && target != null) {
            int tw = 148;
            int th = 44;
            panel(g, targetX, targetY, tw, th, 0xB80B101B, 0xFFEF4444);

            float hp = Math.max(0f, target.getHealth());
            float maxHp = Math.max(1f, target.getMaxHealth());
            float ratio = Math.max(0f, Math.min(1f, hp / maxHp));

            g.drawString(mc.font, target.getName().getString(),
                targetX + 7, targetY + 6, 0xFFF8FAFC, true);
            g.drawString(mc.font,
                String.format(Locale.ROOT, "%.1f HP - %.1fm", hp, target.distanceTo(p)),
                targetX + 7, targetY + 18, 0xFFCBD5E1, false);

            int bx = targetX + 7;
            int by = targetY + 31;
            int bw = tw - 14;
            g.fill(bx, by, bx + bw, by + 6, 0xFF1F2937);
            g.fill(bx, by, bx + Math.round(bw * ratio), by + 6, 0xFF22C55E);
        }

        // ---- Combat: Zirh ----
        if (ARMOR.on) {
            int aw = 96;
            int ah = 26;
            panel(g, armorX, armorY, aw, ah, 0xA80B101B, 0xFF1E293B);

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
                    g.drawString(mc.font, pct + "%", ix, iy + 15,
                        pct > 25 ? 0xFF94A3B8 : 0xFFEF4444, false);
                }
            }
        }

        // ---- Combat: Keystrokes ----
        if (KEYS.on) {
            int kw = 76;
            int kh = 52;
            panel(g, keysX, keysY, kw, kh, 0x990B101B, 0xFF1E293B);

            keyBox(g, mc, keysX + 28, keysY + 4, 20, 14, "W", mc.options.keyUp.isDown());
            keyBox(g, mc, keysX + 6, keysY + 20, 20, 14, "A", mc.options.keyLeft.isDown());
            keyBox(g, mc, keysX + 28, keysY + 20, 20, 14, "S", mc.options.keyDown.isDown());
            keyBox(g, mc, keysX + 50, keysY + 20, 20, 14, "D", mc.options.keyRight.isDown());
            keyBox(g, mc, keysX + 6, keysY + 36, 31, 12, "LMB", mc.options.keyAttack.isDown());
            keyBox(g, mc, keysX + 39, keysY + 36, 31, 12, "RMB", mc.options.keyUse.isDown());
        }

        // ---- Player kozmetik & animasyon gostergesi ----
        List<String> playerTags = new ArrayList<>();
        if (HAT.on) playerTags.add("Sapka[" + keyName(HAT.keyCode) + "]");
        if (HALO.on) playerTags.add("Hale");
        if (WALK_ANIM.on && p.walkAnimation.speed() > 0.05f) {
            int phase = (int) ((now / 120L) % 4L);
            String[] frames = {"|", "/", "-", "\\"};
            playerTags.add("Adim " + frames[phase]);
        }
        if (GLIDE_POSE.on && p.isFallFlying()) {
            playerTags.add("Suzulme Pozu");
        }
        if (SPRINT.on && p.isSprinting()) {
            playerTags.add("Oto Kosu");
        }
        if (!playerTags.isEmpty()) {
            String pLine = String.join("  ", playerTags);
            int pw = mc.font.width(pLine) + 12;
            int px = sw - pw - 8;
            int py = 8;
            panel(g, px, py, pw, 18, 0xA80B101B, 0xFF1E293B);
            g.drawString(mc.font, pLine, px + 6, py + 5, 0xFFE2E8F0, false);
        }

        // ---- Elytra ucus gorsel animasyonu ve telemetri ----
        if (p.isFallFlying()) {
            if (ELYTRA_ANIM.on) {
                double wave = Math.sin(now / 110.0) * 18.0;
                int wingSpan = 48;
                int wy = cy + 24 + (int) Math.round(wave * 0.25);
                g.fill(cx - wingSpan, wy, cx - 10, wy + 2, accent);
                g.fill(cx + 10, wy, cx + wingSpan, wy + 2, accent);
            }
            if (ELYTRA_TRAIL.on) {
                for (int i = 1; i <= 4; i++) {
                    int offset = i * 9;
                    g.fill(cx - 24 - offset, cy + 28 + offset / 2,
                        cx - 20 - offset, cy + 30 + offset / 2, 0x6638BDF8);
                    g.fill(cx + 20 + offset, cy + 28 + offset / 2,
                        cx + 24 + offset, cy + 30 + offset / 2, 0x6638BDF8);
                }
            }
            if (ELYTRA_HUD.on) {
                double spd = p.getDeltaMovement().length() * 20.0;
                String eInfo = String.format(Locale.ROOT, "Elytra: %.1f b/s | Y:%.0f", spd, p.getY());
                int ew = mc.font.width(eInfo) + 12;
                int ex = (sw - ew) / 2;
                int ey = sh - 54;
                panel(g, ex, ey, ew, 18, 0xB80B101B, accent);
                g.drawString(mc.font, eInfo, ex + 6, ey + 5, 0xFFF8FAFC, false);
            }
            if (BOOST_WARN.on && p.getDeltaMovement().y < -0.55) {
                String warn = "DIKKAT: HIZLI INIS";
                int w2 = mc.font.width(warn) + 12;
                g.fill((sw - w2) / 2, cy - 28, (sw + w2) / 2, cy - 12, 0xCC991B1B);
                g.drawString(mc.font, warn, (sw - w2) / 2 + 6, cy - 24, 0xFFFFFFFF, true);
            }
        }
    }

    // ==================== Kayit / yukleme ====================

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("nexus.properties");
    }

    static void save() {
        Properties p = new Properties();
        for (Feature f : FEATURES) {
            p.setProperty("f." + f.key, String.valueOf(f.on));
            p.setProperty("k." + f.key, String.valueOf(f.keyCode));
        }
        p.setProperty("watermark.on", String.valueOf(showWatermark));
        p.setProperty("wm.x", String.valueOf(wmX));
        p.setProperty("wm.y", String.valueOf(wmY));
        p.setProperty("hud.x", String.valueOf(hudX));
        p.setProperty("hud.y", String.valueOf(hudY));
        p.setProperty("keys.x", String.valueOf(keysX));
        p.setProperty("keys.y", String.valueOf(keysY));
        p.setProperty("armor.x", String.valueOf(armorX));
        p.setProperty("armor.y", String.valueOf(armorY));
        p.setProperty("target.x", String.valueOf(targetX));
        p.setProperty("target.y", String.valueOf(targetY));
        p.setProperty("color", String.valueOf(colorIdx));
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
            for (Feature f : FEATURES) {
                f.on = Boolean.parseBoolean(p.getProperty("f." + f.key, String.valueOf(f.on)));
                f.keyCode = Integer.parseInt(p.getProperty("k." + f.key, String.valueOf(f.keyCode)));
            }
            showWatermark = Boolean.parseBoolean(p.getProperty("watermark.on", "true"));
            wmX = Integer.parseInt(p.getProperty("wm.x", String.valueOf(wmX)));
            wmY = Integer.parseInt(p.getProperty("wm.y", String.valueOf(wmY)));
            hudX = Integer.parseInt(p.getProperty("hud.x", String.valueOf(hudX)));
            hudY = Integer.parseInt(p.getProperty("hud.y", String.valueOf(hudY)));
            keysX = Integer.parseInt(p.getProperty("keys.x", String.valueOf(keysX)));
            keysY = Integer.parseInt(p.getProperty("keys.y", String.valueOf(keysY)));
            armorX = Integer.parseInt(p.getProperty("armor.x", String.valueOf(armorX)));
            armorY = Integer.parseInt(p.getProperty("armor.y", String.valueOf(armorY)));
            targetX = Integer.parseInt(p.getProperty("target.x", String.valueOf(targetX)));
            targetY = Integer.parseInt(p.getProperty("target.y", String.valueOf(targetY)));
            colorIdx = Math.floorMod(Integer.parseInt(p.getProperty("color", "0")), COLORS.length);
        } catch (NumberFormatException ignored) {
        }
    }

    static void resetPositions() {
        wmX = 8; wmY = 8;
        hudX = 8; hudY = 34;
        keysX = 8; keysY = 152;
        armorX = 8; armorY = 212;
        targetX = 8; targetY = 244;
        save();
    }

    // ==================== Hile menusu gorunumlu GUI ====================

    public static class NexusMenu extends Screen {

        private static int tab = 0;
        private static String searchQuery = "";
        private static Feature listeningFeature = null;

        private EditBox searchBox;

        public NexusMenu() {
            super(Component.literal("Nexus Client"));
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        protected void init() {
            listeningFeature = null;

            int panelW = 420;
            int panelH = 236;
            int px = (width - panelW) / 2;
            int py = (height - panelH) / 2;

            // Sol kolon sekmeleri
            for (int i = 0; i < TABS.length; i++) {
                final int idx = i;
                boolean selected = (i == tab);
                String tLabel = (selected ? "> " : "  ") + TABS[i];
                addRenderableWidget(Button.builder(Component.literal(tLabel), b -> {
                    tab = idx;
                    rebuildWidgets();
                }).bounds(px + 10, py + 32 + i * 24, 96, 20).build());
            }

            // Arama kutusu (Ayar disindaki sekmelerde)
            if (!TABS[tab].equals("Ayar")) {
                searchBox = new EditBox(font, px + 116, py + 32, panelW - 126, 18,
                    Component.literal("Ara..."));
                searchBox.setMaxLength(32);
                searchBox.setValue(searchQuery);
                searchBox.setResponder(val -> {
                    searchQuery = val;
                    rebuildModuleButtons(px + 116, py + 56, panelW - 126);
                });
                addRenderableWidget(searchBox);
                rebuildModuleButtons(px + 116, py + 56, panelW - 126);
            } else {
                searchBox = null;
                buildSettings(px + 116, py + 32);
            }
        }

        private void rebuildModuleButtons(int startX, int startY, int areaW) {
            // Mevcut modul/bind butonlarini temizlemek icin ekran elemanlarini tazele
            clearWidgets();

            int panelW = 420;
            int panelH = 236;
            int px = (width - panelW) / 2;
            int py = (height - panelH) / 2;

            for (int i = 0; i < TABS.length; i++) {
                final int idx = i;
                boolean selected = (i == tab);
                String tLabel = (selected ? "> " : "  ") + TABS[i];
                addRenderableWidget(Button.builder(Component.literal(tLabel), b -> {
                    tab = idx;
                    rebuildWidgets();
                }).bounds(px + 10, py + 32 + i * 24, 96, 20).build());
            }

            searchBox = new EditBox(font, px + 116, py + 32, panelW - 126, 18,
                Component.literal("Ara..."));
            searchBox.setMaxLength(32);
            searchBox.setValue(searchQuery);
            searchBox.setResponder(val -> {
                if (!val.equals(searchQuery)) {
                    searchQuery = val;
                    rebuildModuleButtons(startX, startY, areaW);
                }
            });
            addRenderableWidget(searchBox);

            String q = searchQuery.trim().toLowerCase(Locale.ROOT);
            List<Feature> matching = new ArrayList<>();
            for (Feature f : FEATURES) {
                boolean tabMatch = q.isEmpty() && f.tab.equals(TABS[tab]);
                boolean searchMatch = !q.isEmpty() && (
                    f.name.toLowerCase(Locale.ROOT).contains(q)
                        || f.desc.toLowerCase(Locale.ROOT).contains(q)
                        || f.tab.toLowerCase(Locale.ROOT).contains(q)
                );
                if (tabMatch || searchMatch) {
                    matching.add(f);
                }
            }

            int rowH = 24;
            int maxRows = 7;
            for (int i = 0; i < Math.min(matching.size(), maxRows); i++) {
                Feature f = matching.get(i);
                int by = startY + i * rowH;

                String toggleText = f.name + ": " + (f.on ? "ACIK" : "KAPALI");
                addRenderableWidget(Button.builder(Component.literal(toggleText), b -> {
                    f.on = !f.on;
                    save();
                    rebuildModuleButtons(startX, startY, areaW);
                }).bounds(startX, by, areaW - 78, 20).build());

                String bindText = (listeningFeature == f) ? "[TUS BAS]" : "[" + keyName(f.keyCode) + "]";
                addRenderableWidget(Button.builder(Component.literal(bindText), b -> {
                    listeningFeature = (listeningFeature == f) ? null : f;
                    rebuildModuleButtons(startX, startY, areaW);
                }).bounds(startX + areaW - 74, by, 74, 20).build());
            }
        }

        private void buildSettings(int x, int y) {
            int accent = COLORS[colorIdx];

            addRenderableWidget(Button.builder(
                Component.literal("Watermark: " + (showWatermark ? "ACIK" : "KAPALI")), b -> {
                    showWatermark = !showWatermark;
                    save();
                    rebuildWidgets();
                }).bounds(x, y, 145, 20).build());

            addRenderableWidget(Button.builder(
                Component.literal("Tema Rengi: " + COLOR_NAMES[colorIdx]), b -> {
                    colorIdx = (colorIdx + 1) % COLORS.length;
                    save();
                    rebuildWidgets();
                }).bounds(x + 150, y, 144, 20).build());

            y += 26;
            mover(x, y, "Watermark", () -> wmX, v -> wmX = v, () -> wmY, v -> wmY = v);
            y += 24;
            mover(x, y, "HUD", () -> hudX, v -> hudX = v, () -> hudY, v -> hudY = v);
            y += 24;
            mover(x, y, "Keystrokes", () -> keysX, v -> keysX = v, () -> keysY, v -> keysY = v);
            y += 24;
            mover(x, y, "Zirh", () -> armorX, v -> armorX = v, () -> armorY, v -> armorY = v);
            y += 24;
            mover(x, y, "Hedef", () -> targetX, v -> targetX = v, () -> targetY, v -> targetY = v);
            y += 28;

            addRenderableWidget(Button.builder(Component.literal("Konumlari Sifirla"), b -> {
                resetPositions();
                rebuildWidgets();
            }).bounds(x, y, 145, 20).build());

            // Aktif vurgu rengi gostergesi
            Button swatch = Button.builder(Component.literal("Aktif Renk"), b -> { })
                .bounds(x + 150, y, 144, 20).build();
            swatch.active = false;
            addRenderableWidget(swatch);
        }

        private void mover(int x, int y, String label,
                           IntSupplier gx, IntConsumer sx, IntSupplier gy, IntConsumer sy) {
            String coordText = label + " (" + gx.getAsInt() + "," + gy.getAsInt() + ")";
            Button name = Button.builder(Component.literal(coordText), b -> { })
                .bounds(x, y, 166, 20).build();
            name.active = false;
            addRenderableWidget(name);

            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                sx.accept(Math.max(0, gx.getAsInt() - 8));
                save();
                rebuildWidgets();
            }).bounds(x + 170, y, 28, 20).build());

            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                sx.accept(Math.min(width - 20, gx.getAsInt() + 8));
                save();
                rebuildWidgets();
            }).bounds(x + 201, y, 28, 20).build());

            addRenderableWidget(Button.builder(Component.literal("^"), b -> {
                sy.accept(Math.max(0, gy.getAsInt() - 8));
                save();
                rebuildWidgets();
            }).bounds(x + 232, y, 28, 20).build());

            addRenderableWidget(Button.builder(Component.literal("v"), b -> {
                sy.accept(Math.min(height - 20, gy.getAsInt() + 8));
                save();
                rebuildWidgets();
            }).bounds(x + 263, y, 28, 20).build());
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (listeningFeature != null) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    listeningFeature.keyCode = -1;
                } else {
                    listeningFeature.keyCode = keyCode;
                }
                listeningFeature = null;
                save();
                rebuildWidgets();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            int panelW = 420;
            int panelH = 236;
            int px = (width - panelW) / 2;
            int py = (height - panelH) / 2;

            // Arka plan karartma ve client penceresi
            graphics.fill(0, 0, width, height, 0x77000000);
            panel(graphics, px, py, panelW, panelH, 0xEE0B101B, COLORS[colorIdx]);
            graphics.fill(px, py, px + panelW, py + 24, 0xFF111827);
            graphics.fill(px + 106, py + 24, px + 107, py + panelH, 0xFF1E293B);

            graphics.drawString(font, "NEXUS CLIENT", px + 12, py + 8, COLORS[colorIdx], true);
            String hint = (listeningFeature != null)
                ? "Bir tusa basin (ESC = Kaldir)"
                : "Menu: P | Kapat: ESC";
            graphics.drawString(font, hint, px + panelW - font.width(hint) - 12, py + 8, 0xFF94A3B8, false);

            super.render(graphics, mouseX, mouseY, delta);
        }
    }
}
