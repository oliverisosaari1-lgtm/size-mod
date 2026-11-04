package com.iikka.smp;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.*;

/** Makes the money box and item prices show for players who don't have the mod (normal Minecraft). */
public class VanillaView {
    private static final String OBJ = "donut_box";
    private static final Set<UUID> hasSidebar = new HashSet<>();

    public static boolean vanilla(ServerPlayer p) {
        return !ServerPlayNetworking.canSend(p, MoneyPayload.TYPE);
    }

    // ---------- money box as a sidebar that only this player sees ----------

    public static void updateSidebar(ServerPlayer p, Data.PlayerData d) {
        Objective obj = new Objective(new Scoreboard(), OBJ, ObjectiveCriteria.DUMMY,
            Component.literal(p.getName().getString()).withStyle(s -> s.withColor(net.minecraft.ChatFormatting.WHITE)),
            ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
        if (!d.moneyHud) {
            if (hasSidebar.remove(p.getUUID())) p.connection.send(new ClientboundSetObjectivePacket(obj, 1));
            return;
        }
        if (hasSidebar.add(p.getUUID())) {
            p.connection.send(new ClientboundSetObjectivePacket(obj, 0));
            p.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, obj));
        }
        Component money = Component.literal("$ ").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN))
            .append(Component.literal(Prices.shortNum(d.money)).withStyle(s -> s.withColor(net.minecraft.ChatFormatting.WHITE)));
        Component shards = Component.literal("◆ ").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.LIGHT_PURPLE))
            .append(Component.literal(Prices.shortNum(d.shards)).withStyle(s -> s.withColor(net.minecraft.ChatFormatting.WHITE)));
        p.connection.send(new ClientboundSetScorePacket("money", OBJ, 2, Optional.of(money), Optional.of(BlankFormat.INSTANCE)));
        p.connection.send(new ClientboundSetScorePacket("shards", OBJ, 1, Optional.of(shards), Optional.of(BlankFormat.INSTANCE)));
    }

    public static void forget(UUID id) {
        hasSidebar.remove(id);
    }

    // ---------- "~$ 40" on items, added to what the player's game is sent ----------

    private static ItemStack withPrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return stack;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            if (stack.get(DataComponents.CUSTOM_NAME) != null) return stack;          // menu buttons
            if (lore.lines().get(0).getString().startsWith("~$")) return stack;      // already has a price
        }
        ItemStack copy = stack.copy();
        List<Component> lines = new ArrayList<>();
        lines.add(Prices.priceLine(Prices.price(stack)));
        if (lore != null) lines.addAll(lore.lines());
        copy.set(DataComponents.LORE, new ItemLore(lines));
        return copy;
    }

    private static List<ItemStack> withPrices(List<ItemStack> items) {
        List<ItemStack> out = new ArrayList<>(items.size());
        for (ItemStack s : items) out.add(withPrice(s));
        return out;
    }

    /** Called for every packet sent to a player. */
    public static Packet<?> rewrite(ServerPlayer p, Packet<?> packet) {
        if (p == null || p.isCreative() || !vanilla(p)) return packet; // creative would save the price into the item
        if (packet instanceof ClientboundContainerSetSlotPacket s)
            return new ClientboundContainerSetSlotPacket(s.getContainerId(), s.getStateId(), s.getSlot(), withPrice(s.getItem()));
        if (packet instanceof ClientboundContainerSetContentPacket c)
            return new ClientboundContainerSetContentPacket(c.containerId(), c.stateId(), withPrices(c.items()), withPrice(c.carriedItem()));
        if (packet instanceof ClientboundSetPlayerInventoryPacket i)
            return new ClientboundSetPlayerInventoryPacket(i.slot(), withPrice(i.contents()));
        if (packet instanceof ClientboundSetCursorItemPacket c)
            return new ClientboundSetCursorItemPacket(withPrice(c.contents()));
        return packet;
    }
}
