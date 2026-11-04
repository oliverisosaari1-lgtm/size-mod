package com.iikka.smp;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Player auction listings: /ah sell, buying them, and taking them back. */
public class Listings {
    public static final int MAX_PER_PLAYER = 14;

    public static String save(MinecraftServer server, ItemStack stack) {
        if (stack.isEmpty()) return "{}";
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        return ItemStack.CODEC.encodeStart(ops, stack).getOrThrow().toString();
    }

    public static ItemStack loadItem(MinecraftServer server, String json) {
        try {
            var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            return ItemStack.CODEC.parse(ops, JsonParser.parseString(json)).result().orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    public static ItemStack load(MinecraftServer server, Data.Listing l) {
        try {
            var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            return ItemStack.CODEC.parse(ops, JsonParser.parseString(l.item)).result().orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    public static List<Data.Listing> of(ServerPlayer p) {
        List<Data.Listing> out = new ArrayList<>();
        for (Data.Listing l : SmpMod.DATA.listings) if (l.seller.equals(p.getUUID().toString())) out.add(l);
        return out;
    }

    /** The item with extra lines underneath: price, seller and what clicking does. */
    public static ItemStack display(MinecraftServer server, Data.Listing l, String action) {
        ItemStack stack = load(server, l);
        if (stack.isEmpty()) return stack;
        List<Component> lore = new ArrayList<>();
        ItemLore old = stack.get(DataComponents.LORE);
        if (old != null) lore.addAll(old.lines());
        lore.add(Component.empty());
        lore.add(Prices.priceLine(l.price));
        lore.add(Gui.text("Seller: " + l.sellerName, ChatFormatting.GRAY));
        lore.add(Gui.text(action, ChatFormatting.YELLOW));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    public static void list(ServerPlayer p, long price) {
        ItemStack hand = p.getMainHandItem();
        if (hand.isEmpty()) { msg(p, "Hold the item you want to sell in your hand", ChatFormatting.RED); return; }
        if (of(p).size() >= MAX_PER_PLAYER) { msg(p, "You can only have " + MAX_PER_PLAYER + " items listed", ChatFormatting.RED); return; }
        MinecraftServer server = SmpMod.server();
        Data.Listing l = new Data.Listing();
        l.id = SmpMod.DATA.nextListingId++;
        l.seller = p.getUUID().toString();
        l.sellerName = p.getName().getString();
        l.item = save(server, hand.copy());
        l.price = price;
        l.time = System.currentTimeMillis();
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        SmpMod.DATA.listings.add(l);
        SmpMod.saveNow();
        msg(p, "Listed " + hand.getCount() + "x " + hand.getHoverName().getString() + " for " + Prices.money(price) + "!", ChatFormatting.GREEN);
    }

    public static void buy(ServerPlayer p, Data.Listing l) {
        if (!SmpMod.DATA.listings.contains(l)) { msg(p, "Someone already bought that!", ChatFormatting.RED); return; }
        if (l.seller.equals(p.getUUID().toString())) { cancel(p, l); return; }
        if (SmpMod.money(p) < l.price) { msg(p, "You need " + Prices.money(l.price) + " for that!", ChatFormatting.RED); return; }
        MinecraftServer server = SmpMod.server();
        ItemStack stack = load(server, l);
        SmpMod.DATA.listings.remove(l);
        SmpMod.addMoney(p, -l.price);
        Data.PlayerData seller = SmpMod.DATA.get(UUID.fromString(l.seller));
        seller.money += l.price;
        Gui.give(p, stack);
        SmpMod.updateSidebar();
        SmpMod.saveNow();
        msg(p, "Bought " + stack.getCount() + "x " + stack.getHoverName().getString() + " from " + l.sellerName + " for " + Prices.money(l.price), ChatFormatting.GREEN);
        ServerPlayer online = server.getPlayerList().getPlayer(UUID.fromString(l.seller));
        if (online != null) msg(online, p.getName().getString() + " bought your " + stack.getHoverName().getString() + " for " + Prices.money(l.price) + "!", ChatFormatting.GREEN);
    }

    public static void cancel(ServerPlayer p, Data.Listing l) {
        if (!SmpMod.DATA.listings.remove(l)) return;
        ItemStack stack = load(SmpMod.server(), l);
        Gui.give(p, stack);
        SmpMod.saveNow();
        msg(p, "Took back " + stack.getHoverName().getString(), ChatFormatting.YELLOW);
    }

    private static void msg(ServerPlayer p, String t, ChatFormatting c) {
        p.sendSystemMessage(Component.literal(t).withStyle(c));
    }
}
