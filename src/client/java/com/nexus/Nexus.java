package com.nexus;

import com.mojang.blaze3d.platform.GlStateManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Nexus implements ClientModInitializer {

    public static final String MOD_ID = "nexus";
    public static final String CLIENT_TITLE = "NEXUS x AXOLOTL";

    public static boolean axolotlBadge = true;
    public static boolean axolotlMascot = true;
    public static boolean santaHat = true;
    public static boolean jumpCircles = true;
    public static boolean crosshairSpin = true;
    public static boolean frostedDashboard = true;

    public static KeyBinding openDashboardKey;

    private static final List<JumpRing> activeRings = new ArrayList<>();
    private static boolean wasOnGround = true;

    private static float spinAngle = 0.0f;
    private static float focusProgress = 0.0f;

    @Override
    public void onInitializeClient() {
        openDashboardKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.nexus.dashboard",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.nexus.visuals"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        HudRenderCallback.EVENT.register(this::onHudRender);
        WorldRenderEvents.AFTER_ENTITIES.register(this::onWorldRender);
    }

    private void onClientTick(MinecraftClient client) {
        while (openDashboardKey.wasPressed()) {
            if (client.player != null) {
                client.setScreen(new NexusDashboardScreen(Text.literal(CLIENT_TITLE)));
            }
        }

        if (client.player == null || client.world == null) {
            return;
        }

        if (jumpCircles) {
            boolean onGround = client.player.isOnGround();
            Vec3d pos = client.player.getPos();

            if (wasOnGround && !onGround && client.player.getVelocity().y > 0.05) {
                activeRings.add(new JumpRing(pos.x, pos.y + 0.05, pos.z, 0.25f, 1.35f, 18));
                spawnAxolotlParticles(client, pos.x, pos.y + 0.1, pos.z, 10);
            } else if (!wasOnGround && onGround) {
                activeRings.add(new JumpRing(pos.x, pos.y + 0.05, pos.z, 0.20f, 1.60f, 22));
                spawnAxolotlParticles(client, pos.x, pos.y + 0.1, pos.z, 14);
            }

            wasOnGround = onGround;

            Iterator<JumpRing> iterator = activeRings.iterator();
            while (iterator.hasNext()) {
                JumpRing ring = iterator.next();
                ring.tick();
                if (ring.isExpired()) {
                    iterator.remove();
                }
            }
        } else {
            activeRings.clear();
        }

        if (crosshairSpin) {
            boolean nearPlayerHitbox = isCrosshairNearPlayer(client, 6.0);
            float target = nearPlayerHitbox ? 1.0f : 0.25f;
            focusProgress = MathHelper.lerp(0.22f, focusProgress, target);
            spinAngle = (spinAngle + (nearPlayerHitbox ? 6.5f : 2.0f)) % 360.0f;
        } else {
            focusProgress = 0.0f;
        }
    }

    private void spawnAxolotlParticles(MinecraftClient client, double x, double y, double z, int count) {
        if (client.world == null) return;
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 * i) / count;
            double vx = Math.cos(angle) * 0.04;
            double vz = Math.sin(angle) * 0.04;
            client.world.addParticle(ParticleTypes.END_ROD, x, y, z, vx, 0.02, vz);
        }
    }

    private boolean isCrosshairNearPlayer(MinecraftClient client, double maxDistance) {
        if (client.player == null || client.world == null) return false;

        Vec3d eyePos = client.player.getCameraPosVec(1.0f);
        Vec3d lookVec = client.player.getRotationVec(1.0f);
        Vec3d reachEnd = eyePos.add(lookVec.multiply(maxDistance));

        for (PlayerEntity other : client.world.getPlayers()) {
            if (other == client.player || !other.isAlive()) continue;
            Box expandedBox = other.getBoundingBox().expand(0.35);
            if (expandedBox.raycast(eyePos, reachEnd).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        if (axolotlBadge) {
            int panelX = 10;
            int panelY = 10;
            context.fill(panelX, panelY, panelX + 156, panelY + 24, 0x99181C28);
            context.fill(panelX, panelY, panelX + 156, panelY + 2, 0xFFFF77B7);
            context.drawTextWithShadow(
                    client.textRenderer,
                    Text.literal("✦ " + CLIENT_TITLE),
                    panelX + 8,
                    panelY + 8,
                    0xFF8CF7FF
            );
        }

        if (crosshairSpin && focusProgress > 0.02f) {
            float centerX = width / 2.0f;
            float centerY = height / 2.0f;
            float radius = MathHelper.lerp(focusProgress, 18.0f, 11.0f);

            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(centerX, centerY, 0);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spinAngle));

            for (int i = 0; i < 4; i++) {
                matrices.push();
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * 90.0f));
                int color = (i % 2 == 0) ? 0xFFFF77B7 : 0xFF7CF8FF;

                context.fill(-1, (int) -radius - 6, 1, (int) -radius, color);
                context.fill(-3, (int) -radius - 4, 0, (int) -radius - 2, color);
                context.fill(0, (int) -radius - 4, 3, (int) -radius - 2, color);
                matrices.pop();
            }

            matrices.pop();
        }
    }

    private void onWorldRender(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        Vec3d cameraPos = context.camera().getPos();

        if (jumpCircles && !activeRings.isEmpty()) {
            renderJumpCircles(matrices, cameraPos);
        }

        if (santaHat) {
            for (PlayerEntity player : client.world.getPlayers()) {
                if (player == client.player && client.options.getPerspective().isFirstPerson()) {
                    continue;
                }
                renderSantaHatAbovePlayer(matrices, cameraPos, player);
            }
        }
    }

    private void renderJumpCircles(MatrixStack matrices, Vec3d cameraPos) {
        GlStateManager._enableBlend();
        GlStateManager._blendFunc(GlStateManager.SrcFactor.SRC_ALPHA.value, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA.value);
        GlStateManager._disableCull();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        int segments = 36;
        for (JumpRing ring : activeRings) {
            float alpha = ring.getAlpha();
            int a = (int) (alpha * 255.0f) & 0xFF;

            for (int i = 0; i < segments; i++) {
                double a1 = (Math.PI * 2.0 * i) / segments;
                double a2 = (Math.PI * 2.0 * (i + 1)) / segments;

                float x1 = (float) (ring.x + Math.cos(a1) * ring.currentRadius);
                float z1 = (float) (ring.z + Math.sin(a1) * ring.currentRadius);
                float x2 = (float) (ring.x + Math.cos(a2) * ring.currentRadius);
                float z2 = (float) (ring.z + Math.sin(a2) * ring.currentRadius);

                int r = (i % 2 == 0) ? 255 : 124;
                int g = (i % 2 == 0) ? 119 : 248;
                int b = (i % 2 == 0) ? 183 : 255;

                buffer.vertex(matrix, x1, (float) ring.y, z1).color(r, g, b, a);
                buffer.vertex(matrix, x2, (float) ring.y, z2).color(r, g, b, a);
            }
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();

        GlStateManager._enableCull();
        GlStateManager._disableBlend();
    }

    private void renderSantaHatAbovePlayer(MatrixStack matrices, Vec3d cameraPos, PlayerEntity player) {
        double px = MathHelper.lerp(0.5, player.prevX, player.getX());
        double py = MathHelper.lerp(0.5, player.prevY, player.getY());
        double pz = MathHelper.lerp(0.5, player.prevZ, player.getZ());

        float headY = (float) (py + player.getEyeHeight(player.getPose()) + 0.14 - cameraPos.y);
        float relX = (float) (px - cameraPos.x);
        float relZ = (float) (pz - cameraPos.z);

        GlStateManager._enableBlend();
        GlStateManager._blendFunc(GlStateManager.SrcFactor.SRC_ALPHA.value, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA.value);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        matrices.push();
        matrices.translate(relX, headY, relZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-player.getYaw()));
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        addBox(buffer, matrix, -0.26f, 0.00f, -0.26f, 0.26f, 0.08f, 0.26f, 248, 250, 255, 235);
        addBox(buffer, matrix, -0.22f, 0.08f, -0.22f, 0.22f, 0.24f, 0.22f, 235, 45, 75, 235);
        addBox(buffer, matrix, -0.14f, 0.24f, -0.28f, 0.14f, 0.34f, -0.06f, 235, 45, 75, 235);
        addBox(buffer, matrix, -0.07f, 0.32f, -0.34f, 0.07f, 0.44f, -0.20f, 255, 255, 255, 245);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();

        GlStateManager._disableBlend();
    }

    private static void addBox(
            BufferBuilder buffer,
            Matrix4f m,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            int r, int g, int b, int a
    ) {
        buffer.vertex(m, x0, y0, z1).color(r, g, b, a);
        buffer.vertex(m, x1, y0, z1).color(r, g, b, a);
        buffer.vertex(m, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(m, x0, y1, z1).color(r, g, b, a);

        buffer.vertex(m, x1, y0, z0).color(r, g, b, a);
        buffer.vertex(m, x0, y0, z0).color(r, g, b, a);
        buffer.vertex(m, x0, y1, z0).color(r, g, b, a);
        buffer.vertex(m, x1, y1, z0).color(r, g, b, a);

        buffer.vertex(m, x0, y0, z0).color(r, g, b, a);
        buffer.vertex(m, x0, y0, z1).color(r, g, b, a);
        buffer.vertex(m, x0, y1, z1).color(r, g, b, a);
        buffer.vertex(m, x0, y1, z0).color(r, g, b, a);

        buffer.vertex(m, x1, y0, z1).color(r, g, b, a);
        buffer.vertex(m, x1, y0, z0).color(r, g, b, a);
        buffer.vertex(m, x1, y1, z0).color(r, g, b, a);
        buffer.vertex(m, x1, y1, z1).color(r, g, b, a);

        buffer.vertex(m, x0, y1, z1).color(r, g, b, a);
        buffer.vertex(m, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(m, x1, y1, z0).color(r, g, b, a);
        buffer.vertex(m, x0, y1, z0).color(r, g, b, a);
    }

    private static class JumpRing {
        final double x, y, z;
        final float startRadius, endRadius;
        final int maxAge;
        int age = 0;
        float currentRadius;

        JumpRing(double x, double y, double z, float startRadius, float endRadius, int maxAge) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.startRadius = startRadius;
            this.endRadius = endRadius;
            this.maxAge = maxAge;
            this.currentRadius = startRadius;
        }

        void tick() {
            age++;
            float progress = Math.min(1.0f, (float) age / maxAge);
            currentRadius = MathHelper.lerp(progress, startRadius, endRadius);
        }

        float getAlpha() {
            return Math.max(0.0f, 1.0f - ((float) age / maxAge));
        }

        boolean isExpired() {
            return age >= maxAge;
        }
    }

    public static class NexusDashboardScreen extends Screen {

        protected NexusDashboardScreen(Text title) {
            super(title);
        }

        private TextRenderer getRenderer() {
            return MinecraftClient.getInstance().textRenderer;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (frostedDashboard) {
                super.renderBackground(context, mouseX, mouseY, delta);
            }

            TextRenderer tr = getRenderer();
            int panelW = Math.min(this.width - 40, 440);
            int panelH = Math.min(this.height - 40, 250);
            int left = (this.width - panelW) / 2;
            int top = (this.height - panelH) / 2;

            context.fill(left, top, left + panelW, top + panelH, 0xCC121622);
            context.fill(left, top, left + panelW, top + 2, 0xFFFF77B7);
            context.fill(left, top + panelH - 2, left + panelW, top + panelH, 0xFF7CF8FF);

            context.drawTextWithShadow(
                    tr,
                    Text.literal(CLIENT_TITLE + " • TEK EKRAN GÖRSEL PANEL"),
                    left + 14,
                    top + 12,
                    0xFFFFFFFF
            );

            int col1X = left + 14;
            int col2X = left + (panelW / 2) + 8;
            int y = top + 36;

            context.drawTextWithShadow(tr, Text.literal("AXOLOTL"), col1X, y, 0xFFFF77B7);
            context.drawTextWithShadow(tr, Text.literal("VISUAL & COSMETIC"), col2X, y, 0xFF7CF8FF);
            y += 18;

            drawToggleRow(context, tr, col1X, y, "Axolotl Rozeti", axolotlBadge);
            drawToggleRow(context, tr, col2X, y, "Crosshair Spin (4x)", crosshairSpin);
            y += 24;

            drawToggleRow(context, tr, col1X, y, "Axolotl Maskot", axolotlMascot);
            drawToggleRow(context, tr, col2X, y, "Jump Circles", jumpCircles);
            y += 24;

            drawToggleRow(context, tr, col1X, y, "Frosted Menu", frostedDashboard);
            drawToggleRow(context, tr, col2X, y, "Santa Hat", santaHat);

            context.drawTextWithShadow(
                    tr,
                    Text.literal("Tıklayarak aç/kapat • ESC ile çık"),
                    left + 14,
                    top + panelH - 18,
                    0xFFA0A8C0
            );

            super.render(context, mouseX, mouseY, delta);
        }

        private void drawToggleRow(DrawContext context, TextRenderer tr, int x, int y, String label, boolean enabled) {
            context.fill(x, y, x + 190, y + 18, 0x99202638);
            context.drawTextWithShadow(tr, Text.literal(label), x + 6, y + 5, 0xFFE6ECF8);
            context.drawTextWithShadow(
                    tr,
                    Text.literal(enabled ? "[AÇIK]" : "[KAPALI]"),
                    x + 142,
                    y + 5,
                    enabled ? 0xFF7CF8FF : 0xFFFF77B7
            );
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                int panelW = Math.min(this.width - 40, 440);
                int panelH = Math.min(this.height - 40, 250);
                int left = (this.width - panelW) / 2;
                int top = (this.height - panelH) / 2;

                int col1X = left + 14;
                int col2X = left + (panelW / 2) + 8;
                int row1Y = top + 54;
                int row2Y = top + 78;
                int row3Y = top + 102;

                if (inBox(mouseX, mouseY, col1X, row1Y, 190, 18)) { axolotlBadge = !axolotlBadge; return true; }
                if (inBox(mouseX, mouseY, col1X, row2Y, 190, 18)) { axolotlMascot = !axolotlMascot; return true; }
                if (inBox(mouseX, mouseY, col1X, row3Y, 190, 18)) { frostedDashboard = !frostedDashboard; return true; }

                if (inBox(mouseX, mouseY, col2X, row1Y, 190, 18)) { crosshairSpin = !crosshairSpin; return true; }
                if (inBox(mouseX, mouseY, col2X, row2Y, 190, 18)) { jumpCircles = !jumpCircles; return true; }
                if (inBox(mouseX, mouseY, col2X, row3Y, 190, 18)) { santaHat = !santaHat; return true; }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        private boolean inBox(double mx, double my, int x, int y, int w, int h) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }
}
