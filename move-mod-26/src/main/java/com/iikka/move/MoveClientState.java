package com.iikka.move;

import net.minecraft.world.entity.player.Player;

/** Small bit of client state that common code can read safely. */
public final class MoveClientState {
    public static volatile boolean crawling;
    public static volatile int localId = Integer.MIN_VALUE;

    public static boolean isLocal(Player p) { return p.getId() == localId; }
}
