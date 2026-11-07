package com.iikka.silly;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.iikka.silly.Arenas.Arena;
import com.iikka.silly.Weapons.Weapon;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SillyMod implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("silly-roulette");
    public static final int ROUND = 30 * 20; // a new weapon every 30 seconds

    static MinecraftServer server;
    static long tick;
    static int roundTick;
    static final Random RNG = new Random();

    // ---------- saved in the world folder ----------
    static final class State {
        String map = "colosseum";
        boolean arenaWorld = false;
        boolean roulette = true;
        List<String> built = new ArrayList<>();
        List<String> seen = new ArrayList<>();
    }
    static State state = new State();
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static Path stateFile() { return server.getWorldPath(LevelResource.ROOT).resolve("silly_roulette.json"); }

    static void save() {
        try { Files.writeString(stateFile(), GSON.toJson(state)); } catch (Throwable t) { warn("save", t); }
    }

    static boolean load() {
        try {
            Path f = stateFile();
            if (!Files.exists(f)) return false;
            State s = GSON.fromJson(Files.readString(f), State.class);
            if (s != null) state = s;
            if (state.built == null) state.built = new ArrayList<>();
            if (state.seen == null) state.seen = new ArrayList<>();
            return true;
        } catch (Throwable t) { warn("load", t); return true; }
    }

    public static void warn(String what, Throwable t) { LOG.warn("Silly Roulette: {} failed: {}", what, t.toString()); }

    // ---------- small timers ----------
    record Later(long at, Runnable r) { }
    static final List<Later> LATER = new ArrayList<>();
    public static void later(int ticks, Runnable r) { LATER.add(new Later(tick + Math.max(1, ticks), r)); }

    // ---------- no fall damage after being launched ----------
    static final Map<UUID, Integer> NO_FALL = new ConcurrentHashMap<>();
    public static void protectFall(ServerPlayer p) { NO_FALL.put(p.getUUID(), 0); }

    // ---------- current weapon of each player ----------
    static final Map<UUID, Weapon> CURRENT = new HashMap<>();

    @Override
    public void onInitialize() {
        LOG.info("Silly Weapon Roulette: {} weapons, {} maps", Weapons.ALL.size(), Arenas.ALL.size());

        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            try {
                if (!load()) {
                    ServerLevel ow = s.overworld();
                    // a brand new world: build the arena and start everyone there
                    state.arenaWorld = ow.getGameTime() < 400;
                    if (state.arenaWorld) ensureBuilt(Arenas.get(state.map));
                    save();
                }
            } catch (Throwable t) { warn("start", t); }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { Fx.Flying.clear(); save(); });

        ServerTickEvents.END_SERVER_TICK.register(s -> { try { onTick(s); } catch (Throwable t) { warn("tick", t); } });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> later(5, () -> {
            try {
                ServerPlayer p = handler.getPlayer();
                if (state.arenaWorld && !state.seen.contains(p.getUUID().toString())) {
                    state.seen.add(p.getUUID().toString());
                    save();
                    sendToArena(p);
                    p.sendSystemMessage(Component.literal("Welcome to Silly Weapon Roulette! A new silly weapon every 30 seconds. Type /map to change the arena.").withStyle(ChatFormatting.GOLD));
                }
                if (state.roulette && findWeapon(p) == null) giveNew(p, false);
            } catch (Throwable t) { warn("join", t); }
        }));

        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> later(2, () -> {
            try {
                if (state.arenaWorld) sendToArena(newP);
                if (state.roulette) giveNew(newP, false);
            } catch (Throwable t) { warn("respawn", t); }
        }));

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer p && source.is(DamageTypeTags.IS_FALL)) {
                if (NO_FALL.containsKey(p.getUUID())) return false;
                if (state.arenaWorld && inAnyArena(p)) return false; // no fall damage in the arenas
            }
            return true;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> onUse(player, hand));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> onUse(player, hand));

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer p) || !(entity instanceof LivingEntity t)) return InteractionResult.PASS;
            Weapon w = weaponOf(p.getMainHandItem());
            if (w != null && w.hit() != null) {
                try { w.hit().run(p, (ServerLevel) p.level(), t); } catch (Throwable e) { warn(w.name(), e); }
            }
            return InteractionResult.PASS;
        });

        CommandRegistrationCallback.EVENT.register((d, reg, env) -> {
            d.register(Commands.literal("map")
                .executes(c -> { listMaps(c.getSource()); return 1; })
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests((c, sb) -> { for (Arena a : Arenas.ALL) sb.suggest(a.id()); return sb.buildFuture(); })
                    .executes(c -> { changeMap(c.getSource(), StringArgumentType.getString(c, "name")); return 1; })));
            d.register(Commands.literal("roulette")
                .executes(c -> { c.getSource().sendSuccess(() -> Component.literal("Silly Weapon Roulette is " + (state.roulette ? "ON" : "OFF") + ". Use /roulette on or /roulette off").withStyle(ChatFormatting.GOLD), false); return 1; })
                .then(Commands.literal("on").executes(c -> { state.roulette = true; save(); roundTick = ROUND - 20; c.getSource().sendSuccess(() -> Component.literal("Roulette ON: new silly weapon every 30 seconds").withStyle(ChatFormatting.GREEN), true); return 1; }))
                .then(Commands.literal("off").executes(c -> { state.roulette = false; save(); c.getSource().sendSuccess(() -> Component.literal("Roulette OFF").withStyle(ChatFormatting.RED), true); return 1; })));
            d.register(Commands.literal("weapon")
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayer();
                    if (p != null) giveNew(p, true);
                    return 1;
                }));
            d.register(Commands.literal("sillytest").executes(c -> { selfTest(); return 1; }));
            d.register(Commands.literal("arena")
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayer();
                    if (p != null) { ensureBuilt(Arenas.get(state.map)); state.arenaWorld = true; save(); sendToArena(p); }
                    return 1;
                }));
        });
    }

    // ---------- using a weapon ----------

    static InteractionResult onUse(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Weapon w = weaponOf(stack);
        if (w == null) return InteractionResult.PASS;
        if (w.use() == null) return InteractionResult.FAIL; // melee weapons do nothing on right click (no eating, no placing)
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer p)) return InteractionResult.PASS;
        if (p.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
        try { w.use().run(p, (ServerLevel) p.level()); } catch (Throwable t) { warn(w.name(), t); }
        if (w.cooldown() > 0) p.getCooldowns().addCooldown(stack, w.cooldown());
        return InteractionResult.SUCCESS;
    }

    // ---------- the weapon items ----------

    public static Weapon weaponOf(ItemStack s) {
        if (s == null || s.isEmpty()) return null;
        Component n = s.get(DataComponents.CUSTOM_NAME);
        if (n == null || !Boolean.TRUE.equals(s.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))) return null;
        Weapon w = Weapons.BY_NAME.get(n.getString());
        return w != null && s.is(w.item()) ? w : null;
    }

    static ItemStack makeItem(Weapon w) {
        ItemStack s = new ItemStack(w.item());
        s.set(DataComponents.CUSTOM_NAME, Component.literal(w.name()).withStyle(st -> st.withColor(w.color()).withBold(true).withItalic(false)));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal(w.desc()).withStyle(st -> st.withColor(ChatFormatting.GRAY).withItalic(false)));
        lore.add(Component.literal(w.use() != null ? "Right click to use" : "Hit someone with it").withStyle(st -> st.withColor(ChatFormatting.YELLOW).withItalic(false)));
        s.set(DataComponents.LORE, new ItemLore(lore));
        s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return s;
    }

    static Weapon findWeapon(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) { Weapon w = weaponOf(inv.getItem(i)); if (w != null) return w; }
        return null;
    }

    /** Takes away the old silly weapon and gives a new random one in the same slot. */
    static void giveNew(ServerPlayer p, boolean announce) {
        Inventory inv = p.getInventory();
        int slot = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (weaponOf(inv.getItem(i)) != null) { if (slot < 0) slot = i; inv.setItem(i, ItemStack.EMPTY); }
        }
        Weapon w = Weapons.random(CURRENT.get(p.getUUID()));
        CURRENT.put(p.getUUID(), w);
        ItemStack s = makeItem(w);
        if (slot >= 0 && slot < 36) inv.setItem(slot, s);
        else if (!inv.add(s)) p.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), s));
        ServerLevel l = (ServerLevel) p.level();
        Fx.sound(l, p.position(), "entity.player.levelup", 0.7f, 1.6f);
        Fx.parts(l, net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, p.position().add(0, 1, 0), 20, 0.5, 0.3);
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(w.name()).withStyle(st -> st.withColor(w.color()).withBold(true))));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(w.desc()).withStyle(ChatFormatting.WHITE)));
        p.sendSystemMessage(Component.literal("New weapon: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(w.name()).withStyle(st -> st.withColor(w.color()).withBold(true)))
            .append(Component.literal(" - " + w.desc()).withStyle(ChatFormatting.GRAY)));
    }

    // ---------- every tick ----------

    static void onTick(MinecraftServer s) {
        tick++;
        if (!LATER.isEmpty()) {
            List<Later> due = new ArrayList<>();
            for (Iterator<Later> it = LATER.iterator(); it.hasNext(); ) { Later x = it.next(); if (x.at <= tick) { due.add(x); it.remove(); } }
            for (Later x : due) { try { x.r.run(); } catch (Throwable t) { warn("later", t); } }
        }
        Fx.Flying.tick();

        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            Integer n = NO_FALL.get(p.getUUID());
            if (n != null) {
                p.resetFallDistance();
                if (n > 4 && (p.onGround() || p.isInWater())) NO_FALL.remove(p.getUUID());
                else NO_FALL.put(p.getUUID(), n + 1);
            }
            if (tick % 20 == 0) {
                Arena moon = Arenas.get("moon");
                if (moon != null && state.built.contains("moon") && Arenas.inside(moon, p.position()) && p.level() == s.overworld()) {
                    Fx.eff(p, MobEffects.JUMP_BOOST, 40, 3); // low gravity on the moon
                    p.resetFallDistance();
                }
            }
        }

        if (!state.roulette) return;
        roundTick++;
        int left = ROUND - roundTick;
        if (left > 0 && left <= 100 && left % 20 == 0) {
            int sec = left / 20;
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                p.sendSystemMessage(Component.literal("New weapon in " + sec + "...").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), true);
                Fx.sound((ServerLevel) p.level(), p.position(), "block.note_block.hat", 0.6f, 1.2f + (5 - sec) * 0.1f);
            }
        }
        if (roundTick >= ROUND) {
            roundTick = 0;
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                try { giveNew(p, true); } catch (Throwable t) { warn("give", t); }
            }
        }
    }

    // ---------- self test (used by the build check) ----------

    static void selfTest() {
        ServerLevel ow = server.overworld();
        Arena a = Arenas.get(state.map);
        ensureBuilt(a);
        Vec3 c = Arenas.spawnSpot(ow, a, new Random(1));
        ServerPlayer fp = net.fabricmc.fabric.api.entity.FakePlayer.get(ow);
        fp.teleportTo(c.x, c.y, c.z);
        fp.setYRot(0); fp.setXRot(0);
        int ok = 0, bad = 0;
        for (Weapon w : Weapons.ALL) {
            try {
                ItemStack item = makeItem(w);
                if (weaponOf(item) != w) throw new IllegalStateException("item not recognised");
                fp.setItemInHand(InteractionHand.MAIN_HAND, item);
                LivingEntity target = (LivingEntity) net.minecraft.world.entity.EntityTypes.PIG.create(ow, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                target.snapTo(c.x, c.y, c.z + 4, 180, 0);
                ow.addFreshEntity(target);
                if (w.use() != null) w.use().run(fp, ow);
                if (w.hit() != null) w.hit().run(fp, ow, target);
                Fx.Flying.tick();
                target.discard();
                ok++;
            } catch (Throwable t) {
                bad++;
                LOG.error("SELFTEST FAIL {}: {}", w.name(), t.toString(), t);
            }
        }
        for (int i = 0; i < 120; i++) { tick++; Fx.Flying.tick(); }
        LOG.info("SELFTEST weapons ok={} failed={} total={}", ok, bad, Weapons.ALL.size());
    }

    // ---------- maps ----------

    static boolean inAnyArena(ServerPlayer p) {
        if (p.level() != server.overworld()) return false;
        for (Arena a : Arenas.ALL) if (state.built.contains(a.id()) && Arenas.inside(a, p.position())) return true;
        return false;
    }

    static void ensureBuilt(Arena a) {
        if (a == null || state.built.contains(a.id())) return;
        long t0 = System.currentTimeMillis();
        Arenas.build(server.overworld(), a);
        state.built.add(a.id());
        save();
        LOG.info("Silly Roulette: built map {} in {} ms", a.id(), System.currentTimeMillis() - t0);
    }

    static void sendToArena(ServerPlayer p) {
        Arena a = Arenas.get(state.map);
        if (a == null) a = Arenas.ALL.get(0);
        ensureBuilt(a);
        ServerLevel ow = server.overworld();
        Vec3 v = Arenas.spawnSpot(ow, a, RNG);
        p.teleportTo(ow, v.x, v.y, v.z, Set.of(), RNG.nextFloat() * 360, 0, false);
        protectFall(p);
        p.sendSystemMessage(Component.literal("Map: " + a.name()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
    }

    static void listMaps(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("Maps (type /map <name>):").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (Arena a : Arenas.ALL) {
            boolean on = a.id().equals(state.map);
            src.sendSuccess(() -> Component.literal((on ? " > " : "   ") + a.id()).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.YELLOW)
                .append(Component.literal("  " + a.name() + " - " + a.about()).withStyle(ChatFormatting.GRAY)), false);
        }
    }

    static void changeMap(CommandSourceStack src, String id) {
        Arena a = Arenas.get(id);
        if (a == null) { src.sendFailure(Component.literal("No map called " + id + ". Type /map to see them all")); return; }
        boolean fresh = !state.built.contains(a.id());
        if (fresh) for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(Component.literal("Building " + a.name() + "...").withStyle(ChatFormatting.YELLOW), true);
        ensureBuilt(a);
        state.map = a.id();
        state.arenaWorld = true;
        save();
        Set<UUID> done = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (done.add(p.getUUID())) {
                sendToArena(p);
                p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 10));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(a.name()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(a.about()).withStyle(ChatFormatting.WHITE)));
            }
        }
        src.sendSuccess(() -> Component.literal("Map changed to " + a.name()).withStyle(ChatFormatting.GREEN), true);
    }
}
