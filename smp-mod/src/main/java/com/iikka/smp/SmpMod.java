package com.iikka.smp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

public class SmpMod implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("donutsmp");
    public static Data DATA = new Data();
    private static MinecraftServer server;
    private static Path dataFile;

    private static final int COMBAT_TICKS = 15 * 20;
    private static final long SHARD_TICKS = 2L * 60 * 60 * 20; // 2 hours
    private static final int TPA_TIMEOUT_TICKS = 60 * 20;
    private static final int RTP_RANGE = 5000;
    private static final int MAX_HOMES = 5;
    private static final Random RANDOM = new Random();

    private static final Map<UUID, Long> combatUntil = new HashMap<>();
    private static final Map<UUID, TpaRequest> tpaRequests = new HashMap<>(); // key = player who gets asked
    private static long tick = 0;

    record TpaRequest(UUID from, boolean here, long expires) {}

    // ---------- money ----------

    public static Data.PlayerData data(ServerPlayer p) {
        Data.PlayerData d = DATA.get(p.getUUID());
        d.name = p.getName().getString();
        return d;
    }

    public static long money(ServerPlayer p) {
        return data(p).money;
    }

    public static void addMoney(ServerPlayer p, long amount) {
        Data.PlayerData d = data(p);
        d.money = Math.max(0, d.money + amount);
        updateSidebar();
    }

    // ---------- money on the right side of the screen ----------

    public static void updateSidebar() {
        if (server == null) return;
        // Remove the old scoreboard sidebar from earlier versions
        var board = server.getScoreboard();
        var old = board.getObjective("donut_money");
        if (old != null) board.removeObjective(old);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(p, MoneyPayload.TYPE)) {
                ServerPlayNetworking.send(p, new MoneyPayload(money(p), data(p).moneyHud, data(p).shards));
            }
        }
    }

    /** Opens one of the mod's own screens on the player's game. Returns false if their game doesn't have the mod. */
    public static boolean openScreen(ServerPlayer p, String screen, String data) {
        if (!ServerPlayNetworking.canSend(p, ScreenPayload.TYPE)) return false;
        ServerPlayNetworking.send(p, new ScreenPayload(screen, data));
        return true;
    }

    public static final List<String> HOME_ICONS = List.of("white_bed", "red_bed", "blue_bed", "lime_bed", "yellow_bed",
        "chest", "crafting_table", "furnace", "grass_block", "oak_sapling", "diamond", "emerald", "nether_star", "beacon");

    private static void openHomes(ServerPlayer p) {
        StringBuilder sb = new StringBuilder();
        data(p).homes.forEach((name, h) -> sb.append(name).append('|').append(h.icon == null ? "white_bed" : h.icon).append('\n'));
        openScreen(p, "homes", sb.toString());
    }

    // ---------- helpers ----------

    private static void say(ServerPlayer p, String text, ChatFormatting color) {
        p.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    private static boolean inCombat(ServerPlayer p) {
        Long until = combatUntil.get(p.getUUID());
        return until != null && until > tick;
    }

    /** Returns the player running the command, or null (and a message) if they can't use commands right now. */
    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (inCombat(p)) {
            long secs = (combatUntil.get(p.getUUID()) - tick + 19) / 20;
            say(p, "You're in combat! Wait " + secs + "s before using commands.", ChatFormatting.RED);
            return null;
        }
        return p;
    }

    private static void teleport(ServerPlayer p, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        p.teleportTo(level, x, y, z, Set.of(), yaw, pitch, true);
    }

    private static ServerLevel levelByName(String name) {
        for (ServerLevel l : server.getAllLevels()) {
            if (l.dimension().toString().equals(name)) return l;
        }
        return null;
    }

    public static MinecraftServer server() {
        return server;
    }

    public static void saveNow() {
        save();
    }

    private static void save() {
        if (dataFile != null) DATA.save(dataFile);
    }

    // ---------- setup ----------

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(MoneyPayload.TYPE, MoneyPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ScreenPayload.TYPE, ScreenPayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            dataFile = s.getWorldPath(LevelResource.ROOT).resolve("donutsmp_mod.json");
            DATA = Data.load(dataFile);
            updateSidebar();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            save();
            server = null;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> s.execute(SmpMod::updateSidebar));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> save());

        ServerTickEvents.END_SERVER_TICK.register(s -> {
            tick++;
            if (tick % (20 * 60 * 5) == 0) save();
            if (tick % 40 == 0) updateSidebar(); // keep the money box up to date
            // One shard for every 2 hours played
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                Data.PlayerData pd = data(p);
                if (++pd.playTicks >= SHARD_TICKS) {
                    pd.playTicks = 0;
                    pd.shards++;
                    say(p, "You got a shard for playing 2 hours! You have " + pd.shards + " shards.", ChatFormatting.LIGHT_PURPLE);
                }
            }
            if (tick % 20 == 0) {
                for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                    Long until = combatUntil.get(p.getUUID());
                    if (until == null) continue;
                    if (until <= tick) {
                        combatUntil.remove(p.getUUID());
                        p.sendSystemMessage(Component.literal("You are no longer in combat").withStyle(ChatFormatting.GREEN), true);
                    } else if (data(p).combatTimer) {
                        p.sendSystemMessage(Component.literal("⚔ Combat: " + (until - tick + 19) / 20 + "s").withStyle(ChatFormatting.RED), true);
                    }
                }
            }
        });

        // Hitting another player puts you both in combat for 15 seconds
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClientSide() && player instanceof ServerPlayer a && entity instanceof ServerPlayer b && a != b) {
                if (!inCombat(a)) say(a, "You are in combat! Don't log out for 15s.", ChatFormatting.RED);
                if (!inCombat(b)) say(b, "You are in combat! Don't log out for 15s.", ChatFormatting.RED);
                combatUntil.put(a.getUUID(), tick + COMBAT_TICKS);
                combatUntil.put(b.getUUID(), tick + COMBAT_TICKS);
            }
            return InteractionResult.PASS;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    // ---------- commands (everyone can use them, no cheats needed) ----------

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("ah")
            .executes(ctx -> { ServerPlayer p = player(ctx); if (p != null) ShopMenu.open(p, null, false, 0, 0); return 1; })
            .then(Commands.literal("sell").then(Commands.argument("price", StringArgumentType.word()).executes(ctx -> {
                ServerPlayer p = player(ctx);
                if (p == null) return 0;
                long price = Prices.parse(StringArgumentType.getString(ctx, "price"));
                if (price <= 0) { say(p, "Type a price like 500, 10k or 2m", ChatFormatting.RED); return 0; }
                Listings.list(p, price);
                return 1;
            })))
            .then(Commands.argument("search", StringArgumentType.greedyString()).executes(ctx -> {
                ServerPlayer p = player(ctx);
                if (p != null) ShopMenu.open(p, StringArgumentType.getString(ctx, "search"), false, 0, 0);
                return 1;
            })));
        d.register(Commands.literal("shop").executes(ctx -> { ServerPlayer p = player(ctx); if (p != null) ShopMenu.open(p, null, false, 0, 0); return 1; }));
        d.register(Commands.literal("quickbuy").executes(ctx -> { ServerPlayer p = player(ctx); if (p != null) ShopMenu.open(p, null, true, 0, 0); return 1; }));
        d.register(Commands.literal("menu").executes(ctx -> {
            ServerPlayer p = player(ctx);
            if (p != null && !openScreen(p, "menu", "")) SettingsMenu.open(p);
            return 1;
        }));
        d.register(Commands.literal("togglesettings").executes(ctx -> { ServerPlayer p = player(ctx); if (p != null) SettingsMenu.open(p); return 1; }));
        for (String soon : List.of("orders", "rtpqueue", "friends")) {
            d.register(Commands.literal(soon).executes(ctx -> {
                ServerPlayer p = player(ctx);
                if (p != null) say(p, "Coming soon!", ChatFormatting.YELLOW);
                return 1;
            }));
        }
        d.register(Commands.literal("shards").executes(ctx -> {
            ServerPlayer p = player(ctx);
            if (p == null) return 0;
            Data.PlayerData pd = data(p);
            long mins = (SHARD_TICKS - pd.playTicks) / 20 / 60;
            say(p, "You have " + pd.shards + " shards. Next shard in " + (mins / 60) + "h " + (mins % 60) + "m of playing.", ChatFormatting.LIGHT_PURPLE);
            return 1;
        }));
        d.register(Commands.literal("stats").executes(ctx -> {
            ServerPlayer p = player(ctx);
            if (p == null) return 0;
            Data.PlayerData pd = data(p);
            List<Data.PlayerData> list = new ArrayList<>(DATA.players.values());
            list.sort((x, y) -> Long.compare(y.money, x.money));
            say(p, "---- Your stats ----", ChatFormatting.GOLD);
            say(p, "Money: " + Prices.money(pd.money), ChatFormatting.YELLOW);
            say(p, "Money rank: #" + (list.indexOf(pd) + 1) + " of " + list.size(), ChatFormatting.YELLOW);
            say(p, "Shards: " + pd.shards, ChatFormatting.YELLOW);
            say(p, "Homes: " + pd.homes.size() + "/" + MAX_HOMES, ChatFormatting.YELLOW);
            return 1;
        }));
        d.register(Commands.literal("homeicon").then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
            ServerPlayer p = player(ctx);
            if (p == null) return 0;
            Data.Home h = data(p).homes.get(StringArgumentType.getString(ctx, "name"));
            if (h == null) { say(p, "No home with that name", ChatFormatting.RED); return 0; }
            int i = HOME_ICONS.indexOf(h.icon == null ? "white_bed" : h.icon);
            h.icon = HOME_ICONS.get((i + 1) % HOME_ICONS.size());
            save();
            openScreen(p, "home", StringArgumentType.getString(ctx, "name") + "|" + h.icon);
            return 1;
        })));
        d.register(Commands.literal("renamehome").then(Commands.argument("name", StringArgumentType.word())
            .then(Commands.argument("newname", StringArgumentType.word()).executes(ctx -> {
                ServerPlayer p = player(ctx);
                if (p == null) return 0;
                String from = StringArgumentType.getString(ctx, "name"), to = StringArgumentType.getString(ctx, "newname");
                Map<String, Data.Home> homes = data(p).homes;
                if (!homes.containsKey(from)) { say(p, "No home called " + from, ChatFormatting.RED); return 0; }
                if (homes.containsKey(to)) { say(p, "You already have a home called " + to, ChatFormatting.RED); return 0; }
                Map<String, Data.Home> renamed = new LinkedHashMap<>();
                homes.forEach((k, v) -> renamed.put(k.equals(from) ? to : k, v));
                homes.clear();
                homes.putAll(renamed);
                save();
                say(p, "Renamed " + from + " to " + to, ChatFormatting.GREEN);
                return 1;
            }))));
        d.register(Commands.literal("sell").executes(ctx -> { ServerPlayer p = player(ctx); if (p != null) SellMenu.open(p); return 1; }));
        d.register(Commands.literal("settings").executes(ctx -> {
            ServerPlayer p = player(ctx);
            if (p != null && !openScreen(p, "menu", "")) SettingsMenu.open(p);
            return 1;
        }));

        // /bal and /bal <player>
        LiteralArgumentBuilder<CommandSourceStack> bal = Commands.literal("bal")
            .executes(ctx -> {
                ServerPlayer p = player(ctx);
                if (p != null) say(p, "You have " + Prices.money(money(p)), ChatFormatting.GOLD);
                return 1;
            })
            .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                ServerPlayer p = player(ctx);
                ServerPlayer other = EntityArgument.getPlayer(ctx, "player");
                if (p != null) say(p, other.getName().getString() + " has " + Prices.money(money(other)), ChatFormatting.GOLD);
                return 1;
            }));
        d.register(bal);
        d.register(Commands.literal("balance").redirect(d.getRoot().getChild("bal")));
        d.register(Commands.literal("money").redirect(d.getRoot().getChild("bal")));

        // /baltop: richest players
        d.register(Commands.literal("baltop").executes(ctx -> {
            ServerPlayer p = player(ctx);
            if (p == null) return 0;
            for (ServerPlayer online : server.getPlayerList().getPlayers()) data(online);
            List<Data.PlayerData> list = new ArrayList<>(DATA.players.values());
            list.sort((x, y) -> Long.compare(y.money, x.money));
            say(p, "---- Richest players ----", ChatFormatting.GOLD);
            for (int i = 0; i < Math.min(10, list.size()); i++) {
                say(p, (i + 1) + ". " + list.get(i).name + "  " + Prices.money(list.get(i).money), ChatFormatting.YELLOW);
            }
            return 1;
        }));

        // /pay <player> <amount>
        d.register(Commands.literal("pay")
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", StringArgumentType.word()).executes(ctx -> {
                    ServerPlayer p = player(ctx);
                    if (p == null) return 0;
                    ServerPlayer to = EntityArgument.getPlayer(ctx, "player");
                    long amount = Prices.parse(StringArgumentType.getString(ctx, "amount"));
                    if (amount <= 0) { say(p, "Type an amount like 500, 10k or 2m", ChatFormatting.RED); return 0; }
                    if (to == p) { say(p, "You can't pay yourself!", ChatFormatting.RED); return 0; }
                    if (money(p) < amount) { say(p, "You only have " + Prices.money(money(p)), ChatFormatting.RED); return 0; }
                    addMoney(p, -amount);
                    addMoney(to, amount);
                    say(p, "You paid " + to.getName().getString() + " " + Prices.money(amount), ChatFormatting.GREEN);
                    say(to, p.getName().getString() + " paid you " + Prices.money(amount) + "!", ChatFormatting.GREEN);
                    return 1;
                }))));

        // /rtp: random spot in the overworld
        d.register(Commands.literal("rtp").executes(ctx -> {
            ServerPlayer p = player(ctx);
            if (p == null) return 0;
            ServerLevel level = server.overworld();
            for (int attempt = 0; attempt < 20; attempt++) {
                int x = RANDOM.nextInt(RTP_RANGE * 2) - RTP_RANGE;
                int z = RANDOM.nextInt(RTP_RANGE * 2) - RTP_RANGE;
                level.getChunk(x >> 4, z >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockState ground = level.getBlockState(new BlockPos(x, y - 1, z));
                if (y <= level.getMinY() + 1 || !ground.getFluidState().isEmpty() || ground.isAir()) continue;
                teleport(p, level, x + 0.5, y, z + 0.5, p.getYRot(), p.getXRot());
                say(p, "Teleported to a random place!", ChatFormatting.GREEN);
                return 1;
            }
            say(p, "Couldn't find a safe spot, try again!", ChatFormatting.RED);
            return 0;
        }));

        // /spawn
        d.register(Commands.literal("spawn").executes(ctx -> {
            ServerPlayer p = player(ctx);
            if (p == null) return 0;
            ServerLevel level = server.overworld();
            BlockPos spawn = level.getRespawnData().pos();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
            teleport(p, level, spawn.getX() + 0.5, y, spawn.getZ() + 0.5, p.getYRot(), p.getXRot());
            say(p, "Teleported to spawn", ChatFormatting.GREEN);
            return 1;
        }));

        // /tpa, /tpahere, /tpaccept, /tpdeny
        d.register(Commands.literal("tpa").then(Commands.argument("player", EntityArgument.player())
            .executes(ctx -> sendTpa(ctx, false))));
        d.register(Commands.literal("tpahere").then(Commands.argument("player", EntityArgument.player())
            .executes(ctx -> sendTpa(ctx, true))));
        d.register(Commands.literal("tpaccept").executes(ctx -> {
            ServerPlayer p = player(ctx);
            if (p == null) return 0;
            TpaRequest r = tpaRequests.remove(p.getUUID());
            ServerPlayer from = r == null ? null : server.getPlayerList().getPlayer(r.from());
            if (r == null || r.expires() < tick || from == null) { say(p, "You have no teleport requests", ChatFormatting.RED); return 0; }
            ServerPlayer mover = r.here() ? p : from;
            ServerPlayer target = r.here() ? from : p;
            teleport(mover, (ServerLevel) target.level(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
            say(mover, "Teleported to " + target.getName().getString(), ChatFormatting.GREEN);
            say(target, mover.getName().getString() + " teleported to you", ChatFormatting.GREEN);
            return 1;
        }));
        d.register(Commands.literal("tpdeny").executes(ctx -> {
            ServerPlayer p = player(ctx);
            if (p == null) return 0;
            TpaRequest r = tpaRequests.remove(p.getUUID());
            if (r == null) { say(p, "You have no teleport requests", ChatFormatting.RED); return 0; }
            ServerPlayer from = server.getPlayerList().getPlayer(r.from());
            if (from != null) say(from, p.getName().getString() + " said no to your teleport request", ChatFormatting.RED);
            say(p, "Request denied", ChatFormatting.YELLOW);
            return 1;
        }));

        // /sethome [name], /home [name], /delhome <name>, /homes
        d.register(Commands.literal("sethome")
            .executes(ctx -> setHome(ctx, "home"))
            .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> setHome(ctx, StringArgumentType.getString(ctx, "name")))));
        d.register(Commands.literal("home")
            .executes(ctx -> home(ctx, null))
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, b) -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p != null) data(p).homes.keySet().forEach(b::suggest);
                    return b.buildFuture();
                })
                .executes(ctx -> home(ctx, StringArgumentType.getString(ctx, "name")))));
        d.register(Commands.literal("delhome").then(Commands.argument("name", StringArgumentType.word())
            .suggests((ctx, b) -> {
                ServerPlayer p = ctx.getSource().getPlayer();
                if (p != null) data(p).homes.keySet().forEach(b::suggest);
                return b.buildFuture();
            })
            .executes(ctx -> {
                ServerPlayer p = player(ctx);
                if (p == null) return 0;
                String name = StringArgumentType.getString(ctx, "name");
                if (data(p).homes.remove(name) == null) { say(p, "You don't have a home called " + name, ChatFormatting.RED); return 0; }
                say(p, "Deleted home " + name, ChatFormatting.YELLOW);
                save();
                if (!data(p).homes.isEmpty()) openHomes(p);
                return 1;
            })));
        d.register(Commands.literal("homes").executes(ctx -> {
            ServerPlayer p = player(ctx);
            if (p == null) return 0;
            Set<String> names = data(p).homes.keySet();
            say(p, names.isEmpty() ? "You have no homes. Use /sethome <name>" : "Your homes: " + String.join(", ", names), ChatFormatting.GOLD);
            return 1;
        }));
    }

    private static int sendTpa(CommandContext<CommandSourceStack> ctx, boolean here) throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        ServerPlayer to = EntityArgument.getPlayer(ctx, "player");
        if (to == p) { say(p, "You can't teleport to yourself!", ChatFormatting.RED); return 0; }
        if (!data(to).tpaRequests) { say(p, to.getName().getString() + " has teleport requests turned off", ChatFormatting.RED); return 0; }
        tpaRequests.put(to.getUUID(), new TpaRequest(p.getUUID(), here, tick + TPA_TIMEOUT_TICKS));
        say(p, "Request sent to " + to.getName().getString(), ChatFormatting.GREEN);
        say(to, p.getName().getString() + (here ? " wants you to teleport to them." : " wants to teleport to you."), ChatFormatting.YELLOW);
        say(to, "Type /tpaccept to say yes or /tpdeny to say no (60s)", ChatFormatting.YELLOW);
        return 1;
    }

    private static int setHome(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        Map<String, Data.Home> homes = data(p).homes;
        if (!homes.containsKey(name) && homes.size() >= MAX_HOMES) {
            say(p, "You can only have " + MAX_HOMES + " homes. Delete one with /delhome", ChatFormatting.RED);
            return 0;
        }
        Data.Home h = new Data.Home();
        h.dimension = p.level().dimension().toString();
        h.x = p.getX(); h.y = p.getY(); h.z = p.getZ();
        h.yaw = p.getYRot(); h.pitch = p.getXRot();
        homes.put(name, h);
        save();
        say(p, "Home " + name + " set! Go back with /home " + name, ChatFormatting.GREEN);
        return 1;
    }

    private static int home(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        Map<String, Data.Home> homes = data(p).homes;
        if (name == null) {
            if (homes.isEmpty()) return setHome(ctx, "home"); // first /home sets your home right here
            if (ServerPlayNetworking.canSend(p, ScreenPayload.TYPE)) { openHomes(p); return 1; }
            name = homes.containsKey("home") ? "home" : homes.keySet().iterator().next();
        }
        Data.Home h = homes.get(name);
        if (h == null) { say(p, "You don't have a home called " + name + ". Your homes: " + String.join(", ", homes.keySet()), ChatFormatting.RED); return 0; }
        ServerLevel level = levelByName(h.dimension);
        if (level == null) level = server.overworld();
        teleport(p, level, h.x, h.y, h.z, h.yaw, h.pitch);
        say(p, "Teleported to home " + name, ChatFormatting.GREEN);
        return 1;
    }
}
