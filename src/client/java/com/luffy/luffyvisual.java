package com.example.ornekmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class OrnekModClient implements ClientModInitializer {

    private static boolean gorunur = false;
    private static KeyBinding acKapatKey;

    // Varsayılan değerler (config dosyası yoksa veya okunamazsa kullanılır)
    private static int kutuX = 5;
    private static int kutuY = 5;
    private static int genislik = 130;
    private static int yukseklik = 42;

    private static final String CONFIG_DOSYA_ADI = "ornekmod.yaml";

    @Override
    public void onInitializeClient() {
        configYukle();

        acKapatKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ornekmod.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.ornekmod"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (acKapatKey.wasPressed()) {
                gorunur = !gorunur;
            }
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (!gorunur) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            int x = client.player.getBlockX();
            int y = client.player.getBlockY();
            int z = client.player.getBlockZ();
            int fps = client.getCurrentFps();

            context.fill(kutuX, kutuY, kutuX + genislik, kutuY + yukseklik, 0x90000000);
            context.drawTextWithShadow(client.textRenderer,
                    "Koordinat: " + x + ", " + y + ", " + z,
                    kutuX + 5, kutuY + 6, 0xFFFFFF);
            context.drawTextWithShadow(client.textRenderer,
                    "FPS: " + fps,
                    kutuX + 5, kutuY + 22, 0xFFFFFF);
        });
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
                    // Geçersiz değer: varsayılan kullanılır
                }
            }
        } catch (Exception e) {
            System.err.println("[ornekmod] Config okunamadı, varsayılanlar kullanılıyor: " + e.getMessage());
        }
    }
}
