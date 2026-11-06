package com.iikka.morph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Morphing: you become invisible and a copy of the mob follows you everywhere,
 * so everyone (and you in third person) sees the mob. Works for friends without the mod too.
 */
public class MorphMod implements ModInitializer {
    private static final String TAG = "morph_disguise";
    private static final Set<String> BANNED = Set.of("minecraft:ender_dragon", "minecraft:giant");
    private static MinecraftServer server;

    public static class SaveData {
        public Map<String, List<String>> unlocked = new HashMap<>(); // player uuid -> mob ids
        public Map<String, String> current = new HashMap<>();        // player uuid -> mob id
    }
    private static SaveData DATA = new SaveData();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Entity> disguises = new HashMap<>();
    private static final Map<UUID, Long> cooldown = new HashMap<>();
    private static final Identifier HEALTH = Identifier.fromNamespaceAndPath("morph", "health");
    private static final Identifier SIZE = Identifier.fromNamespaceAndPath("morph", "size");

    /** You become the mob's size, so a silverfish fits under a bed. */
    private static void setSize(ServerPlayer p, Entity disguise) {
        var attr = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        if (attr == null) return;
        if (disguise == null) { attr.removeModifier(SIZE); return; }
        double scale = Math.max(0.0625, Math.min(16, disguise.getBbHeight() / 1.8));
        attr.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SIZE, scale - 1,
            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
    }

    /** Your max health becomes the mob's max health. */
    private static void setHealth(ServerPlayer p, Entity disguise) {
        var attr = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (attr == null) return;
        if (disguise instanceof LivingEntity le) {
            double want = Math.max(2, le.getMaxHealth());
            attr.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(HEALTH, want - 20,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        } else attr.removeModifier(HEALTH);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    private static Path file() { return server.getWorldPath(LevelResource.ROOT).resolve("morphs.json"); }
    private static void save() { try { Files.writeString(file(), GSON.toJson(DATA)); } catch (Exception ignored) { } }

    public static List<String> unlocked(ServerPlayer p) {
        return DATA.unlocked.getOrDefault(p.getUUID().toString(), List.of());
    }

    public static String current(ServerPlayer p) {
        return DATA.current.get(p.getUUID().toString());
    }

    public static void morph(ServerPlayer p, String id) {
        removeDisguise(p);
        DATA.current.put(p.getUUID().toString(), id);
        save();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));
        p.sendSystemMessage(Component.literal("You turned into a ").append(type.getDescription()).append("!").withStyle(ChatFormatting.GREEN));
    }

    public static void unmorph(ServerPlayer p, boolean message) {
        removeDisguise(p);
        var attr = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (attr != null) attr.removeModifier(HEALTH);
        setSize(p, null);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        if (DATA.current.remove(p.getUUID().toString()) != null) save();
        p.removeEffect(MobEffects.INVISIBILITY);
        if (message) p.sendSystemMessage(Component.literal("You're normal again.").withStyle(ChatFormatting.GRAY));
    }

    private static void removeDisguise(ServerPlayer p) {
        Entity e = disguises.remove(p.getUUID());
        if (e != null) e.discard();
    }

    private static PlayerTeam team() {
        var board = server.getScoreboard();
        PlayerTeam t = board.getPlayerTeam("morph");
        if (t == null) {
            t = board.addPlayerTeam("morph");
            t.setCollisionRule(Team.CollisionRule.PUSH_OTHER_TEAMS); // you don't bump into your own disguise
            t.setSeeFriendlyInvisibles(false); // so you don't see a see-through you inside the mob
        }
        return t;
    }

    private static Entity spawnDisguise(ServerPlayer p, String id) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));
        Entity e = type.create(p.level(), EntitySpawnReason.COMMAND);
        if (e == null) return null;
        e.addTag(TAG);
        e.setPos(p.getX(), p.getY(), p.getZ());
        e.setInvulnerable(true);
        e.setSilent(true);
        e.setNoGravity(true);
        e.setCustomName(p.getName());
        e.setCustomNameVisible(true);
        if (e instanceof Mob m) { m.setNoAi(true); m.setPersistenceRequired(); }
        p.level().addFreshEntity(e);
        PlayerTeam t = team();
        server.getScoreboard().addPlayerToTeam(e.getScoreboardName(), t);
        server.getScoreboard().addPlayerToTeam(p.getScoreboardName(), t);
        return e;
    }

    private static void tick(MinecraftServer s) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            String id = current(p);
            if (id == null) continue;
            Entity e = disguises.get(p.getUUID());
            if (e == null || e.isRemoved() || e.level() != p.level()) {
                if (e != null) e.discard();
                e = spawnDisguise(p, id);
                if (e == null) continue;
                disguises.put(p.getUUID(), e);
                boolean full = p.getHealth() >= p.getMaxHealth();
                setHealth(p, e);
                setSize(p, e);
                if (full) p.setHealth(p.getMaxHealth());
            }
            // Follow the player
            e.setPos(p.getX(), p.getY(), p.getZ());
            e.setYRot(p.getYRot());
            e.setXRot(p.getXRot());
            if (e instanceof LivingEntity le) { le.setYHeadRot(p.getYHeadRot()); le.setYBodyRot(p.yBodyRot); }
            e.setDeltaMovement(p.getDeltaMovement());
            e.setShiftKeyDown(p.isShiftKeyDown());
            if (Powers.noFallDamage(id)) p.fallDistance = 0;
            // As a hostile mob, other hostile mobs leave you alone (unless you hit them first)
            if (e.getType().getCategory() == MobCategory.MONSTER) {
                for (Entity near : p.level().getEntities(p, p.getBoundingBox().inflate(40))) {
                    if (near instanceof Mob m && m.getTarget() == p && m.getType().getCategory() == MobCategory.MONSTER && m.getLastHurtByMob() != p) m.setTarget(null);
                }
            }
            if (s.getTickCount() % 20 == 0) {
                setHealth(p, e);
                setSize(p, e);
                Powers.traits(p, id);
                p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false, false));
                // Hide your armor and held items from others, so only the mob is seen
                List<Pair<EquipmentSlot, ItemStack>> empty = new ArrayList<>();
                for (EquipmentSlot slot : EquipmentSlot.values()) empty.add(Pair.of(slot, ItemStack.EMPTY));
                var packet = new ClientboundSetEquipmentPacket(p.getId(), empty);
                for (ServerPlayer other : s.getPlayerList().getPlayers()) if (other != p && other.level() == p.level()) other.connection.send(packet);
            }
        }
    }

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            try { if (Files.exists(file())) DATA = GSON.fromJson(Files.readString(file()), SaveData.class); } catch (Exception ignored) { }
            if (DATA == null) DATA = new SaveData();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { for (Entity e : disguises.values()) e.discard(); disguises.clear(); save(); });

        // Old disguises left in the world (after a crash) are removed
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.getTags().contains(TAG) && !disguises.containsValue(entity)) entity.discard();
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> removeDisguise(handler.player));

        // Killing a mob unlocks it
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Player || entity.getTags().contains(TAG)) return;
            if (!(source.getEntity() instanceof ServerPlayer p)) return;
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            if (BANNED.contains(id)) return;
            List<String> list = DATA.unlocked.computeIfAbsent(p.getUUID().toString(), k -> new ArrayList<>());
            if (list.contains(id)) return;
            list.add(id);
            save();
            p.sendSystemMessage(Component.literal("Unlocked morph: ").append(entity.getType().getDescription())
                .append("! Press Right Shift (or /morph) to turn into it.").withStyle(ChatFormatting.LIGHT_PURPLE));
        });

        ServerTickEvents.END_SERVER_TICK.register(MorphMod::tick);

        // Iron golem punches: much stronger, and throw them a little up in the air
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (!(player instanceof ServerPlayer p) || !(target instanceof LivingEntity le) || !"minecraft:iron_golem".equals(current(p))) return net.minecraft.world.InteractionResult.PASS;
            if (p.getAttackStrengthScale(0.5f) < 0.9f) return net.minecraft.world.InteractionResult.PASS; // normal hit while charging
            le.hurtServer((ServerLevel) world, p.damageSources().playerAttack(p), 15);
            Vec3Holder.launch(le, p);
            p.resetAttackStrengthTicker();
            world.playSound(null, le.getX(), le.getY(), le.getZ(), net.minecraft.sounds.SoundEvents.IRON_GOLEM_ATTACK, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
            return net.minecraft.world.InteractionResult.SUCCESS;
        });

        PayloadTypeRegistry.playC2S().register(OpenPayload.TYPE, OpenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PowerPayload.TYPE, PowerPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PowerPayload.TYPE, (payload, ctx) -> {
            ServerPlayer p = ctx.player();
            String id = current(p);
            if (id == null) { p.sendSystemMessage(Component.literal("Morph into a mob first (Right Shift)!").withStyle(ChatFormatting.RED)); return; }
            long now = server.getTickCount();
            Long ready = cooldown.get(p.getUUID());
            if (ready != null && now < ready) return;
            cooldown.put(p.getUUID(), now + Powers.use(p, id));
        });
        ServerPlayNetworking.registerGlobalReceiver(OpenPayload.TYPE, (payload, ctx) -> MorphMenu.open(ctx.player()));

        CommandRegistrationCallback.EVENT.register((d, reg, env) -> {
            d.register(Commands.literal("morph").executes(ctx -> { MorphMenu.open(ctx.getSource().getPlayerOrException()); return 1; }));
            d.register(Commands.literal("unmorph").executes(ctx -> { unmorph(ctx.getSource().getPlayerOrException(), true); return 1; }));
        });
    }

    /** Small helper so the golem launch is in one place. */
    static class Vec3Holder {
        static void launch(LivingEntity le, ServerPlayer p) {
            var away = le.position().subtract(p.position()).normalize().scale(0.4);
            le.setDeltaMovement(away.x, 0.75, away.z); // a little up, not far
            le.hurtMarked = true;
        }
    }
}
