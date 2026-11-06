package com.iikka.cloak.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityAccess {
    @Invoker("updatePlayer")
    void cloak$updatePlayer(ServerPlayer viewer);
}
