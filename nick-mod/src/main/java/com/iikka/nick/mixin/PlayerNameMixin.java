package com.iikka.nick.mixin;

import com.iikka.nick.NickMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chat and join/death messages use your nick. */
@Mixin(Player.class)
public class PlayerNameMixin {
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void nick$name(CallbackInfoReturnable<Component> cir) {
        if ((Object) this instanceof ServerPlayer sp) {
            NickMod.Disguise d = NickMod.get(sp.getUUID());
            if (d != null && d.nick != null) cir.setReturnValue(Component.literal(d.nick));
        }
    }
}
