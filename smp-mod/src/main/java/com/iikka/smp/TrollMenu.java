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

    private static final int BACK = 49;
    private static final Object[][] TROLLS = {
        // slot, item, name, info
        {10, Items.CREEPER_HEAD, "Creeper Hiss", "Plays a creeper hiss right behind them"},
        {11, Items.FIREWORK_ROCKET, "Launch", "Sends them flying up in the air"},
        {12, Items.EGG, "Chicken Party", "Spawns 15 chickens around them"},
        {13, Items.DIAMOND_SWORD, "Drop Item", "Makes them drop what they're holding"},
        {14, Items.ICE, "Freeze", "They can't move for 10 seconds"},
        {15, Items.BARRIER, "Fake Ban", "Kicks them with a fake ban message"},
        {16, Items.COMPASS, "Spin", "Turns them around 180 degrees"},
        {19, Items.POTION, "Drunk", "Wobbly screen for 15 seconds"},
        {20, Items.INK_SAC, "Blind", "Darkness for 10 seconds"},
        {21, Items.LIGHTNING_ROD, "Fake Lightning", "Lightning hits them but does no damage"},
        {22, Items.COMMAND_BLOCK, "Fake OP", "Tells them they got OP (they didn't)"},
        {23, Items.TNT, "Fake TNT", "Lit TNT sound right next to them, no boom"},
        {24, Items.DIRT, "Fake Diamonds", "Gives them 'Diamonds' that are really dirt"},
        {25, Items.ENDER_PEARL, "Jumpscare", "Enderman scream in their ears"},
        {28, Items.SKELETON_SKULL, "Fake Death", "Shows 'You died!' and a death message in chat"},
        {29, Items.BLUE_ICE, "Slippery", "The ground under them feels like ice for 10 seconds"},
        {30, Items.ZOMBIE_HEAD, "Ghost Zombies", "Zombie sounds follow them for 15 seconds"},
        {31, Items.ANVIL, "Anvil Drop", "An anvil falls right next to them"},
        {32, Items.PAPER, "Fake Chat", "Says something silly in chat as them"},
        {33, Items.GLASS, "Glass Cage", "Traps them in glass for 15 seconds"},
        {34, Items.BAKED_POTATO, "Hot Potato", "A Hot Potato they can't get rid of for 20 seconds"},
        {37, Items.NAME_TAG, "Noob Name", "Puts [Noob] in front of their name for 1 minute"},
        {38, Items.CARVED_PUMPKIN, "Pumpkin Head", "Pumpkin stuck on their head for 30 seconds"},
        {39, Items.REDSTONE_BLOCK, "Fake Crash", "Kicks them with a scary crash error"},
        {40, Items.COARSE_DIRT, "Dirt Rain", "Dirt rains down around them"},
        {41, Items.HOPPER, "Shuffle Inventory", "Mixes up all the items in their inventory"},
    };

    // ---------- things that last a while ----------
    private record Task(long end, Runnable each, Runnable done) {}
    private static final List<Task> tasks = new ArrayList<>();
    private static long now;

    private static void during(int ticks, Runnable each, Runnable done) {
        tasks.add(new Task(now + ticks, each, done));
    }

    /** Called every server tick. */
    public static void tick() {
        now++;
        for (Task t : new ArrayList<>(tasks)) {
            try {
                if (now >= t.end) { tasks.remove(t); if (t.done != null) t.done.run(); }
                else if (t.each != null) t.each.run();
            } catch (Exception e) { tasks.remove(t); }
        }
    }

    private static ServerPlayer online(UUID id) {
        return SmpMod.server().getPlayerList().getPlayer(id);
    }

    @SuppressWarnings("unchecked")
    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> type(String id) {
        return (EntityType<T>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(id));
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new TrollMenu(id, inv, new SimpleContainer(54), (ServerPlayer) p),
            Component.literal("Troll Menu")));
    }

    private TrollMenu(int id, Inventory inv, SimpleContainer box, ServerPlayer player) {
        super(MenuType.GENERIC_9x6, id, inv, box, 6);
        this.box = box;
        fill(player);
    }

    private void fill(ServerPlayer me) {
        for (int i = 0; i < 54; i++) box.setItem(i, Gui.filler());
        ServerPlayer t = target == null ? null : SmpMod.server().getPlayerList().getPlayer(target);
        if (t == null) {
            target = null;
            players.clear();
            for (ServerPlayer p : SmpMod.server().getPlayerList().getPlayers()) {
                if (players.size() >= 45) break;
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
        if (slot >= 0 && slot < 54) {
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
            case "Launch" -> { t.setDeltaMovement(t.getDeltaMovement().add(0, 2.5, 0));
                t.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(t));
                t.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0)); }
            case "Chicken Party" -> {
                for (int i = 0; i < 15; i++) {
                    BlockPos pos = BlockPos.containing(t.getX() + Math.random() * 6 - 3, t.getY() + 1, t.getZ() + Math.random() * 6 - 3);
                    type("chicken").spawn(level, pos, EntitySpawnReason.COMMAND);
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
            case "Blind" -> t.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0));
            case "Fake Lightning" -> {
                net.minecraft.world.entity.LightningBolt bolt = (net.minecraft.world.entity.LightningBolt) type("lightning_bolt").create(level, EntitySpawnReason.COMMAND);
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
            case "Fake Death" -> {
                t.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(5, 50, 20));
                t.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal("Score: 0").withStyle(ChatFormatting.YELLOW)));
                t.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("You died!").withStyle(ChatFormatting.RED)));
                sound(t, SoundEvents.PLAYER_DEATH);
                SmpMod.server().getPlayerList().broadcastSystemMessage(Component.literal(t.getName().getString() + " fell out of the world"), false);
            }
            case "Slippery" -> {
                UUID id = t.getUUID();
                Set<BlockPos> sent = new HashSet<>();
                during(200, () -> {
                    ServerPlayer x = online(id);
                    if (x == null || now % 3 != 0) return;
                    BlockPos under = x.blockPosition().below();
                    for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                        BlockPos b = under.offset(dx, 0, dz);
                        if (x.level().getBlockState(b).isAir()) continue;
                        sent.add(b);
                        x.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(b, net.minecraft.world.level.block.Blocks.BLUE_ICE.defaultBlockState()));
                    }
                }, () -> {
                    ServerPlayer x = online(id);
                    if (x != null) for (BlockPos b : sent) x.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(x.level(), b));
                });
            }
            case "Ghost Zombies" -> {
                UUID id = t.getUUID();
                during(300, () -> {
                    ServerPlayer x = online(id);
                    if (x != null && now % 50 == 0) sound(x, now % 100 == 0 ? SoundEvents.ZOMBIE_AMBIENT : SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR);
                }, null);
            }
            case "Anvil Drop" -> {
                BlockPos pos = t.blockPosition().offset(1, 6, 0);
                if (level.getBlockState(pos).isAir()) net.minecraft.world.entity.item.FallingBlockEntity.fall(level, pos, net.minecraft.world.level.block.Blocks.ANVIL.defaultBlockState());
            }
            case "Fake Chat" -> {
                String[] lines = {"I love pink unicorns", "I still sleep with a teddy bear", "my mom picks my skins", "I'm scared of chickens",
                    "can someone teach me how to craft a crafting table", "I lost to a baby zombie again"};
                SmpMod.server().getPlayerList().broadcastSystemMessage(Component.literal("<" + t.getName().getString() + "> " + lines[(int) (Math.random() * lines.length)]), false);
            }
            case "Glass Cage" -> {
                List<BlockPos> placed = new ArrayList<>();
                BlockPos c = t.blockPosition();
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0 && (dy == 0 || dy == 1)) continue; // where they stand
                    BlockPos b = c.offset(dx, dy, dz);
                    if (level.getBlockState(b).isAir()) { level.setBlockAndUpdate(b, net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState()); placed.add(b); }
                }
                t.teleportTo(level, c.getX() + 0.5, c.getY(), c.getZ() + 0.5, Set.of(), t.getYRot(), t.getXRot(), true);
                during(300, null, () -> { for (BlockPos b : placed) if (level.getBlockState(b).is(net.minecraft.world.level.block.Blocks.GLASS)) level.removeBlock(b, false); });
            }
            case "Hot Potato" -> {
                UUID id = t.getUUID();
                during(400, () -> {
                    ServerPlayer x = online(id);
                    if (x == null || now % 10 != 0) return;
                    if (!hasHot(x)) Gui.give(x, hotPotato());
                    if (now % 40 == 0) x.sendSystemMessage(Component.literal("The Hot Potato is burning your hands!").withStyle(ChatFormatting.GOLD));
                }, () -> {
                    ServerPlayer x = online(id);
                    if (x == null) return;
                    for (int i = 0; i < x.getInventory().getContainerSize(); i++)
                        if (isHot(x.getInventory().getItem(i))) x.getInventory().setItem(i, ItemStack.EMPTY);
                });
            }
            case "Noob Name" -> {
                var board = SmpMod.server().getScoreboard();
                var team = board.getPlayerTeam("donut_noob");
                if (team == null) { team = board.addPlayerTeam("donut_noob"); team.setPlayerPrefix(Component.literal("[Noob] ").withStyle(ChatFormatting.RED)); }
                String who = t.getScoreboardName();
                board.addPlayerToTeam(who, team);
                var finalTeam = team;
                during(1200, null, () -> { if (board.getPlayersTeam(who) == finalTeam) board.removePlayerFromTeam(who, finalTeam); });
            }
            case "Pumpkin Head" -> {
                UUID id = t.getUUID();
                ItemStack old = t.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).copy();
                if (!old.isEmpty()) Gui.give(t, old);
                during(600, () -> {
                    ServerPlayer x = online(id);
                    if (x != null && !x.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN))
                        x.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
                }, () -> {
                    ServerPlayer x = online(id);
                    if (x != null && x.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN))
                        x.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
                });
            }
            case "Fake Crash" -> t.connection.disconnect(Component.literal("Internal Exception: java.lang.OutOfMemoryError: Java heap space\n\n").withStyle(ChatFormatting.RED)
                .append(Component.literal("Your game ran out of memory and your world may be corrupted.\nError code: 0xDEAD-B33F").withStyle(ChatFormatting.GRAY)));
            case "Dirt Rain" -> {
                for (int i = 0; i < 40; i++) {
                    ItemEntity e = new ItemEntity(level, t.getX() + Math.random() * 8 - 4, t.getY() + 8 + Math.random() * 4, t.getZ() + Math.random() * 8 - 4, new ItemStack(Items.DIRT));
                    e.setPickUpDelay(10);
                    level.addFreshEntity(e);
                }
            }
            case "Shuffle Inventory" -> {
                var inv = t.getInventory();
                List<ItemStack> items = new ArrayList<>();
                for (int i = 0; i < 36; i++) items.add(inv.getItem(i));
                Collections.shuffle(items);
                for (int i = 0; i < 36; i++) inv.setItem(i, items.get(i));
            }
        }
    }

    private static ItemStack hotPotato() {
        return Gui.button(Items.BAKED_POTATO, "Hot Potato", ChatFormatting.GOLD, "You can't get rid of it!");
    }

    private static boolean isHot(ItemStack s) {
        var name = s.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        return s.is(Items.BAKED_POTATO) && name != null && name.getString().equals("Hot Potato");
    }

    private static boolean hasHot(ServerPlayer x) {
        for (int i = 0; i < x.getInventory().getContainerSize(); i++) if (isHot(x.getInventory().getItem(i))) return true;
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
