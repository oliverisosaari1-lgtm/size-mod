package com.iikka.smp;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What every item costs. Items in SHOP can be bought in /ah. Anything else sells for DEFAULT_PRICE. */
public class Prices {
    public static final long DEFAULT_PRICE = 10;
    public static final Map<Item, Long> SHOP = new LinkedHashMap<>();
    private static final Map<Item, Long> SELL_ONLY = new LinkedHashMap<>();

    static {
        // Blocks
        SHOP.put(Items.GRASS_BLOCK, 450L);
        SHOP.put(Items.DIRT, 100L);
        SHOP.put(Items.STONE, 150L);
        SHOP.put(Items.COBBLESTONE, 100L);
        SHOP.put(Items.OAK_LOG, 300L);
        SHOP.put(Items.OAK_PLANKS, 100L);
        SHOP.put(Items.SAND, 200L);
        SHOP.put(Items.GLASS, 300L);
        SHOP.put(Items.OBSIDIAN, 1_100L);
        SHOP.put(Items.TNT, 10_000L);
        SHOP.put(Items.TORCH, 50L);
        SHOP.put(Items.SHULKER_BOX, 2_000_000L);
        SHOP.put(Items.BEACON, 100_000_000L);
        // Ores and gems
        SHOP.put(Items.COAL, 1_000L);
        SHOP.put(Items.IRON_INGOT, 5_000L);
        SHOP.put(Items.GOLD_INGOT, 8_000L);
        SHOP.put(Items.EMERALD, 30_000L);
        SHOP.put(Items.DIAMOND, 50_000L);
        SHOP.put(Items.NETHERITE_INGOT, 5_000_000L);
        // Food
        SHOP.put(Items.BREAD, 200L);
        SHOP.put(Items.COOKED_BEEF, 500L);
        SHOP.put(Items.GOLDEN_CARROT, 2_000L);
        SHOP.put(Items.GOLDEN_APPLE, 100_000L);
        SHOP.put(Items.ENCHANTED_GOLDEN_APPLE, 10_000_000L);
        // Gear
        SHOP.put(Items.DIAMOND_SWORD, 200_000L);
        SHOP.put(Items.DIAMOND_PICKAXE, 250_000L);
        SHOP.put(Items.DIAMOND_CHESTPLATE, 400_000L);
        SHOP.put(Items.NETHERITE_SWORD, 11_000_000L);
        SHOP.put(Items.NETHERITE_AXE, 10_000_000L);
        SHOP.put(Items.NETHERITE_PICKAXE, 10_000_000L);
        SHOP.put(Items.NETHERITE_HELMET, 15_000_000L);
        SHOP.put(Items.NETHERITE_CHESTPLATE, 15_000_000L);
        SHOP.put(Items.NETHERITE_LEGGINGS, 15_000_000L);
        SHOP.put(Items.NETHERITE_BOOTS, 15_000_000L);
        SELL_ONLY.put(Items.DRAGON_EGG, 100_000_000L); // can only be sold, not bought
        SHOP.put(Items.KELP, 3_200L);
        SHOP.put(Items.BOW, 20_000L);
        SHOP.put(Items.ARROW, 200L);
        SHOP.put(Items.SHIELD, 15_000L);
        SHOP.put(Items.TOTEM_OF_UNDYING, 25_000_000L);
        SHOP.put(Items.ELYTRA, 500_000_000L);
        SHOP.put(Items.FIREWORK_ROCKET, 5_000L);
        for (net.minecraft.world.item.Item it : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it).getPath();
            if (id.endsWith("_boat") || id.endsWith("_raft")) SHOP.put(it, 1_200L); // every boat $1.2K
        }
        // Other
        SHOP.put(Items.ENDER_PEARL, 15_000L);
        SHOP.put(Items.EXPERIENCE_BOTTLE, 2_000L);
        SHOP.put(Items.BLAZE_ROD, 8_000L);
        SHOP.put(Items.SLIME_BALL, 2_000L);
        SHOP.put(Items.GUNPOWDER, 500L);
        SHOP.put(Items.STRING, 150L);
        SHOP.put(Items.BONE, 100L);

        SELL_ONLY.put(Items.ROTTEN_FLESH, 20L);
        SELL_ONLY.put(Items.WHEAT, 100L);
        SELL_ONLY.put(Items.CARROT, 150L);
        SELL_ONLY.put(Items.POTATO, 150L);
        SELL_ONLY.put(Items.IRON_ORE, 4_000L);
        SELL_ONLY.put(Items.DIAMOND_ORE, 45_000L);
        SELL_ONLY.put(Items.DEEPSLATE_DIAMOND_ORE, 45_000L);
        SELL_ONLY.put(Items.ANCIENT_DEBRIS, 1_000_000L);
    }

    /** Admin-only items that can't be bought. */
    private static boolean hidden(String id) {
        return id.equals("air") || id.contains("command_block") || id.equals("barrier") || id.startsWith("structure_")
            || id.equals("jigsaw") || id.equals("debug_stick") || id.equals("knowledge_book") || id.equals("light")
            || id.startsWith("test_") || id.equals("dragon_egg") || id.equals("enchanted_book") || id.equals("end_portal_frame") || id.equals("bedrock");
    }

    /** Enchanted book worth: 25K x level x level (Sharpness V = 625K), Mending 1M. */
    public static long bookPrice(ItemStack book) {
        net.minecraft.world.item.enchantment.ItemEnchantments ench = book.get(DataComponents.STORED_ENCHANTMENTS);
        if (ench == null || ench.isEmpty()) return DEFAULT_PRICE;
        long total = 0;
        for (var e : ench.entrySet()) {
            String id = e.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            int lvl = e.getIntValue();
            total += id.equals("mending") ? 1_000_000L : 25_000L * lvl * lvl;
        }
        return total;
    }

    /** Names of the enchantments on a book, like "sharpness", for /ah search. */
    public static String bookNames(ItemStack book) {
        net.minecraft.world.item.enchantment.ItemEnchantments ench = book.get(DataComponents.STORED_ENCHANTMENTS);
        if (ench == null) return "";
        StringBuilder b = new StringBuilder();
        for (var e : ench.entrySet()) b.append(e.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("")).append(' ');
        return b.toString();
    }

    private static List<ItemStack> books;

    /** One enchanted book for every enchantment at every level. */
    public static List<ItemStack> allBooks(net.minecraft.server.MinecraftServer server) {
        if (books != null) return books;
        books = new ArrayList<>();
        var reg = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        reg.listElements().forEach(h -> {
            for (int lvl = 1; lvl <= h.value().getMaxLevel(); lvl++) {
                books.add(net.minecraft.world.item.enchantment.EnchantmentHelper.createBook(
                    new net.minecraft.world.item.enchantment.EnchantmentInstance(h, lvl)));
            }
        });
        return books;
    }

    private static List<Item> all;

    /** Every item in the game for /ah, in creative-menu-ish order. */
    public static List<Item> allItems() {
        if (all == null) {
            all = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (!hidden(BuiltInRegistries.ITEM.getKey(item).getPath())) all.add(item);
            }
        }
        return all;
    }

    /** What it costs to buy one in /ah. Items not in the list get a price from how rare they are. */
    public static long buyPrice(Item item) {
        Long p = SHOP.get(item);
        if (p == null) p = SELL_ONLY.get(item);
        if (p != null) return p;
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (id.endsWith("_spawn_egg")) return 5_000_000L;
        if (id.endsWith("_ore")) return 20_000L;
        Rarity rarity = new ItemStack(item).get(DataComponents.RARITY);
        long base = rarity == Rarity.EPIC ? 10_000_000L : rarity == Rarity.RARE ? 1_000_000L : rarity == Rarity.UNCOMMON ? 50_000L : 500L;
        if (item.getDefaultMaxStackSize() == 1) base *= 20; // tools, armor and such
        return base;
    }

    /** What you get for selling one with /sell. */
    public static long price(Item item) {
        Long p = SHOP.get(item);
        if (p == null) p = SELL_ONLY.get(item);
        return p == null ? DEFAULT_PRICE : p;
    }

    public static long price(ItemStack stack) {
        if (stack.is(Items.ENCHANTED_BOOK)) return bookPrice(stack) * stack.getCount();
        return price(stack.getItem()) * stack.getCount();
    }

    /** 1234 -> "1.2K" (no dollar sign) */
    public static String shortNum(long amount) {
        return money(amount).replace("$", "");
    }

    /** "~$ 40" with a gray ~, green $ and white number, like DonutSMP. */
    public static net.minecraft.network.chat.Component priceLine(long amount) {
        return net.minecraft.network.chat.Component.literal("~").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY).withItalic(false))
            .append(net.minecraft.network.chat.Component.literal("$ ").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN).withItalic(false)))
            .append(net.minecraft.network.chat.Component.literal(shortNum(amount)).withStyle(s -> s.withColor(net.minecraft.ChatFormatting.WHITE).withItalic(false)));
    }

    /** 1234 -> "$1.2K", 500000000 -> "$500M" */
    public static String money(long amount) {
        String sign = amount < 0 ? "-" : "";
        long a = Math.abs(amount);
        if (a >= 1_000_000_000_000L) return sign + "$" + trim(a / 1e12) + "T";
        if (a >= 1_000_000_000L) return sign + "$" + trim(a / 1e9) + "B";
        if (a >= 1_000_000L) return sign + "$" + trim(a / 1e6) + "M";
        if (a >= 1_000L) return sign + "$" + trim(a / 1e3) + "K";
        return sign + "$" + a;
    }

    private static String trim(double v) {
        String s = String.format(java.util.Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** Reads "500", "1.5k", "2m", "1b". Returns -1 if it isn't a number. */
    public static long parse(String text) {
        try {
            String t = text.trim().toLowerCase(java.util.Locale.ROOT).replace("$", "").replace(",", "");
            double mult = 1;
            if (t.endsWith("k")) { mult = 1e3; t = t.substring(0, t.length() - 1); }
            else if (t.endsWith("m")) { mult = 1e6; t = t.substring(0, t.length() - 1); }
            else if (t.endsWith("b")) { mult = 1e9; t = t.substring(0, t.length() - 1); }
            double v = Double.parseDouble(t) * mult;
            if (v < 1 || v > 1e15) return -1;
            return (long) v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
