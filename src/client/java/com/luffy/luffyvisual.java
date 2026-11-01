package com.luffy;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class luffyvisual implements ClientModInitializer {

    private static boolean gorunur = false;
    private static KeyMapping acKapatKey;

    private static int kutuX = 5;
    private static int kutuY = 5;
    private static int genislik = 130;
    private static int yukseklik = 42;

    private static final String CONFIG_DOSYA_ADI = "luffyvisual.yaml";

    @Override
    public void onInitializeClient() {
        configYukle();

        KeyMapping.Category cat = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("luffyvisual", "main"));
        acKapatKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.luffyvisual.toggle", GLFW.GLFW_KEY_K, cat));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (acKapatKey.consumeClick()) {
                gorunur = !gorunur;
            }
        });

        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            Identifier.fromNamespaceAndPath("luffyvisual", "kutu"),
            (graphics, tickCounter) -> {
                Minecraft mc = Minecraft.getInstance();
                if (!gorunur || mc.player == null || mc.options.hideGui) return;

                int x = mc.player.getBlockX();
                int y = mc.player.getBlockY();
                int z = mc.player.getBlockZ();
                int fps = mc.getFps();

                graphics.fill(kutuX, kutuY, kutuX + genislik, kutuY + yukseklik, 0x90000000);
                graphics.drawString(mc.font, "Koordinat: " + x + ", " + y + ", " + z,
                    kutuX + 5, kutuY + 6, 0xFFFFFFFF, true);
                graphics.drawString(mc.font, "FPS: " + fps,
                    kutuX + 5, kutuY + 22, 0xFFFFFFFF, true);
            }
        );
    }

    private static void configYukle() {
        Path yol = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_DOSYA_ADI);
        try {
            if (!Files.exists(yol)) {
                Files.writeString(yol,
                    "# Kutunun ekrandaki konumu ve boyutu\n" +
                    "kutu_x: 5\n" +
                    "kutu_y: 5\n" +
                    "genislik: 130\n" +
                    "yukseklik: 42\n");
            }
            List<String> satirlar = Files.readAllLines(yol);
            for (String satir : satirlar) {
                satir = satir.trim();
                if (satir.isEmpty() || satir.startsWith("#")) continue;
                int ayrac = satir.indexOf(':');
                if (ayrac < 0) continue;
                String anahtar = satir.substring(0, ayrac).trim();
                String deger = satir.substring(ayrac + 1).trim();
                try {
                    int sayi = Integer.parseInt(deger);
                    switch (anahtar) {
                        case "kutu_x" -> kutuX = sayi;
                        case "kutu_y" -> kutuY = sayi;
                        case "genislik" -> genislik = sayi;
                        case "yukseklik" -> yukseklik = sayi;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception e) {
            System.err.println("[luffyvisual] Config okunamadi: " + e.getMessage());
        }
    }
}
