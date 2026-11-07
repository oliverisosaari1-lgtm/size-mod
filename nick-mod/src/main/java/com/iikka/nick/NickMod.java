package com.iikka.nick;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.iikka.nick.mixin.ChunkMapAccess;
import com.iikka.nick.mixin.TrackedEntityAccess;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class NickMod implements ModInitializer {
    /** What one player has changed. Saved per world. */
    public static class Disguise {
        public String nick;
        public String skinOf;
        public String skinValue;
        public String skinSignature;
        boolean empty() { return nick == null && skinValue == null; }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static Map<UUID, Disguise> data = new HashMap<>();
    private static MinecraftServer server;

    public static Disguise get(UUID id) { return data.get(id); }

    /** The name and skin other games should see for this player. */
    public static GameProfile profileFor(ServerPlayer p) {
        GameProfile real = p.getGameProfile();
        Disguise d = data.get(p.getUUID());
        if (d == null || d.empty()) return real;
        String name = d.nick != null ? d.nick : real.name();
        PropertyMap props = real.properties();
        if (d.skinValue != null)
            props = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", d.skinValue, d.skinSignature)));
        return new GameProfile(real.id(), name, props);
    }

    /** Swap in the disguised profile for every player in a tab-list packet. */
    public static List<ClientboundPlayerInfoUpdatePacket.Entry> disguise(List<ClientboundPlayerInfoUpdatePacket.Entry> entries) {
        if (server == null || data.isEmpty()) return entries;
        List<ClientboundPlayerInfoUpdatePacket.Entry> out = new java.util.ArrayList<>(entries.size());
        for (var e : entries) {
            ServerPlayer p = data.containsKey(e.profileId()) ? server.getPlayerList().getPlayer(e.profileId()) : null;
            if (p == null || e.profile() == null) { out.add(e); continue; }
            out.add(new ClientboundPlayerInfoUpdatePacket.Entry(e.profileId(), profileFor(p), e.listed(), e.latency(),
                e.gameMode(), e.displayName(), e.showHat(), e.listOrder(), e.chatSession()));
        }
        return out;
    }

    // ---------- saving ----------
    private static Path file() { return server.getWorldPath(LevelResource.ROOT).resolve("nick_skin.json"); }

    private static void load() {
        data = new HashMap<>();
        try {
            Path f = file();
            if (Files.exists(f)) {
                Map<UUID, Disguise> m = GSON.fromJson(Files.readString(f), new TypeToken<Map<UUID, Disguise>>() { }.getType());
                if (m != null) data.putAll(m);
            }
        } catch (Exception ignored) { }
    }

    private static void save() {
        try { Files.writeString(file(), GSON.toJson(data)); } catch (Exception ignored) { }
    }

    private static Disguise of(ServerPlayer p) { return data.computeIfAbsent(p.getUUID(), k -> new Disguise()); }

    // ---------- show the change to everyone right away ----------
    private static void refresh(ServerPlayer p) {
        Object tracked = null;
        try {
            tracked = ((ChunkMapAccess) ((ServerLevel) p.level()).getChunkSource().chunkMap).nick$entityMap().get(p.getId());
        } catch (Throwable ignored) { }
        List<ServerPlayer> others = server.getPlayerList().getPlayers().stream().filter(o -> o != p).toList();
        if (tracked != null) for (ServerPlayer o : others) try { ((TrackedEntityAccess) tracked).nick$removePlayer(o); } catch (Throwable ignored) { }
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(p.getUUID())));
        server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(p)));
        if (tracked != null) for (ServerPlayer o : others) try { ((TrackedEntityAccess) tracked).nick$updatePlayer(o); } catch (Throwable ignored) { }
        if (ServerPlayNetworking.canSend(p, RefreshPayload.TYPE)) ServerPlayNetworking.send(p, new RefreshPayload());
    }

    private static void say(ServerPlayer p, String text, ChatFormatting color) {
        p.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    // ---------- /nick ----------
    private static int setNick(ServerPlayer p, String nick) {
        if (!nick.matches("[A-Za-z0-9_]{1,16}")) {
            say(p, "A nick can have 1-16 letters, numbers or _ (no spaces).", ChatFormatting.RED);
            return 0;
        }
        of(p).nick = nick;
        save();
        refresh(p);
        say(p, "Your name is now " + nick + ". Everyone sees it over your head, in chat and in the tab list. /nick reset brings your real name back.", ChatFormatting.GREEN);
        return 1;
    }

    private static int resetNick(ServerPlayer p) {
        Disguise d = data.get(p.getUUID());
        if (d != null) d.nick = null;
        cleanup(p);
        refresh(p);
        say(p, "Your real name is back.", ChatFormatting.GREEN);
        return 1;
    }

    // ---------- /skin ----------
    private static int setSkin(ServerPlayer p, String who) {
        if (!who.matches("[A-Za-z0-9_]{1,16}")) {
            say(p, "That is not a Minecraft username.", ChatFormatting.RED);
            return 0;
        }
        say(p, "Getting " + who + "'s skin...", ChatFormatting.GRAY);
        UUID id = p.getUUID();
        CompletableFuture.supplyAsync(() -> fetchSkin(who)).thenAccept(skin -> server.execute(() -> {
            ServerPlayer now = server.getPlayerList().getPlayer(id);
            if (now == null) return;
            if (skin == null) {
                say(now, "Couldn't find a Minecraft player called " + who + " (or no internet).", ChatFormatting.RED);
                return;
            }
            Disguise d = of(now);
            d.skinOf = skin[0];
            d.skinValue = skin[1];
            d.skinSignature = skin[2];
            save();
            refresh(now);
            say(now, "You now look like " + skin[0] + ". Press F5 to see yourself. /skin reset brings your own skin back.", ChatFormatting.GREEN);
        }));
        return 1;
    }

    private static int resetSkin(ServerPlayer p) {
        Disguise d = data.get(p.getUUID());
        if (d != null) { d.skinOf = null; d.skinValue = null; d.skinSignature = null; }
        cleanup(p);
        refresh(p);
        say(p, "Your own skin is back.", ChatFormatting.GREEN);
        return 1;
    }

    private static void cleanup(ServerPlayer p) {
        Disguise d = data.get(p.getUUID());
        if (d != null && d.empty()) data.remove(p.getUUID());
        save();
    }

    /** Returns {real name, textures value, signature} from Mojang, or null. */
    private static String[] fetchSkin(String name) {
        try {
            var r1 = HTTP.send(HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r1.statusCode() != 200) return null;
            JsonObject who = JsonParser.parseString(r1.body()).getAsJsonObject();
            String uuid = who.get("id").getAsString();
            String realName = who.get("name").getAsString();
            var r2 = HTTP.send(HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false"))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r2.statusCode() != 200) return null;
            JsonArray props = JsonParser.parseString(r2.body()).getAsJsonObject().getAsJsonArray("properties");
            for (var e : props) {
                JsonObject o = e.getAsJsonObject();
                if ("textures".equals(o.get("name").getAsString()))
                    return new String[] { realName, o.get("value").getAsString(), o.has("signature") ? o.get("signature").getAsString() : null };
            }
        } catch (Exception ignored) { }
        return null;
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(RefreshPayload.TYPE, RefreshPayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> { server = s; load(); });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { data = new HashMap<>(); server = null; });

        CommandRegistrationCallback.EVENT.register((d, reg, env) -> {
            d.register(Commands.literal("nick")
                .executes(ctx -> {
                    say(ctx.getSource().getPlayerOrException(), "/nick <name> changes the name everyone sees (over your head, chat, tab list). /nick reset gives your real name back.", ChatFormatting.YELLOW);
                    return 1;
                })
                .then(Commands.literal("reset").executes(ctx -> resetNick(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> setNick(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "name")))));
            d.register(Commands.literal("skin")
                .executes(ctx -> {
                    say(ctx.getSource().getPlayerOrException(), "/skin <player> makes you look like any Minecraft player (for example /skin Dream). /skin reset gives your own skin back.", ChatFormatting.YELLOW);
                    return 1;
                })
                .then(Commands.literal("reset").executes(ctx -> resetSkin(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), b))
                    .executes(ctx -> setSkin(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "player")))));
        });
    }
}
