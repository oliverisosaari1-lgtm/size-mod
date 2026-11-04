package com.iikka.smp;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.*;

/** /duel: fight a friend in the arena with the mace kit, winner takes the bet. Your own stuff comes back after. */
public class Duels {
    record Request(UUID from, long bet, long time) {}
    record Duel(UUID a, UUID b, long bet) {}

    private static final Map<UUID, Request> requests = new HashMap<>(); // target -> request
    private static final List<Duel> duels = new ArrayList<>();

    public static boolean inDuel(ServerPlayer p) {
        return find(p.getUUID()) != null;
    }

    private static Duel find(UUID id) {
        for (Duel d : duels) if (d.a.equals(id) || d.b.equals(id)) return d;
        return null;
    }

    private static ServerPlayer online(UUID id) {
        return SmpMod.server().getPlayerList().getPlayer(id);
    }

    // ---------- commands ----------

    public static void challenge(ServerPlayer me, ServerPlayer target, long bet) {
        if (target == me) { SmpMod.say(me, "You can't duel yourself!", ChatFormatting.RED); return; }
        if (inDuel(me) || inDuel(target)) { SmpMod.say(me, "One of you is already in a duel!", ChatFormatting.RED); return; }
        if (bet < 0) bet = 0;
        if (SmpMod.money(me) < bet) { SmpMod.say(me, "You only have " + Prices.money(SmpMod.money(me)), ChatFormatting.RED); return; }
        requests.put(target.getUUID(), new Request(me.getUUID(), bet, System.currentTimeMillis()));
        String b = bet > 0 ? " for " + Prices.money(bet) : "";
        SmpMod.say(me, "Duel request sent to " + target.getName().getString() + b, ChatFormatting.GREEN);
        SmpMod.say(target, me.getName().getString() + " wants to duel you" + b + "! Mace kit, winner takes the bet.", ChatFormatting.GOLD);
        SmpMod.say(target, "Type /duel accept or /duel deny", ChatFormatting.YELLOW);
    }

    public static void deny(ServerPlayer me) {
        Request r = requests.remove(me.getUUID());
        if (r == null) { SmpMod.say(me, "You have no duel requests.", ChatFormatting.RED); return; }
        ServerPlayer from = online(r.from);
        if (from != null) SmpMod.say(from, me.getName().getString() + " said no to the duel.", ChatFormatting.RED);
        SmpMod.say(me, "Duel denied.", ChatFormatting.YELLOW);
    }

    public static void accept(ServerPlayer me) {
        Request r = requests.remove(me.getUUID());
        if (r == null || System.currentTimeMillis() - r.time > 60_000) { SmpMod.say(me, "You have no duel requests.", ChatFormatting.RED); return; }
        ServerPlayer other = online(r.from);
        if (other == null) { SmpMod.say(me, "That player left.", ChatFormatting.RED); return; }
        if (inDuel(me) || inDuel(other)) { SmpMod.say(me, "One of you is already in a duel!", ChatFormatting.RED); return; }
        if (SmpMod.money(me) < r.bet) { SmpMod.say(me, "You need " + Prices.money(r.bet) + " for this duel.", ChatFormatting.RED); return; }
        if (SmpMod.money(other) < r.bet) { SmpMod.say(me, other.getName().getString() + " doesn't have the money anymore.", ChatFormatting.RED); return; }
        SmpMod.addMoney(me, -r.bet);
        SmpMod.addMoney(other, -r.bet);
        duels.add(new Duel(other.getUUID(), me.getUUID(), r.bet));

        ServerLevel level = SmpMod.server().overworld();
        if (!SmpMod.DATA.arenaBuilt) { Arena.build(level); SmpMod.DATA.arenaBuilt = true; }
        start(other, level, Arena.X + 0.5, Arena.Z - 14.5, 0f);
        start(me, level, Arena.X + 0.5, Arena.Z + 15.5, 180f);
        String b = r.bet > 0 ? " Winner gets " + Prices.money(r.bet * 2) + "!" : "";
        SmpMod.say(other, "Duel started against " + me.getName().getString() + "!" + b, ChatFormatting.GOLD);
        SmpMod.say(me, "Duel started against " + other.getName().getString() + "!" + b, ChatFormatting.GOLD);
    }

    // ---------- start / end ----------

    private static void start(ServerPlayer p, ServerLevel level, double x, double z, float yaw) {
        Data.PlayerData d = SmpMod.data(p);
        // Save their own things so they get them back after
        d.duelItems = new ArrayList<>();
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) d.duelItems.add(Listings.save(SmpMod.server(), inv.getItem(i)));
        Data.Home back = new Data.Home();
        back.dimension = p.level().dimension().toString();
        back.x = p.getX(); back.y = p.getY(); back.z = p.getZ(); back.yaw = p.getYRot(); back.pitch = p.getXRot();
        d.duelReturn = back;
        SmpMod.saveNow();

        inv.clearContent();
        giveKit(p);
        heal(p);
        SmpMod.teleport(p, level, x, Arena.Y + 1, z, yaw, 0f);
    }

    private static void heal(ServerPlayer p) {
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.setRemainingFireTicks(0);
        p.fallDistance = 0;
    }

    /** Puts their own items back and sends them back where they were. Also used when they join after leaving mid-duel. */
    public static void restore(ServerPlayer p) {
        Data.PlayerData d = SmpMod.data(p);
        if (d.duelItems == null) return;
        var inv = p.getInventory();
        inv.clearContent();
        for (int i = 0; i < d.duelItems.size() && i < inv.getContainerSize(); i++) inv.setItem(i, Listings.loadItem(SmpMod.server(), d.duelItems.get(i)));
        d.duelItems = null;
        heal(p);
        Data.Home h = d.duelReturn;
        d.duelReturn = null;
        if (h != null) {
            ServerLevel level = SmpMod.levelByName(h.dimension);
            if (level != null) SmpMod.teleport(p, level, h.x, h.y, h.z, h.yaw, h.pitch);
        }
        SmpMod.saveNow();
    }

    /** loser lost (died, left or fell out). */
    public static void lose(UUID loserId) {
        Duel d = find(loserId);
        if (d == null) return;
        duels.remove(d);
        UUID winnerId = d.a.equals(loserId) ? d.b : d.a;
        ServerPlayer winner = online(winnerId), loser = online(loserId);
        String wName = winner != null ? winner.getName().getString() : SmpMod.DATA.players.getOrDefault(winnerId.toString(), new Data.PlayerData()).name;
        String lName = loser != null ? loser.getName().getString() : SmpMod.DATA.players.getOrDefault(loserId.toString(), new Data.PlayerData()).name;
        if (d.bet > 0) {
            if (winner != null) SmpMod.addMoney(winner, d.bet * 2);
            else SmpMod.DATA.players.get(winnerId.toString()).money += d.bet * 2;
        }
        String b = d.bet > 0 ? " and won " + Prices.money(d.bet * 2) : "";
        SmpMod.server().getPlayerList().broadcastSystemMessage(
            Component.literal(wName + " beat " + lName + " in a duel" + b + "!").withStyle(ChatFormatting.GOLD), false);
        if (winner != null) restore(winner);
        if (loser != null) restore(loser);
    }

    // ---------- the mace kit ----------

    private static Holder<Enchantment> ench(ResourceKey<Enchantment> key) {
        return SmpMod.server().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static ItemStack item(Item item, int count, Object... enchants) {
        ItemStack s = new ItemStack(item, count);
        for (int i = 0; i + 1 < enchants.length; i += 2) {
            try {
                @SuppressWarnings("unchecked") ResourceKey<Enchantment> k = (ResourceKey<Enchantment>) enchants[i];
                s.enchant(ench(k), (int) enchants[i + 1]);
            } catch (Exception ignored) { }
        }
        return s;
    }

    private static ItemStack armor(Item item) {
        if (item == Items.NETHERITE_BOOTS)
            return item(item, 1, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1, Enchantments.FEATHER_FALLING, 4);
        return item(item, 1, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1);
    }

    private static ItemStack potion(Item type, Holder<net.minecraft.world.item.alchemy.Potion> potion, int count) {
        ItemStack s = PotionContents.createItemStack(type, potion);
        s.setCount(count);
        return s;
    }

    private static void giveKit(ServerPlayer p) {
        var inv = p.getInventory();
        p.setItemSlot(EquipmentSlot.HEAD, armor(Items.NETHERITE_HELMET));
        p.setItemSlot(EquipmentSlot.CHEST, armor(Items.NETHERITE_CHESTPLATE));
        p.setItemSlot(EquipmentSlot.LEGS, armor(Items.NETHERITE_LEGGINGS));
        p.setItemSlot(EquipmentSlot.FEET, armor(Items.NETHERITE_BOOTS));
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));

        // Hotbar
        inv.setItem(0, item(Items.NETHERITE_SWORD, 1, Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1, Enchantments.FIRE_ASPECT, 2));
        inv.setItem(1, item(Items.MACE, 1, Enchantments.DENSITY, 5, Enchantments.WIND_BURST, 1, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
        inv.setItem(2, new ItemStack(Items.GOLDEN_APPLE, 32));
        inv.setItem(3, item(Items.NETHERITE_AXE, 1, Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
        inv.setItem(4, new ItemStack(Items.WIND_CHARGE, 64));
        inv.setItem(5, new ItemStack(Items.ENDER_PEARL, 16));
        inv.setItem(6, potion(Items.SPLASH_POTION, Potions.STRONG_HEALING, 1));
        inv.setItem(7, new ItemStack(Items.TOTEM_OF_UNDYING));
        inv.setItem(8, new ItemStack(Items.EXPERIENCE_BOTTLE, 64));

        // Inventory: spare armor, totems, pearls, wind charges, potions
        int slot = 9;
        inv.setItem(slot++, armor(Items.NETHERITE_HELMET));
        inv.setItem(slot++, armor(Items.NETHERITE_CHESTPLATE));
        inv.setItem(slot++, armor(Items.NETHERITE_LEGGINGS));
        inv.setItem(slot++, armor(Items.NETHERITE_BOOTS));
        for (int i = 0; i < 5; i++) inv.setItem(slot++, new ItemStack(Items.TOTEM_OF_UNDYING));
        inv.setItem(slot++, new ItemStack(Items.ENDER_PEARL, 16));
        inv.setItem(slot++, new ItemStack(Items.ENDER_PEARL, 16));
        inv.setItem(slot++, new ItemStack(Items.WIND_CHARGE, 64));
        inv.setItem(slot++, new ItemStack(Items.GOLDEN_APPLE, 32));
        inv.setItem(slot++, new ItemStack(Items.EXPERIENCE_BOTTLE, 64));
        for (int i = 0; i < 4; i++) inv.setItem(slot++, potion(Items.SPLASH_POTION, Potions.STRONG_HEALING, 1));
        inv.setItem(slot++, potion(Items.SPLASH_POTION, Potions.STRONG_STRENGTH, 1));
        inv.setItem(slot++, potion(Items.SPLASH_POTION, Potions.STRONG_STRENGTH, 1));
        inv.setItem(slot++, potion(Items.SPLASH_POTION, Potions.STRONG_SWIFTNESS, 1));
        inv.setItem(slot++, potion(Items.SPLASH_POTION, Potions.STRONG_SWIFTNESS, 1));
        inv.setItem(slot++, potion(Items.POTION, Potions.LONG_FIRE_RESISTANCE, 1));
        inv.setItem(slot++, potion(Items.POTION, Potions.LONG_FIRE_RESISTANCE, 1));
        inv.setItem(slot++, item(Items.NETHERITE_PICKAXE, 1, Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
        inv.setItem(slot++, new ItemStack(Items.COOKED_BEEF, 64));
    }
}
