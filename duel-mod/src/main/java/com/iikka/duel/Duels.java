package com.iikka.duel;

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
    record Request(UUID from, long time) {}
    record Duel(UUID a, UUID b) {}

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
        return DuelMod.server.getPlayerList().getPlayer(id);
    }

    // ---------- commands ----------

    public static void challenge(ServerPlayer me, ServerPlayer target) {
        if (target == me) { DuelMod.say(me, "You can't duel yourself!", ChatFormatting.RED); return; }
        if (inDuel(me) || inDuel(target)) { DuelMod.say(me, "One of you is already in a duel!", ChatFormatting.RED); return; }
        requests.put(target.getUUID(), new Request(me.getUUID(), System.currentTimeMillis()));
        DuelMod.say(me, "Duel request sent to " + target.getName().getString(), ChatFormatting.GREEN);
        DuelMod.say(target, me.getName().getString() + " wants to duel you! Mace kit, no block breaking.", ChatFormatting.GOLD);
        DuelMod.say(target, "Type /duel accept or /duel deny", ChatFormatting.YELLOW);
    }

    public static void deny(ServerPlayer me) {
        Request r = requests.remove(me.getUUID());
        if (r == null) { DuelMod.say(me, "You have no duel requests.", ChatFormatting.RED); return; }
        ServerPlayer from = online(r.from);
        if (from != null) DuelMod.say(from, me.getName().getString() + " said no to the duel.", ChatFormatting.RED);
        DuelMod.say(me, "Duel denied.", ChatFormatting.YELLOW);
    }

    public static void accept(ServerPlayer me) {
        Request r = requests.remove(me.getUUID());
        if (r == null || System.currentTimeMillis() - r.time > 60_000) { DuelMod.say(me, "You have no duel requests.", ChatFormatting.RED); return; }
        ServerPlayer other = online(r.from);
        if (other == null) { DuelMod.say(me, "That player left.", ChatFormatting.RED); return; }
        if (inDuel(me) || inDuel(other)) { DuelMod.say(me, "One of you is already in a duel!", ChatFormatting.RED); return; }
        duels.add(new Duel(other.getUUID(), me.getUUID()));

        ServerLevel level = DuelMod.server.overworld();
        if (!DuelMod.DATA.arenaBuilt) { Arena.build(level); DuelMod.DATA.arenaBuilt = true; }
        start(other, level, Arena.X + 0.5, Arena.Z - 14.5, 0f);
        start(me, level, Arena.X + 0.5, Arena.Z + 15.5, 180f);
        DuelMod.say(other, "Duel started against " + me.getName().getString() + "!", ChatFormatting.GOLD);
        DuelMod.say(me, "Duel started against " + other.getName().getString() + "!", ChatFormatting.GOLD);
    }

    // ---------- start / end ----------

    private static void start(ServerPlayer p, ServerLevel level, double x, double z, float yaw) {
        // Save their own things so they get them back after
        DuelMod.Saved back = new DuelMod.Saved();
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) back.items.add(DuelMod.saveItem(inv.getItem(i)));
        back.dimension = p.level().dimension().toString();
        back.x = p.getX(); back.y = p.getY(); back.z = p.getZ(); back.yaw = p.getYRot(); back.pitch = p.getXRot();
        DuelMod.DATA.saved.put(p.getUUID().toString(), back);
        DuelMod.save();

        inv.clearContent();
        giveKit(p);
        heal(p);
        DuelMod.teleport(p, level, x, Arena.Y + 1, z, yaw, 0f);
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
        DuelMod.Saved h = DuelMod.DATA.saved.remove(p.getUUID().toString());
        if (h == null) return;
        var inv = p.getInventory();
        inv.clearContent();
        for (int i = 0; i < h.items.size() && i < inv.getContainerSize(); i++) inv.setItem(i, DuelMod.loadItem(h.items.get(i)));
        heal(p);
        ServerLevel level = DuelMod.levelByName(h.dimension);
        if (level != null) DuelMod.teleport(p, level, h.x, h.y, h.z, h.yaw, h.pitch);
        DuelMod.save();
    }

    /** loser lost (died, left or fell out). */
    public static void lose(UUID loserId) {
        Duel d = find(loserId);
        if (d == null) return;
        duels.remove(d);
        UUID winnerId = d.a.equals(loserId) ? d.b : d.a;
        ServerPlayer winner = online(winnerId), loser = online(loserId);
        String wName = winner != null ? winner.getName().getString() : "?";
        String lName = loser != null ? loser.getName().getString() : "?";
        DuelMod.server.getPlayerList().broadcastSystemMessage(
            Component.literal(wName + " beat " + lName + " in a duel!").withStyle(ChatFormatting.GOLD), false);
        if (winner != null) restore(winner);
        if (loser != null) restore(loser);
    }

    // ---------- the mace kit ----------

    private static Holder<Enchantment> ench(ResourceKey<Enchantment> key) {
        return DuelMod.server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
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
