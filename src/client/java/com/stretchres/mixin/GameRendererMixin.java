package com.stretchres.mixin;

import com.mojang.blaze3d.platform.Window;
import com.stretchres.StretchClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getProjectionMatrix", at = @At("RETURN"), cancellable = true, require = 0)
    private void stretchres$stretch(float fov, CallbackInfoReturnable<Matrix4f> cir) {
        if (!StretchClient.stretched) return;
        Window w = Minecraft.getInstance().getWindow();
        float current = (float) w.getWidth() / (float) w.getHeight();
        float factor = current / StretchClient.TARGET_RATIO;
        cir.setReturnValue(new Matrix4f(cir.getReturnValue()).scale(factor, 1f, 1f));
    }
}
