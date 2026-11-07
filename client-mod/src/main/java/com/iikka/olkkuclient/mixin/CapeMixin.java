package com.iikka.olkkuclient.mixin;

import com.iikka.olkkuclient.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Puts your chosen Olkku's Client cape on you. */
@Mixin(AbstractClientPlayer.class)
public class CapeMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void olkku$cape(CallbackInfoReturnable<PlayerSkin> cir) {
        try {
            String cape = Config.INSTANCE.cape;
            if (cape == null || cape.isEmpty() || (Object) this != Minecraft.getInstance().player) return;
            PlayerSkin s = cir.getReturnValue();
            if (s == null) return;
            var tex = new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath("olkkuclient", "cape/" + cape));
            cir.setReturnValue(new PlayerSkin(s.body(), tex, s.elytra(), s.model(), s.secure()));
        } catch (Throwable ignored) { }
    }
}
