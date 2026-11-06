package com.iikka.practice;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * PvP kits. A kit is 41 slots: 0-35 inventory (0-8 hotbar), 36 boots, 37 leggings, 38 chestplate, 39 helmet, 40 offhand.
 * Players can edit their own copy of each kit in the kit editor; edits are saved in the world.
 */
public class Kits {
    public static final List<String> NAMES = List.of("axe", "vanilla", "mace", "mace-spear", "sword", "spear", "uhc", "smp");
    public static final int SIZE = 41;
    private static final Map<String, Map<String, ItemStack[]>> edited = new HashMap<>(); // uuid -> kit -> items

    static MinecraftServer server;

    public static String pretty(String kit) {
        return switch (kit) { case "uhc" -> "UHC"; case "smp" -> "SMP"; case "mace-spear" -> "Mace + Spear"; default -> Character.toUpperCase(kit.charAt(0)) + kit.substring(1); };
    }

    // ---------- building items ----------
    private static Item item(String id, Item fallback) {
        Item i = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
        return i == Items.AIR ? fallback : i;
    }

    private static ItemStack ench(ItemStack s, Object... pairs) {
        HolderLookup.RegistryLookup<Enchantment> reg = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            Optional<Holder.Reference<Enchantment>> h = reg.get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace((String) pairs[i])));
            int lvl = (Integer) pairs[i + 1];
            h.ifPresent(e -> s.enchant(e, lvl));
        }
        return s;
    }

    private static ItemStack st(Item i, int n) { return new ItemStack(i, n); }

    private static void armor(ItemStack[] k, String tier, boolean prot) {
        String[] parts = {"boots", "leggings", "chestplate", "helmet"};
        for (int i = 0; i < 4; i++) {
            ItemStack s = new ItemStack(item(tier + "_" + parts[i], Items.IRON_CHESTPLATE));
            if (prot) ench(s, "protection", 4, "unbreaking", 3);
            k[36 + i] = s;
        }
    }

    public static ItemStack[] defaults(String kit) {
        ItemStack[] k = new ItemStack[SIZE];
        Arrays.fill(k, ItemStack.EMPTY);
        switch (kit) {
            case "axe" -> {
                armor(k, "diamond", true);
                k[0] = ench(st(Items.DIAMOND_AXE, 1), "sharpness", 5);
                k[1] = ench(st(Items.DIAMOND_SWORD, 1), "sharpness", 5);
                k[2] = st(Items.CROSSBOW, 1); k[3] = st(Items.ARROW, 16);
                k[8] = st(Items.COOKED_BEEF, 32);
                k[40] = st(Items.SHIELD, 1);
            }
            case "vanilla" -> {
                armor(k, "netherite", true);
                k[0] = ench(st(Items.NETHERITE_SWORD, 1), "sharpness", 5);
                k[1] = st(Items.END_CRYSTAL, 64); k[2] = st(Items.OBSIDIAN, 64);
                k[3] = st(Items.RESPAWN_ANCHOR, 16); k[4] = st(Items.GLOWSTONE, 64);
                k[5] = st(Items.ENDER_PEARL, 16); k[6] = st(Items.GOLDEN_APPLE, 64);
                k[7] = ench(st(Items.NETHERITE_AXE, 1), "sharpness", 5);
                k[8] = st(Items.TOTEM_OF_UNDYING, 1);
                for (int i = 9; i < 15; i++) k[i] = st(Items.TOTEM_OF_UNDYING, 1);
                k[40] = st(Items.TOTEM_OF_UNDYING, 1);
            }
            case "mace" -> {
                armor(k, "netherite", true);
                k[0] = ench(st(Items.MACE, 1), "density", 5, "wind_burst", 1);
                k[1] = ench(st(Items.NETHERITE_SWORD, 1), "sharpness", 5);
                k[2] = st(Items.WIND_CHARGE, 64); k[3] = st(Items.ENDER_PEARL, 16);
                k[4] = st(Items.GOLDEN_APPLE, 32);
                k[8] = st(Items.TOTEM_OF_UNDYING, 1);
                k[40] = st(Items.TOTEM_OF_UNDYING, 1);
            }
            case "mace-spear" -> {
                armor(k, "netherite", true);
                k[0] = ench(st(item("netherite_spear", Items.TRIDENT), 1), "sharpness", 5);
                k[1] = ench(st(Items.MACE, 1), "density", 5, "wind_burst", 1);
                k[2] = st(Items.WIND_CHARGE, 64); k[3] = st(Items.GOLDEN_APPLE, 32);
                k[4] = st(Items.ENDER_PEARL, 16);
                k[40] = st(Items.TOTEM_OF_UNDYING, 1);
            }
            case "sword" -> {
                armor(k, "diamond", true);
                k[0] = ench(st(Items.DIAMOND_SWORD, 1), "sharpness", 5);
                k[1] = st(Items.GOLDEN_APPLE, 16);
                k[8] = st(Items.COOKED_BEEF, 32);
            }
            case "spear" -> {
                armor(k, "diamond", true);
                k[0] = ench(st(item("diamond_spear", Items.TRIDENT), 1), "sharpness", 5);
                k[1] = ench(st(Items.DIAMOND_SWORD, 1), "sharpness", 5);
                k[2] = st(Items.GOLDEN_APPLE, 16);
                k[8] = st(Items.COOKED_BEEF, 32);
            }
            case "uhc" -> {
                armor(k, "diamond", false);
                ench(k[36], "protection", 3); ench(k[37], "protection", 3); ench(k[38], "protection", 2); ench(k[39], "protection", 3);
                k[0] = ench(st(Items.DIAMOND_SWORD, 1), "sharpness", 4);
                k[1] = ench(st(Items.DIAMOND_AXE, 1), "sharpness", 1);
                k[2] = ench(st(Items.BOW, 1), "power", 3);
                k[3] = st(Items.LAVA_BUCKET, 1); k[4] = st(Items.WATER_BUCKET, 1);
                k[5] = st(Items.COBWEB, 8); k[6] = st(Items.GOLDEN_APPLE, 12);
                k[7] = st(Items.COBBLESTONE, 64); k[8] = st(Items.OAK_PLANKS, 64);
                k[9] = st(Items.ARROW, 32); k[10] = st(Items.LAVA_BUCKET, 1); k[11] = st(Items.WATER_BUCKET, 1);
                k[12] = ench(st(Items.DIAMOND_PICKAXE, 1), "efficiency", 3);
                k[40] = st(Items.SHIELD, 1);
            }
            case "smp" -> {
                armor(k, "netherite", true);
                k[0] = ench(st(Items.NETHERITE_SWORD, 1), "sharpness", 5, "fire_aspect", 2);
                k[1] = ench(st(Items.NETHERITE_AXE, 1), "sharpness", 5);
                k[2] = st(Items.ENDER_PEARL, 16); k[3] = st(Items.GOLDEN_APPLE, 64);
                k[4] = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRONG_HEALING);
                k[5] = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRONG_HEALING);
                k[6] = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRONG_STRENGTH);
                k[7] = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRONG_SWIFTNESS);
                k[8] = st(Items.TOTEM_OF_UNDYING, 1);
                k[40] = st(Items.TOTEM_OF_UNDYING, 1);
            }
            default -> { }
        }
        return k;
    }

    public static ItemStack[] get(ServerPlayer p, String kit) {
        ItemStack[] e = edited.getOrDefault(p.getUUID().toString(), Map.of()).get(kit);
        ItemStack[] src = e != null ? e : defaults(kit);
        ItemStack[] copy = new ItemStack[SIZE];
        for (int i = 0; i < SIZE; i++) copy[i] = src[i] == null ? ItemStack.EMPTY : src[i].copy();
        return copy;
    }

    public static void set(ServerPlayer p, String kit, ItemStack[] items) {
        edited.computeIfAbsent(p.getUUID().toString(), k -> new HashMap<>()).put(kit, items);
        save();
    }

    public static void reset(ServerPlayer p, String kit) {
        Map<String, ItemStack[]> m = edited.get(p.getUUID().toString());
        if (m != null) m.remove(kit);
        save();
    }

    /** Clears your inventory and puts the kit on. */
    public static void give(ServerPlayer p, String kit) {
        ItemStack[] k = get(p, kit);
        var inv = p.getInventory();
        inv.clearContent();
        for (int i = 0; i < 36; i++) inv.setItem(i, k[i]);
        p.setItemSlot(EquipmentSlot.FEET, k[36]);
        p.setItemSlot(EquipmentSlot.LEGS, k[37]);
        p.setItemSlot(EquipmentSlot.CHEST, k[38]);
        p.setItemSlot(EquipmentSlot.HEAD, k[39]);
        p.setItemSlot(EquipmentSlot.OFFHAND, k[40]);
        p.containerMenu.broadcastChanges();
        p.inventoryMenu.broadcastChanges();
    }

    // ---------- saving ----------
    private static Path file() { return server.getWorldPath(LevelResource.ROOT).resolve("practice_kits.json"); }

    static void save() {
        try {
            var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            JsonObject root = new JsonObject();
            for (var pe : edited.entrySet()) {
                JsonObject kits = new JsonObject();
                for (var ke : pe.getValue().entrySet()) {
                    JsonArray arr = new JsonArray();
                    for (ItemStack s : ke.getValue()) arr.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, s == null ? ItemStack.EMPTY : s).result().orElse(new JsonObject()));
                    kits.add(ke.getKey(), arr);
                }
                root.add(pe.getKey(), kits);
            }
            Files.writeString(file(), new GsonBuilder().create().toJson(root));
        } catch (Exception ignored) { }
    }

    static void load() {
        edited.clear();
        try {
            if (!Files.exists(file())) return;
            var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            JsonObject root = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
            for (var pe : root.entrySet()) {
                Map<String, ItemStack[]> kits = new HashMap<>();
                for (var ke : pe.getValue().getAsJsonObject().entrySet()) {
                    JsonArray arr = ke.getValue().getAsJsonArray();
                    ItemStack[] items = new ItemStack[SIZE];
                    for (int i = 0; i < SIZE; i++)
                        items[i] = i < arr.size() ? ItemStack.OPTIONAL_CODEC.parse(ops, arr.get(i)).result().orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
                    kits.put(ke.getKey(), items);
                }
                edited.put(pe.getKey(), kits);
            }
        } catch (Exception ignored) { }
    }
}
