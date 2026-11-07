package com.iikka.move.mixin;

import com.iikka.move.MoveClientState;
import com.iikka.move.MoveMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Crawl on the ground like in a 1-block gap. */
@Mixin(Player.class)
public class CrawlMixin {
    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void move$crawl(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        boolean crawl = self instanceof ServerPlayer sp ? MoveMod.isCrawling(sp.getUUID())
            : self.level().isClientSide() && MoveClientState.crawling && MoveClientState.isLocal(self);
        if (crawl && !self.isPassenger() && !self.getAbilities().flying) {
            self.setPose(Pose.SWIMMING);
            ci.cancel();
        }
    }
}
