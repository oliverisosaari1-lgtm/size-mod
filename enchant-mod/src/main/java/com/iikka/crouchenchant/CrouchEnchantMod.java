package com.iikka.crouchenchant;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Every time you crouch, the item in your hand gets a random enchant (any enchant, any item). Same enchant again = one level higher. */
public class CrouchEnchantMod implements ModInitializer {
    private static final Set<UUID> sneaking = new HashSet<>();

    private static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean now = p.isShiftKeyDown();
            boolean was = sneaking.contains(p.getUUID());
            if (now) sneaking.add(p.getUUID()); else sneaking.remove(p.getUUID());
            if (now && !was) enchant(p);
        }
    }

    private static void enchant(ServerPlayer p) {
        ItemStack stack = p.getMainHandItem();
        if (stack.isEmpty()) return;
        List<Holder.Reference<Enchantment>> all = p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().toList();
        if (all.isEmpty()) return;
        Holder<Enchantment> pick = all.get(p.getRandom().nextInt(all.size()));
        int[] lvl = new int[1];
        EnchantmentHelper.updateEnchantments(stack, m -> {
            lvl[0] = Math.min(255, m.getLevel(pick) + 1);
            m.set(pick, lvl[0]);
        });
        p.displayClientMessage(Component.literal("+ ").append(Enchantment.getFullname(pick, lvl[0])).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    /** L: all enchants come off the held item and go onto an enchanted book you get. */
    private static void detach(ServerPlayer p) {
        ItemStack stack = p.getMainHandItem();
        var ench = stack.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        if (stack.isEmpty() || ench.isEmpty()) {
            p.displayClientMessage(Component.literal("This item has no enchants.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ItemStack book = new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK);
        book.set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, ench);
        stack.remove(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
        if (!p.getInventory().add(book)) p.drop(book, false);
        p.displayClientMessage(Component.literal("Enchants taken off! You got them as a book.").withStyle(ChatFormatting.AQUA), true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.8f, 1f);
    }

    @Override
    public void onInitialize() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(DetachPayload.TYPE, DetachPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(DetachPayload.TYPE, (payload, ctx) -> detach(ctx.player()));
        ServerTickEvents.END_SERVER_TICK.register(CrouchEnchantMod::tick);
    }
}
