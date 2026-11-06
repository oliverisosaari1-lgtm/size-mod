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
            swordAnything(p);
        }
    }

    private static final net.minecraft.resources.Identifier DMG = net.minecraft.resources.Identifier.fromNamespaceAndPath("crouchenchant", "sword_damage");
    private static final net.minecraft.resources.Identifier SPD = net.minecraft.resources.Identifier.fromNamespaceAndPath("crouchenchant", "sword_speed");
    /** Any item without its own attack stats hits like a diamond sword. */
    private static void swordAnything(ServerPlayer p) {
        ItemStack stack = p.getMainHandItem();
        boolean sword = !stack.isEmpty() && stack.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
            net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().isEmpty();
        var dmg = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        var spd = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        if (dmg == null || spd == null) return;
        if (sword) {
            dmg.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(DMG, 6, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            spd.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SPD, -2.4, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        } else {
            if (dmg.getModifier(DMG) != null) dmg.removeModifier(DMG);
            if (spd.getModifier(SPD) != null) spd.removeModifier(SPD);
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

    @Override
    public void onInitialize() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(DetachPayload.TYPE, DetachPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(DetachPayload.TYPE, (payload, ctx) -> DetachMenu.open(ctx.player()));
        ServerTickEvents.END_SERVER_TICK.register(CrouchEnchantMod::tick);
    }
}
