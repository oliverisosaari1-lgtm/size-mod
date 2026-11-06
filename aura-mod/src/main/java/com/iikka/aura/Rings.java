package com.iikka.aura;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** /ring picks what the rings are made of. Hold G to shoot flying rings of it. */
public class Rings {
    static final String TAG = "aura_ring";
    // Blocks first, then mobs ("mob:" ids)
    static final List<String> CHOICES = List.of(
        "sea_lantern", "glowstone", "amethyst_block", "purpur_block", "crying_obsidian", "diamond_block", "gold_block", "emerald_block",
        "redstone_block", "lapis_block", "ice", "glass", "magma_block", "tnt", "obsidian", "white_concrete", "pink_wool", "slime_block",
        "honey_block", "ochre_froglight", "pearlescent_froglight", "verdant_froglight", "beacon", "end_rod",
        "mob:axolotl", "mob:sheep", "mob:pig", "mob:cow", "mob:chicken", "mob:bee", "mob:parrot", "mob:frog", "mob:cat", "mob:fox",
        "mob:allay", "mob:slime");
    static final Map<UUID, String> pick = new HashMap<>();

    private static class Piece { final Entity e; final Vec3 vel; int life; Piece(Entity e, Vec3 v, int l) { this.e = e; vel = v; life = l; } }
    private static final List<Piece> flying = new ArrayList<>();
    private static final Map<UUID, Integer> lastShot = new HashMap<>();

    static void shoot(ServerPlayer p) {
        String what = pick.getOrDefault(p.getUUID(), "sea_lantern");
        boolean mob = what.startsWith("mob:");
        int now = p.level().getServer().getTickCount();
        Integer last = lastShot.get(p.getUUID());
        if (last != null && now - last < (mob ? 4 : 2)) return;
        lastShot.put(p.getUUID(), now);

        ServerLevel level = (ServerLevel) p.level();
        Vec3 look = p.getLookAngle();
        Vec3 up = Math.abs(look.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = look.cross(up).normalize(), v = u.cross(look).normalize();
        Vec3 center = p.getEyePosition().add(look.scale(3));
        double radius = 4;
        int count = mob ? 14 : 26;
        Vec3 vel = look.scale(1.2);
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            Vec3 at = center.add(u.scale(Math.cos(a) * radius)).add(v.scale(Math.sin(a) * radius));
            Entity e;
            if (mob) {
                e = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(what.substring(4))).create(level, EntitySpawnReason.COMMAND);
                if (!(e instanceof Mob m)) continue;
                m.setNoAi(true);
                m.setSilent(true);
                m.setInvulnerable(true);
                m.setNoGravity(true);
                m.snapTo(at.x, at.y - m.getBbHeight() / 2, at.z, p.getYRot(), 0);
                m.setYHeadRot(p.getYRot());
                m.setYBodyRot(p.getYRot());
            } else {
                Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(level, EntitySpawnReason.COMMAND);
                if (d == null) continue;
                Block b = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(what));
                d.setBlockState(b.defaultBlockState());
                d.setPosRotInterpolationDuration(1);
                d.setPos(at.x - 0.5, at.y - 0.5, at.z - 0.5);
                e = d;
            }
            e.addTag(TAG);
            level.addFreshEntity(e);
            flying.add(new Piece(e, vel, 50));
        }
    }

    static void tick(MinecraftServer server) {
        for (Iterator<Piece> it = flying.iterator(); it.hasNext(); ) {
            Piece pc = it.next();
            if (pc.e.isRemoved() || --pc.life <= 0) { pc.e.discard(); it.remove(); continue; }
            pc.e.setPos(pc.e.getX() + pc.vel.x, pc.e.getY() + pc.vel.y, pc.e.getZ() + pc.vel.z);
            pc.e.setDeltaMovement(Vec3.ZERO);
        }
    }

    static void clear() { for (Piece pc : flying) pc.e.discard(); flying.clear(); }

    private static ItemStack icon(String id) {
        boolean mob = id.startsWith("mob:");
        String name = mob ? id.substring(4) : id;
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(mob ? name + "_spawn_egg" : name));
        if (item == Items.AIR) item = Items.BARRIER;
        ItemStack s = new ItemStack(item);
        String pretty = Arrays.stream(name.split("_")).map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1)).reduce((a, b) -> a + " " + b).orElse(name);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(pretty + (mob ? " (mob)" : "")).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.AQUA)));
        return s;
    }

    static void openMenu(ServerPlayer p) {
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new Menu(id, inv, new SimpleContainer(54)), Component.literal("Pick your ring (hold G to shoot)")));
    }

    static class Menu extends ChestMenu {
        Menu(int id, Inventory inv, SimpleContainer box) {
            super(MenuType.GENERIC_9x6, id, inv, box, 6);
            for (int i = 0; i < CHOICES.size() && i < 54; i++) box.setItem(i, icon(CHOICES.get(i)));
        }

        @Override
        public void clicked(int slot, int button, ClickType type, Player pl) {
            if (!(pl instanceof ServerPlayer p)) return;
            if (slot >= 0 && slot < CHOICES.size()) {
                pick.put(p.getUUID(), CHOICES.get(slot));
                p.displayClientMessage(Component.literal("Ring picked! Hold G to shoot.").withStyle(ChatFormatting.AQUA), true);
                p.closeContainer();
            }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }
}
