package com.iikka.practice;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.util.*;

/**
 * Olkku's Practice: bot fights and 1v1 duels with kits, in the practice world only.
 * (The mod does nothing in normal worlds, so your real worlds and loot are never touched.)
 */
public class Practice implements ModInitializer {
    public static final List<String> DIFFICULTY = List.of("easy", "normal", "hard", "insane");
    public static final List<String> LOOT = List.of("none", "leather", "iron", "diamond", "netherite");
    static boolean active;
    static MinecraftServer server;

    static class BotFight { UUID player; Zombie bot; String kit, diff, loot, arena; int start; }
    static class Duel { String arena; final List<UUID> players = new ArrayList<>(); final Map<UUID, String> kits = new HashMap<>(); int countdown = -1; boolean fighting; int start; }
    static final Map<UUID, BotFight> bots = new HashMap<>();
    static final Map<String, Duel> duels = new HashMap<>();
    static final Map<UUID, StartPayload> last = new HashMap<>();
    static final Map<UUID, int[]> score = new HashMap<>(); // wins, losses

    static void say(ServerPlayer p, String text, ChatFormatting color) { p.displayClientMessage(Component.literal(text).withStyle(color), false); }

    static void title(ServerPlayer p, String text, ChatFormatting color) { p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(text).withStyle(color))); }

    static ServerLevel world() { return server.overworld(); }

    static void heal(ServerPlayer p) {
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(20);
        p.removeAllEffects();
        p.clearFire();
        p.fallDistance = 0;
    }

    static void toLobby(ServerPlayer p) {
        BlockPos l = Arenas.lobby();
        p.getInventory().clearContent();
        heal(p);
        p.teleportTo(world(), l.getX() + 0.5, l.getY() + 1, l.getZ() + 0.5, Set.of(), 0, 0, true);
        if (p.gameMode() != GameType.SURVIVAL) p.setGameMode(GameType.SURVIVAL);
    }

    static void tp(ServerPlayer p, BlockPos pos, float yaw) {
        p.teleportTo(world(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), yaw, 0, true);
    }

    // ---------------- Bot fights ----------------
    static void startBot(ServerPlayer p, String kit, String diff, String loot, String arena) {
        endBot(p.getUUID(), false);
        leaveDuel(p);
        last.put(p.getUUID(), new StartPayload("bot", kit, diff, loot, arena));
        ServerLevel level = world();
        Arenas.build(level, arena);
        heal(p);
        Kits.give(p, kit);
        tp(p, Arenas.spawn(arena, 0), -90);

        Zombie z = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (z == null) return;
        BlockPos s = Arenas.spawn(arena, 1);
        z.snapTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, 90, 0);
        z.setBaby(false);
        z.setCustomName(Component.literal("Olkku Bot (" + diff + ")").withStyle(ChatFormatting.RED));
        z.setCustomNameVisible(true);
        z.setPersistenceRequired();
        z.setCanPickUpLoot(false);
        z.addTag("olkku_bot");
        // Weapon from the kit, armor from the loot setting
        ItemStack[] k = Kits.defaults(kit);
        z.setItemSlot(EquipmentSlot.MAINHAND, k[0].copy());
        z.setItemSlot(EquipmentSlot.OFFHAND, k[40].is(Items.SHIELD) ? new ItemStack(Items.SHIELD) : ItemStack.EMPTY);
        if (!loot.equals("none")) {
            String[] parts = {"boots", "leggings", "chestplate", "helmet"};
            EquipmentSlot[] slots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
            for (int i = 0; i < 4; i++) {
                Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(loot + "_" + parts[i]));
                z.setItemSlot(slots[i], new ItemStack(it));
            }
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) z.setDropChance(slot, 0);
        int d = DIFFICULTY.indexOf(diff);
        set(z, Attributes.MAX_HEALTH, new double[]{20, 20, 30, 40}[d]);
        set(z, Attributes.MOVEMENT_SPEED, new double[]{0.23, 0.28, 0.33, 0.38}[d]);
        set(z, Attributes.ATTACK_DAMAGE, new double[]{1, 2, 3.5, 5}[d]);
        set(z, Attributes.FOLLOW_RANGE, 64);
        set(z, Attributes.KNOCKBACK_RESISTANCE, new double[]{0, 0.1, 0.3, 0.5}[d]);
        z.setHealth(z.getMaxHealth());
        level.addFreshEntity(z);
        z.setTarget(p);

        BotFight f = new BotFight();
        f.player = p.getUUID(); f.bot = z; f.kit = kit; f.diff = diff; f.loot = loot; f.arena = arena; f.start = server.getTickCount();
        bots.put(p.getUUID(), f);
        title(p, "FIGHT!", ChatFormatting.RED);
        say(p, "Bot fight: " + Kits.pretty(kit) + " kit, " + diff + " bot with " + loot + " armor, " + Arenas.pretty(arena) + " arena. Kill the bot!", ChatFormatting.GOLD);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    private static void set(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a, double v) {
        var inst = e.getAttribute(a);
        if (inst != null) inst.setBaseValue(v);
    }

    static void endBot(UUID id, boolean won) {
        BotFight f = bots.remove(id);
        if (f == null) return;
        if (f.bot != null && f.bot.isAlive()) f.bot.discard();
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p == null) return;
        int[] sc = score.computeIfAbsent(id, k -> new int[2]);
        sc[won ? 0 : 1]++;
        int secs = (server.getTickCount() - f.start) / 20;
        title(p, won ? "YOU WIN!" : "YOU LOST", won ? ChatFormatting.GREEN : ChatFormatting.RED);
        MutableComponent m = Component.literal((won ? "You beat the bot" : "The bot beat you") + " in " + secs + "s" + (won ? " with " + (int) Math.ceil(p.getHealth()) / 2.0 + " hearts left" : "") + ". Score: " + sc[0] + " wins, " + sc[1] + " losses. ")
            .withStyle(won ? ChatFormatting.GREEN : ChatFormatting.RED);
        m.append(Component.literal("[Rematch]").withStyle(st -> st.withColor(ChatFormatting.AQUA).withBold(true).withClickEvent(new ClickEvent.RunCommand("/practice rematch"))));
        p.displayClientMessage(m, false);
        toLobby(p);
    }

    static void tickBots() {
        int t = server.getTickCount();
        for (BotFight f : new ArrayList<>(bots.values())) {
            ServerPlayer p = server.getPlayerList().getPlayer(f.player);
            if (p == null) { if (f.bot != null) f.bot.discard(); bots.remove(f.player); continue; }
            Zombie z = f.bot;
            if (z == null || z.isRemoved()) continue;
            z.clearFire();
            if (z.getTarget() != p) z.setTarget(p);
            int d = DIFFICULTY.indexOf(f.diff);
            double dist = z.distanceTo(p);
            // Better bots strafe, jump and crit
            if (d >= 2 && dist < 5 && z.onGround() && t % (d == 3 ? 12 : 20) == 0) {
                var side = p.position().subtract(z.position()).normalize().cross(new net.minecraft.world.phys.Vec3(0, 1, 0)).scale(z.getRandom().nextBoolean() ? 0.35 : -0.35);
                z.setDeltaMovement(z.getDeltaMovement().add(side.x, 0.42, side.z));
                z.hurtMarked = true;
            }
            // Insane bots eat golden apples when low
            if (d == 3 && z.getHealth() < z.getMaxHealth() * 0.4 && t % 100 == 0) {
                z.heal(8);
                z.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0));
                z.level().playSound(null, z.getX(), z.getY(), z.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.HOSTILE, 1f, 1f);
            }
            // Bot fell out somehow: bring it back
            if (z.getY() < Arenas.Y - 10) { BlockPos s = Arenas.spawn(f.arena, 1); z.teleportTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5); }
        }
    }

    // ---------------- 1v1 duels ----------------
    static Duel duelOf(UUID id) {
        for (Duel d : duels.values()) if (d.players.contains(id)) return d;
        return null;
    }

    static void joinDuel(ServerPlayer p, String arena) {
        endBot(p.getUUID(), false);
        leaveDuel(p);
        Duel d = duels.computeIfAbsent(arena, a -> { Duel n = new Duel(); n.arena = a; return n; });
        if (d.players.size() >= 2 || d.fighting) { say(p, "That arena is busy, pick another one.", ChatFormatting.RED); return; }
        if (d.players.isEmpty()) Arenas.build(world(), arena);
        d.players.add(p.getUUID());
        heal(p);
        p.getInventory().clearContent();
        tp(p, Arenas.spawn(arena, d.players.size() - 1), d.players.size() == 1 ? -90 : 90);
        say(p, "You're in the " + Arenas.pretty(arena) + " arena. Pick your kit!", ChatFormatting.GOLD);
        if (d.players.size() < 2) {
            say(p, "Waiting for a second player... (they press K > 1v1 Duel > " + Arenas.pretty(arena) + ")", ChatFormatting.GRAY);
            for (ServerPlayer o : server.getPlayerList().getPlayers()) if (o != p) {
                MutableComponent m = Component.literal(p.getName().getString() + " wants to 1v1 you in the " + Arenas.pretty(arena) + " arena! ").withStyle(ChatFormatting.GOLD);
                m.append(Component.literal("[Join]").withStyle(st -> st.withColor(ChatFormatting.GREEN).withBold(true).withClickEvent(new ClickEvent.RunCommand("/practice duel " + arena))));
                o.displayClientMessage(m, false);
            }
        }
        kitPicker(p, kit -> pickDuelKit(p, kit));
    }

    static void pickDuelKit(ServerPlayer p, String kit) {
        Duel d = duelOf(p.getUUID());
        if (d == null || d.fighting) return;
        d.kits.put(p.getUUID(), kit);
        Kits.give(p, kit);
        say(p, "Kit: " + Kits.pretty(kit) + ".", ChatFormatting.GREEN);
        if (d.players.size() == 2 && d.kits.size() == 2) d.countdown = 60;
    }

    static void leaveDuel(ServerPlayer p) {
        Duel d = duelOf(p.getUUID());
        if (d == null) return;
        d.players.remove(p.getUUID());
        d.kits.remove(p.getUUID());
        for (UUID o : d.players) {
            ServerPlayer op = server.getPlayerList().getPlayer(o);
            if (op != null && d.fighting) { say(op, p.getName().getString() + " left. You win!", ChatFormatting.GREEN); toLobby(op); }
        }
        if (d.fighting || d.players.isEmpty()) duels.remove(d.arena);
    }

    static void endDuel(Duel d, ServerPlayer loser) {
        duels.remove(d.arena);
        for (UUID id : d.players) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            boolean won = p != loser;
            int[] sc = score.computeIfAbsent(id, k -> new int[2]);
            sc[won ? 0 : 1]++;
            title(p, won ? "YOU WIN!" : "YOU LOST", won ? ChatFormatting.GREEN : ChatFormatting.RED);
            MutableComponent m = Component.literal((won ? "You won the 1v1" : loser.getName().getString().equals(p.getName().getString()) ? "You lost the 1v1" : "") + ". Score: " + sc[0] + " wins, " + sc[1] + " losses. ").withStyle(won ? ChatFormatting.GREEN : ChatFormatting.RED);
            m.append(Component.literal("[Rematch]").withStyle(st -> st.withColor(ChatFormatting.AQUA).withBold(true).withClickEvent(new ClickEvent.RunCommand("/practice duel " + d.arena))));
            p.displayClientMessage(m, false);
            toLobby(p);
        }
    }

    static void tickDuels() {
        for (Duel d : new ArrayList<>(duels.values())) {
            if (d.countdown > 0) {
                if (d.countdown % 20 == 0) for (UUID id : d.players) {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    if (p != null) { title(p, String.valueOf(d.countdown / 20), ChatFormatting.YELLOW); p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1f, 1f); }
                }
                // Stay at your spawn during the countdown
                for (int i = 0; i < d.players.size(); i++) {
                    ServerPlayer p = server.getPlayerList().getPlayer(d.players.get(i));
                    BlockPos s = Arenas.spawn(d.arena, i);
                    if (p != null && p.distanceToSqr(s.getX() + 0.5, s.getY(), s.getZ() + 0.5) > 1) tp(p, s, i == 0 ? -90 : 90);
                }
                if (--d.countdown == 0) {
                    d.fighting = true;
                    d.start = server.getTickCount();
                    for (UUID id : d.players) { ServerPlayer p = server.getPlayerList().getPlayer(id); if (p != null) { heal(p); title(p, "FIGHT!", ChatFormatting.RED); } }
                }
            }
        }
    }

    // ---------------- Menus ----------------
    static Item kitIcon(String kit) {
        return switch (kit) {
            case "axe" -> Items.DIAMOND_AXE; case "vanilla" -> Items.END_CRYSTAL; case "mace" -> Items.MACE;
            case "mace-spear" -> Items.WIND_CHARGE; case "sword" -> Items.DIAMOND_SWORD; case "spear" -> Items.TRIDENT;
            case "uhc" -> Items.LAVA_BUCKET; case "smp" -> Items.NETHERITE_CHESTPLATE; default -> Items.STONE;
        };
    }

    static Item arenaIcon(String a) {
        return switch (a) {
            case "nether" -> Items.NETHERRACK; case "end" -> Items.END_STONE; case "desert" -> Items.SANDSTONE;
            case "ice" -> Items.PACKED_ICE; case "sky" -> Items.GRASS_BLOCK; default -> Items.STONE_BRICKS;
        };
    }

    static void kitPicker(ServerPlayer p, java.util.function.Consumer<String> then) {
        Map<Integer, Menus.Button> b = new HashMap<>();
        for (int i = 0; i < Kits.NAMES.size(); i++) {
            String k = Kits.NAMES.get(i);
            b.put(i, new Menus.Button(Menus.icon(kitIcon(k), Kits.pretty(k), ChatFormatting.GOLD), pl -> then.accept(k)));
        }
        Menus.open(p, "Pick a kit", b);
    }

    static void arenaPicker(ServerPlayer p, java.util.function.Consumer<String> then) {
        Map<Integer, Menus.Button> b = new HashMap<>();
        for (int i = 0; i < Arenas.NAMES.size(); i++) {
            String a = Arenas.NAMES.get(i);
            b.put(i, new Menus.Button(Menus.icon(arenaIcon(a), Arenas.pretty(a) + " arena", ChatFormatting.AQUA), pl -> then.accept(a)));
        }
        Menus.open(p, "Pick an arena", b);
    }

    static void listPicker(ServerPlayer p, String title, List<String> opts, Item[] icons, java.util.function.Consumer<String> then) {
        Map<Integer, Menus.Button> b = new HashMap<>();
        for (int i = 0; i < opts.size(); i++) {
            String o = opts.get(i);
            b.put(i, new Menus.Button(Menus.icon(icons[i], Character.toUpperCase(o.charAt(0)) + o.substring(1), ChatFormatting.YELLOW), pl -> then.accept(o)));
        }
        Menus.open(p, title, b);
    }

    static void botSetup(ServerPlayer p) {
        kitPicker(p, kit ->
            listPicker(p, "How good is the bot?", DIFFICULTY, new Item[]{Items.WOODEN_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD}, diff ->
                listPicker(p, "How good is the bot's loot?", LOOT, new Item[]{Items.BARRIER, Items.LEATHER_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE}, loot ->
                    arenaPicker(p, arena -> startBot(p, kit, diff, loot, arena)))));
    }

    static void mainMenu(ServerPlayer p) {
        if (!active) { say(p, "Olkku's Practice only works in the practice world (Bot Practice / Play Multiplayer in the main menu).", ChatFormatting.RED); return; }
        Map<Integer, Menus.Button> b = new HashMap<>();
        b.put(10, new Menus.Button(Menus.icon(Items.ZOMBIE_HEAD, "Bot Practice", ChatFormatting.RED, "Fight a bot with any kit."), Practice::botSetup));
        b.put(12, new Menus.Button(Menus.icon(Items.DIAMOND_SWORD, "1v1 Duel", ChatFormatting.GOLD, "Fight the other player in this world."), pl -> arenaPicker(pl, a -> joinDuel(pl, a))));
        b.put(14, new Menus.Button(Menus.icon(Items.ANVIL, "Kit Editor", ChatFormatting.AQUA, "Change how your kits look."), pl -> kitPicker(pl, k -> Menus.editKit(pl, k))));
        b.put(16, new Menus.Button(Menus.icon(Items.CLOCK, "Rematch", ChatFormatting.GREEN, "Same bot fight again."), Practice::rematch));
        b.put(22, new Menus.Button(Menus.icon(Items.RED_BED, "Back to lobby", ChatFormatting.GRAY, "Stop the fight."), pl -> { endBot(pl.getUUID(), false); leaveDuel(pl); toLobby(pl); }));
        Menus.open(p, "Olkku's Practice", b);
    }

    static void rematch(ServerPlayer p) {
        StartPayload l = last.get(p.getUUID());
        if (l == null) { say(p, "No fight to rematch yet.", ChatFormatting.GRAY); return; }
        startBot(p, l.kit(), l.difficulty(), l.loot(), l.arena());
    }

    static void start(ServerPlayer p, StartPayload s) {
        if (!active) return;
        String kit = Kits.NAMES.contains(s.kit()) ? s.kit() : "sword";
        String arena = Arenas.NAMES.contains(s.arena()) ? s.arena() : "classic";
        if (s.mode().equals("bot")) startBot(p, kit, DIFFICULTY.contains(s.difficulty()) ? s.difficulty() : "normal", LOOT.contains(s.loot()) ? s.loot() : "iron", arena);
        else joinDuel(p, arena);
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(StartPayload.TYPE, StartPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MenuPayload.TYPE, MenuPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(StartPayload.TYPE, (pay, ctx) -> start(ctx.player(), pay));
        ServerPlayNetworking.registerGlobalReceiver(MenuPayload.TYPE, (pay, ctx) -> mainMenu(ctx.player()));

        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            Kits.server = s;
            active = Files.exists(s.getWorldPath(LevelResource.ROOT).resolve("olkku_practice.txt"));
            if (!active) return;
            Kits.load();
            Arenas.buildLobby(s.overworld());
            var src = s.createCommandSourceStack().withSuppressedOutput();
            for (String c : new String[]{"gamerule doDaylightCycle false", "gamerule advance_time false", "gamerule doMobSpawning false", "gamerule spawn_mobs false",
                "gamerule doWeatherCycle false", "gamerule advance_weather false", "gamerule keepInventory true", "gamerule keep_inventory true",
                "gamerule doImmediateRespawn true", "gamerule immediate_respawn true", "time set noon", "weather clear", "difficulty normal"})
                try { s.getCommands().performPrefixedCommand(src, c); } catch (Exception ignored) { }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { for (BotFight f : bots.values()) if (f.bot != null) f.bot.discard(); bots.clear(); duels.clear(); active = false; });

        ServerPlayConnectionEvents.JOIN.register((h, sender, s) -> s.execute(() -> {
            if (!active) return;
            toLobby(h.player);
            say(h.player, "Welcome to Olkku's Practice! Press K to open the practice menu.", ChatFormatting.RED);
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> s.execute(() -> {
            BotFight f = bots.remove(h.player.getUUID());
            if (f != null && f.bot != null) f.bot.discard();
            leaveDuel(h.player);
        }));

        ServerTickEvents.END_SERVER_TICK.register(s -> { if (active) { tickBots(); tickDuels(); } });

        // Dying in a fight just ends the fight (totems still work)
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!active || !(entity instanceof ServerPlayer p)) return true;
            if (p.getItemInHand(InteractionHand.MAIN_HAND).is(Items.TOTEM_OF_UNDYING) || p.getItemInHand(InteractionHand.OFF_HAND).is(Items.TOTEM_OF_UNDYING)) return true;
            if (bots.containsKey(p.getUUID())) { p.setHealth(1); endBot(p.getUUID(), false); return false; }
            Duel d = duelOf(p.getUUID());
            if (d != null && d.fighting) { p.setHealth(1); endDuel(d, p); return false; }
            p.setHealth(1);
            toLobby(p);
            return false;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!active || !entity.getTags().contains("olkku_bot")) return;
            for (BotFight f : new ArrayList<>(bots.values())) if (f.bot == entity) endBot(f.player, true);
        });

        CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("practice")
            .executes(ctx -> { mainMenu(ctx.getSource().getPlayerOrException()); return 1; })
            .then(Commands.literal("rematch").executes(ctx -> { if (active) rematch(ctx.getSource().getPlayerOrException()); return 1; }))
            .then(Commands.literal("duel").then(Commands.argument("arena", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
                String a = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "arena");
                if (active && Arenas.NAMES.contains(a)) joinDuel(ctx.getSource().getPlayerOrException(), a);
                return 1;
            })))));
    }
}
