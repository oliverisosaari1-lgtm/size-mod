package com.iikka.smp;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

/** /troll: pick a player, then click a troll. Works on players without the mod. */
public class TrollMenu extends ChestMenu {
    private final SimpleContainer box;
    private final List<UUID> players = new ArrayList<>();
    private UUID target; // null = choosing a player

    private static final int BACK = 18;
    private static final Object[][] TROLLS = {
        // slot, item, name, info
        {10, Items.CREEPER_HEAD, "Creeper Hiss", "Plays a creeper hiss next to them"},
        {11, Items.FIREWORK_ROCKET, "Launch", "Sends them flying up in the air"},
        {12, Items.EGG, "Chicken Party", "Spawns 15 chickens around them"},
        {13, Items.DIAMOND_SWORD, "Drop Item", "Makes them drop what they're holding"},
        {14, Items.ICE, "Freeze", "They can't move for 10 seconds"},
        {15, Items.BARRIER, "Fake Ban", "Kicks them with a fake ban message"},
        {16, Items.COMPASS, "Spin", "Turns them around 180 degrees"},
        {19, Items.POTION, "Drunk", "Wobbly screen for 15 seconds"},
        {20, Items.INK_SAC, "Blind", "Blindness for 10 seconds"},
        {21, Items.LIGHTNING_ROD, "Fake Lightning", "Lightning hits them but does no damage"},
        {22, Items.COMMAND_BLOCK, "Fake OP", "Tells them they got OP (they didn't)"},
        {23, Items.TNT, "Fake TNT", "Lit TNT sound and fuse right next to them, no boom"},
        {24, Items.DIRT, "Fake Diamonds", "Gives them 'Diamonds' that are really dirt"},
        {25, Items.ENDERMAN_SPAWN_EGG, "Jumpscare", "Enderman scream in their ears"},
    };

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new TrollMenu(id, inv, new SimpleContainer(27), (ServerPlayer) p),
            Component.literal("Troll Menu")));
    }

    private TrollMenu(int id, Inventory inv, SimpleContainer box, ServerPlayer player) {
        super(MenuType.GENERIC_9x3, id, inv, box, 3);
        this.box = box;
        fill(player);
    }

    private void fill(ServerPlayer me) {
        for (int i = 0; i < 27; i++) box.setItem(i, Gui.filler());
        ServerPlayer t = target == null ? null : SmpMod.server().getPlayerList().getPlayer(target);
        if (t == null) {
            target = null;
            players.clear();
            for (ServerPlayer p : SmpMod.server().getPlayerList().getPlayers()) {
                if (players.size() >= 27) break;
                players.add(p.getUUID());
            }
            for (int i = 0; i < players.size(); i++) {
                ServerPlayer p = SmpMod.server().getPlayerList().getPlayer(players.get(i));
                String name = p == null ? "?" : p.getName().getString();
                box.setItem(i, Gui.button(Items.PLAYER_HEAD, name, ChatFormatting.YELLOW, "Click to troll " + name));
            }
            return;
        }
        for (Object[] tr : TROLLS) {
            box.setItem((int) tr[0], Gui.button((net.minecraft.world.item.Item) tr[1], (String) tr[2], ChatFormatting.LIGHT_PURPLE,
                (String) tr[3], "Click to troll " + t.getName().getString()));
        }
        box.setItem(BACK, Gui.button(Items.ARROW, "Back", ChatFormatting.WHITE, "Pick another player"));
    }

    @Override
    public void clicked(int slot, int button, ContainerInput type, Player p) {
        if (!(p instanceof ServerPlayer me)) return;
        if (slot >= 0 && slot < 27) {
            if (target == null) {
                if (slot < players.size()) target = players.get(slot);
            } else if (slot == BACK) {
                target = null;
            } else {
                ServerPlayer t = SmpMod.server().getPlayerList().getPlayer(target);
                for (Object[] tr : TROLLS) {
                    if ((int) tr[0] == slot && t != null) {
                        troll(t, (String) tr[2]);
                        me.sendSystemMessage(Component.literal("Trolled " + t.getName().getString() + ": " + tr[2]).withStyle(ChatFormatting.LIGHT_PURPLE));
                    }
                }
            }
            fill(me);
            sendAllDataToRemote();
            return;
        }
        if (type == ContainerInput.QUICK_MOVE) return;
        super.clicked(slot, button, type, p);
    }

    private static void sound(ServerPlayer t, net.minecraft.sounds.SoundEvent s) {
        t.level().playSound(null, t.getX() - Math.sin(Math.toRadians(-t.getYRot())) * -1.5, t.getY(),
            t.getZ() - Math.cos(Math.toRadians(-t.getYRot())) * -1.5, s, SoundSource.HOSTILE, 1f, 1f);
    }

    public static void troll(ServerPlayer t, String name) {
        ServerLevel level = (ServerLevel) t.level();
        switch (name) {
            case "Creeper Hiss" -> sound(t, SoundEvents.CREEPER_PRIMED);
            case "Launch" -> { t.setDeltaMovement(t.getDeltaMovement().add(0, 2.5, 0)); t.hurtMarked = true;
                t.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0)); }
            case "Chicken Party" -> {
                for (int i = 0; i < 15; i++) {
                    BlockPos pos = BlockPos.containing(t.getX() + Math.random() * 6 - 3, t.getY() + 1, t.getZ() + Math.random() * 6 - 3);
                    EntityType.CHICKEN.spawn(level, pos, EntitySpawnReason.COMMAND);
                }
            }
            case "Drop Item" -> {
                ItemStack held = t.getMainHandItem().copy();
                if (!held.isEmpty()) {
                    t.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    ItemEntity e = new ItemEntity(level, t.getX(), t.getEyeY() - 0.3, t.getZ(), held);
                    e.setDeltaMovement(t.getLookAngle().scale(0.3));
                    e.setPickUpDelay(40);
                    level.addFreshEntity(e);
                }
            }
            case "Freeze" -> {
                t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 255));
                t.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 200, 200)); // jump boost this high means no jumping
                t.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 200, 255));
            }
            case "Fake Ban" -> t.connection.disconnect(Component.literal("You are permanently banned from this server!\n\n")
                .withStyle(ChatFormatting.RED).append(Component.literal("Reason: Being too good at Minecraft").withStyle(ChatFormatting.GRAY)));
            case "Spin" -> t.teleportTo(level, t.getX(), t.getY(), t.getZ(), Set.of(), t.getYRot() + 180f, t.getXRot(), true);
            case "Drunk" -> t.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 300, 0));
            case "Blind" -> t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0));
            case "Fake Lightning" -> {
                var bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.COMMAND);
                if (bolt != null) { bolt.setPos(t.getX(), t.getY(), t.getZ()); bolt.setVisualOnly(true); level.addFreshEntity(bolt); }
            }
            case "Fake OP" -> t.sendSystemMessage(Component.literal("[Server: Made " + t.getName().getString() + " a server operator]")
                .withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(true)));
            case "Fake TNT" -> sound(t, SoundEvents.TNT_PRIMED);
            case "Fake Diamonds" -> {
                ItemStack dirt = Gui.withLore(new ItemStack(Items.DIRT, 64), Gui.text("Diamond", ChatFormatting.AQUA), List.of());
                Gui.give(t, dirt);
            }
            case "Jumpscare" -> { sound(t, SoundEvents.ENDERMAN_SCREAM); sound(t, SoundEvents.ENDERMAN_STARE); }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
