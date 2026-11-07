package com.iikka.silly;

import com.iikka.silly.Fx.Impact;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.iikka.silly.Fx.*;

/** All the silly weapons. */
public final class Weapons {
    private Weapons() { }

    public interface Use { void run(ServerPlayer p, ServerLevel l); }
    public interface Hit { void run(ServerPlayer p, ServerLevel l, LivingEntity target); }

    public record Weapon(String name, ChatFormatting color, Item item, String desc, int cooldown, Use use, Hit hit) { }

    public static final List<Weapon> ALL = new ArrayList<>();
    public static final Map<String, Weapon> BY_NAME = new HashMap<>();

    private static void add(String name, ChatFormatting c, Item item, String desc, int cd, Use use, Hit hit) {
        if (BY_NAME.containsKey(name)) return;
        Weapon w = new Weapon(name, c, item, desc, cd, use, hit);
        ALL.add(w);
        BY_NAME.put(name, w);
    }

    private static void use(String name, ChatFormatting c, Item item, String desc, int cd, Use use) { add(name, c, item, desc, cd, use, null); }
    private static void melee(String name, ChatFormatting c, Item item, String desc, Hit hit) { add(name, c, item, desc, 0, null, hit); }

    // ---------------- things you can throw ----------------

    private record Thing(String name, EntityType<?> type, Impact imp, String sound, float pitch, ParticleOptions trail) { }

    private static List<Thing> things() {
        List<Thing> t = new ArrayList<>();
        t.add(new Thing("Pig", EntityTypes.PIG, Impact.BOOM, "entity.pig.ambient", 1.3f, ParticleTypes.HEART));
        t.add(new Thing("Cow", EntityTypes.COW, Impact.KNOCK, "entity.cow.ambient", 1f, ParticleTypes.CLOUD));
        t.add(new Thing("Chicken", EntityTypes.CHICKEN, Impact.PARTY, "entity.chicken.ambient", 1.4f, ParticleTypes.CLOUD));
        t.add(new Thing("Sheep", EntityTypes.SHEEP, Impact.BOUNCE, "entity.sheep.ambient", 1.2f, ParticleTypes.CLOUD));
        t.add(new Thing("Train", EntityTypes.MINECART, Impact.KNOCK, "entity.minecart.riding", 1.5f, ParticleTypes.LARGE_SMOKE));
        t.add(new Thing("Boat", EntityTypes.OAK_BOAT, Impact.SPLASH, "entity.boat.paddle_water", 1f, ParticleTypes.SPLASH));
        t.add(new Thing("TNT", EntityTypes.TNT, Impact.BOOM, "entity.tnt.primed", 1f, ParticleTypes.SMOKE));
        t.add(new Thing("Snowball", EntityTypes.SNOWBALL, Impact.FREEZE, "entity.snowball.throw", 1f, ParticleTypes.SNOWFLAKE));
        t.add(new Thing("Egg", EntityTypes.EGG, Impact.PARTY, "entity.egg.throw", 1f, null));
        t.add(new Thing("Slime", EntityTypes.SLIME, Impact.BOUNCE, "entity.slime.squish", 1f, ParticleTypes.ITEM_SLIME));
        t.add(new Thing("Bee", EntityTypes.BEE, Impact.POISON, "entity.bee.loop_aggressive", 1.2f, ParticleTypes.FALLING_HONEY));
        t.add(new Thing("Villager", EntityTypes.VILLAGER, Impact.CONFUSE, "entity.villager.ambient", 1f, ParticleTypes.HAPPY_VILLAGER));
        t.add(new Thing("Goat", EntityTypes.GOAT, Impact.HONK, "entity.goat.screaming.ambient", 1f, ParticleTypes.CLOUD));
        t.add(new Thing("Frog", EntityTypes.FROG, Impact.BOUNCE, "entity.frog.ambient", 1f, ParticleTypes.HAPPY_VILLAGER));
        t.add(new Thing("Dummy", EntityTypes.ARMOR_STAND, Impact.KNOCK, "entity.armor_stand.hit", 1f, ParticleTypes.CRIT));
        t.add(new Thing("Bunny", EntityTypes.RABBIT, Impact.BOUNCE, "entity.rabbit.jump", 1f, ParticleTypes.HEART));
        t.add(new Thing("Cat", EntityTypes.CAT, Impact.KNOCK, "entity.cat.ambient", 1.2f, ParticleTypes.HEART));
        t.add(new Thing("Fox", EntityTypes.FOX, Impact.FIRE, "entity.fox.screech", 1f, ParticleTypes.FLAME));
        t.add(new Thing("Parrot", EntityTypes.PARROT, Impact.HONK, "entity.parrot.ambient", 1f, ParticleTypes.NOTE));
        t.add(new Thing("Axolotl", EntityTypes.AXOLOTL, Impact.SPLASH, "entity.axolotl.splash", 1f, ParticleTypes.BUBBLE_POP));
        t.add(new Thing("Creeper", EntityTypes.CREEPER, Impact.BOOM, "entity.creeper.primed", 1f, ParticleTypes.SMOKE));
        t.add(new Thing("Wind", EntityTypes.WIND_CHARGE, Impact.KNOCK, "entity.wind_charge.throw", 1f, ParticleTypes.GUST));
        t.add(new Thing("Llama", EntityTypes.LLAMA, Impact.STINK, "entity.llama.spit", 1f, ParticleTypes.SPIT));
        t.add(new Thing("Panda", EntityTypes.PANDA, Impact.SLOW, "entity.panda.sneeze", 1f, ParticleTypes.SNEEZE));
        t.add(new Thing("Turtle", EntityTypes.TURTLE, Impact.SPLASH, "entity.turtle.shamble", 1f, ParticleTypes.BUBBLE_POP));
        return t;
    }

    private static final ChatFormatting[] COLORS = {
        ChatFormatting.RED, ChatFormatting.GOLD, ChatFormatting.YELLOW, ChatFormatting.GREEN, ChatFormatting.AQUA,
        ChatFormatting.LIGHT_PURPLE, ChatFormatting.BLUE, ChatFormatting.DARK_AQUA };

    private static void throwers() {
        int ci = 0;
        for (Thing t : things()) {
            ChatFormatting c = COLORS[ci++ % COLORS.length];
            add(t.name + " Launcher", c, Items.BLAZE_ROD, "Shoots a " + t.name.toLowerCase() + " where you look", 12, (p, l) -> {
                sound(l, p.position(), t.sound, 1, t.pitch);
                throwThing(p, t.type, muzzle(p), p.getLookAngle().scale(1.6), t.imp, 1f, false, t.trail);
            }, null);
            add(t.name + " Shotgun", c, Items.BREEZE_ROD, "5 " + t.name.toLowerCase() + "s at once!", 30, (p, l) -> {
                sound(l, p.position(), t.sound, 1.2f, t.pitch * 0.9f);
                for (int i = 0; i < 5; i++) throwThing(p, t.type, muzzle(p), spread(p.getLookAngle().scale(1.3), 0.5), t.imp, 0.6f, false, t.trail);
            }, null);
            add(t.name + " Minigun", c, Items.NETHERITE_HOE, "Spam click: brrrrr " + t.name.toLowerCase() + "s", 3, (p, l) -> {
                sound(l, p.position(), t.sound, 0.6f, t.pitch * 1.3f);
                throwThing(p, t.type, muzzle(p), spread(p.getLookAngle().scale(1.9), 0.15), t.imp, 0.35f, false, t.trail);
            }, null);
            add(t.name + " Mortar", c, Items.GOLDEN_SHOVEL, "Lobs a huge " + t.name.toLowerCase() + " high in the air", 35, (p, l) -> {
                sound(l, p.position(), "entity.firework_rocket.launch", 1, 0.6f);
                Vec3 v = p.getLookAngle();
                throwThing(p, t.type, muzzle(p), new Vec3(v.x * 1.1, 1.0, v.z * 1.1), t.imp, 1.8f, false, t.trail);
            }, null);
            add(t.name + " Sniper", c, Items.AMETHYST_SHARD, "Super fast flying " + t.name.toLowerCase() + ", no gravity", 22, (p, l) -> {
                sound(l, p.position(), "entity.arrow.shoot", 1, 0.6f);
                sound(l, p.position(), t.sound, 1, t.pitch);
                throwThing(p, t.type, muzzle(p), p.getLookAngle().scale(3.2), t.imp, 1.2f, true, ParticleTypes.END_ROD);
            }, null);
            add(t.name + " Storm", c, Items.NETHER_STAR, "It's raining " + t.name.toLowerCase() + "s where you look", 45, (p, l) -> {
                Vec3 at = lookPoint(p, 60);
                sound(l, at, "item.trident.thunder", 0.6f, 1.6f);
                for (int i = 0; i < 7; i++) {
                    Vec3 from = at.add((R.nextDouble() - 0.5) * 10, 14 + R.nextDouble() * 6, (R.nextDouble() - 0.5) * 10);
                    throwThing(p, t.type, from, new Vec3(0, -0.6, 0), t.imp, 0.9f, false, t.trail);
                }
            }, null);
        }
    }

    // ---------------- hand made specials ----------------

    private static void fart(ServerPlayer p, ServerLevel l, double range, int nausea, double push) {
        Vec3 back = p.position().add(p.getLookAngle().scale(-0.6)).add(0, 0.6, 0);
        sound(l, back, "entity.puffer_fish.blow_out", 2f, 0.5f + R.nextFloat() * 0.2f);
        sound(l, back, "entity.slime.squish", 1.5f, 0.5f);
        parts(l, ParticleTypes.SNEEZE, back, 50, 0.6, 0.05);
        cone(p, range, 0.55, e -> {
            hurt(e, p, 2);
            eff(e, MobEffects.NAUSEA, nausea, 0);
            knock(e, p.position(), push, 0.4);
            parts(l, ParticleTypes.SNEEZE, e.position().add(0, 1, 0), 15, 0.4, 0.02);
        });
        Vec3 look = p.getLookAngle();
        for (int i = 1; i < (int) range; i++) parts(l, ParticleTypes.SNEEZE, p.getEyePosition().add(look.scale(i)), 6, 0.3 * i / 3, 0.01);
    }

    private static void specials() {
        ChatFormatting G = ChatFormatting.GREEN, R0 = ChatFormatting.RED, Y = ChatFormatting.YELLOW, A = ChatFormatting.AQUA,
            P = ChatFormatting.LIGHT_PURPLE, O = ChatFormatting.GOLD, W = ChatFormatting.WHITE, B = ChatFormatting.BLUE;

        use("Fart Gun", G, Items.DYE.get(DyeColor.BROWN), "Pffffrrrt! Stinky cloud in front of you", 15, (p, l) -> fart(p, l, 7, 120, 1.2));
        use("Mega Fart Jetpack", G, Items.DYE.get(DyeColor.BROWN), "Fart yourself into the sky", 30, (p, l) -> {
            fart(p, l, 4, 80, 0.6);
            for (LivingEntity e : around(l, p.position(), 4, p)) eff(e, MobEffects.NAUSEA, 120, 0);
            setVel(p, new Vec3(p.getLookAngle().x * 0.8, 1.6, p.getLookAngle().z * 0.8));
        });
        use("Bean Blaster", G, Items.COCOA_BEANS, "Shoots stinky beans that explode in fart clouds", 10, (p, l) -> {
            sound(l, p.position(), "entity.chicken.egg", 1, 0.6f);
            throwThing(p, EntityTypes.SNOWBALL, muzzle(p), p.getLookAngle().scale(1.7), Impact.STINK, 1.2f, false, ParticleTypes.SNEEZE);
        });
        use("Rocket Launcher", R0, Items.FIREWORK_ROCKET, "Boom. Classic.", 25, (p, l) -> {
            sound(l, p.position(), "entity.firework_rocket.launch", 1.5f, 0.6f);
            throwThing(p, EntityTypes.TNT, muzzle(p), p.getLookAngle().scale(1.8), Impact.BOOM, 2f, true, ParticleTypes.FLAME);
        });
        use("Train Launcher", O, Items.MINECART, "Choo choo! A whole train flies at them", 40, (p, l) -> {
            sound(l, p.position(), "block.bell.use", 2, 0.6f);
            Vec3 look = p.getLookAngle();
            for (int i = 0; i < 6; i++) throwThing(p, EntityTypes.MINECART, muzzle(p).subtract(look.scale(i * 1.3)), look.scale(1.8), Impact.KNOCK, 1.6f, true, ParticleTypes.LARGE_SMOKE);
        });
        melee("Punch Gloves", R0, Items.DYE.get(DyeColor.RED), "Hit someone: they fly away", (p, l, t) -> {
            sound(l, t.position(), "entity.player.attack.strong", 1.5f, 0.7f);
            parts(l, ParticleTypes.CRIT, t.position().add(0, 1, 0), 20, 0.4, 0.3);
            knock(t, p.position(), 3.0, 0.8);
        });
        use("Rocket Fist", R0, Items.DYE.get(DyeColor.RED), "Dash forward and punch everything you hit", 30, (p, l) -> {
            Vec3 look = p.getLookAngle();
            setVel(p, new Vec3(look.x * 2.2, 0.3, look.z * 2.2));
            sound(l, p.position(), "entity.player.attack.sweep", 1.5f, 0.6f);
            parts(l, ParticleTypes.FLAME, p.position(), 20, 0.4, 0.05);
            SillyMod.later(4, () -> { for (LivingEntity e : around(l, p.position(), 3, p)) { hurt(e, p, 6); knock(e, p.position(), 2.4, 0.7); } });
        });
        melee("Boxing Glove on a Stick", Y, Items.STICK, "SPROING! Massive knockback", (p, l, t) -> {
            sound(l, t.position(), "block.piston.extend", 1.5f, 1.2f);
            knock(t, p.position(), 4.0, 1.0);
        });
        melee("Yeet Stick", O, Items.STICK, "Hit them: straight to the moon", (p, l, t) -> {
            sound(l, t.position(), "entity.firework_rocket.launch", 1.5f, 1.2f);
            setVel(t, new Vec3(0, 3.0, 0));
            parts(l, ParticleTypes.CLOUD, t.position(), 20, 0.4, 0.1);
        });
        melee("Rubber Chicken", Y, Items.CHICKEN, "BAWK! Slaps with chicken power", (p, l, t) -> {
            sound(l, t.position(), "entity.chicken.hurt", 2, 0.6f + R.nextFloat());
            parts(l, ParticleTypes.CLOUD, t.position().add(0, 1, 0), 10, 0.3, 0.05);
            knock(t, p.position(), 1.6, 0.5);
        });
        melee("Slap Fish", A, Items.COD, "A wet fishy slap", (p, l, t) -> {
            sound(l, t.position(), "entity.cod.flop", 2, 1);
            parts(l, ParticleTypes.SPLASH, t.position().add(0, 1, 0), 30, 0.4, 0.2);
            knock(t, p.position(), 2.0, 0.5);
            eff(t, MobEffects.SLOWNESS, 40, 1);
        });
        melee("Pillow", W, Items.WOOL.get(DyeColor.WHITE), "Pillow fight! No damage, huge fluff", (p, l, t) -> {
            sound(l, t.position(), "block.wool.break", 2, 0.8f);
            parts(l, ParticleTypes.CLOUD, t.position().add(0, 1, 0), 30, 0.5, 0.1);
            knock(t, p.position(), 2.6, 0.7);
        });
        melee("Grandma's Slipper", P, Items.LEATHER_BOOTS, "The most feared weapon of all time", (p, l, t) -> {
            sound(l, t.position(), "entity.player.attack.crit", 2, 1.5f);
            hurt(t, p, 4);
            knock(t, p.position(), 1.5, 0.4);
            eff(t, MobEffects.SLOWNESS, 60, 1);
        });
        melee("Wet Noodle", Y, Items.STRING, "Floppy. Makes them slow and sad", (p, l, t) -> {
            sound(l, t.position(), "entity.slime.squish_small", 2, 1.2f);
            eff(t, MobEffects.SLOWNESS, 80, 3);
            eff(t, MobEffects.WEAKNESS, 80, 1);
        });
        melee("Banana", Y, Items.DYE.get(DyeColor.YELLOW), "Slip! They spin and fall over", (p, l, t) -> {
            sound(l, t.position(), "block.honey_block.slide", 2, 1.5f);
            t.setYRot(t.getYRot() + 180);
            setVel(t, new Vec3((R.nextDouble() - 0.5) * 2, 0.6, (R.nextDouble() - 0.5) * 2));
            eff(t, MobEffects.NAUSEA, 100, 0);
        });
        melee("Spoon of Doom", W, Items.IRON_SHOVEL, "Tiny spoon. Big explosion", (p, l, t) -> boom(l, p, t.position().add(0, 0.5, 0), 1.6f));
        melee("Thunder Hammer", A, Items.MACE, "Every hit calls lightning", (p, l, t) -> lightning(l, t.position(), p));
        melee("Frying Pan", ChatFormatting.DARK_GRAY, Items.IRON_TRAPDOOR, "BONK", (p, l, t) -> {
            sound(l, t.position(), "block.anvil.land", 1.5f, 1.6f);
            hurt(t, p, 5);
            eff(t, MobEffects.NAUSEA, 80, 0);
            eff(t, MobEffects.SLOWNESS, 30, 2);
        });
        melee("Baguette", O, Items.BREAD, "Ze French bread of justice", (p, l, t) -> {
            sound(l, t.position(), "entity.generic.eat", 2, 0.6f);
            hurt(t, p, 3);
            knock(t, p.position(), 1.8, 0.5);
        });
        melee("Hot Potato", R0, Items.BAKED_POTATO, "Sets them on fire. Ouch, hot!", (p, l, t) -> {
            sound(l, t.position(), "item.firecharge.use", 1.5f, 1);
            t.igniteForSeconds(5);
            knock(t, p.position(), 1, 0.3);
        });
        melee("Ice Pop", A, Items.BLUE_ICE, "Brain freeze!", (p, l, t) -> Impact.FREEZE.apply(l, p, t.position(), 1));
        melee("Vampire Fangs", ChatFormatting.DARK_RED, Items.BONE, "Steal their health", (p, l, t) -> {
            hurt(t, p, 4);
            p.heal(4);
            parts(l, ParticleTypes.DAMAGE_INDICATOR, t.position().add(0, 1, 0), 10, 0.3, 0.1);
            parts(l, ParticleTypes.HEART, p.position().add(0, 2, 0), 4, 0.3, 0.1);
        });
        melee("Bonk Bat", O, Items.STICK, "Home run! Hit them really far", (p, l, t) -> {
            sound(l, t.position(), "block.wood.break", 2, 1.5f);
            Vec3 look = p.getLookAngle();
            setVel(t, new Vec3(look.x * 3.5, 1.2, look.z * 3.5));
        });

        use("Teleport Gun", P, Items.ENDER_EYE, "Teleport to where you look", 20, (p, l) -> {
            Vec3 to = lookPoint(p, 50);
            to = to.subtract(p.getLookAngle().scale(0.8));
            parts(l, ParticleTypes.PORTAL, p.position(), 40, 0.5, 0.5);
            tp(p, to);
            sound(l, to, "entity.enderman.teleport", 1, 1);
            parts(l, ParticleTypes.PORTAL, to, 40, 0.5, 0.5);
        });
        use("Swap Gun", P, Items.ENDER_PEARL, "Swap places with who you look at", 30, (p, l) -> {
            LivingEntity t = lookTarget(p, 50);
            if (t == null) { sound(l, p.position(), "block.note_block.bass", 1, 0.5f); return; }
            Vec3 a = p.position(), b = t.position();
            tp(p, b); tp(t, a);
            sound(l, a, "entity.enderman.teleport", 1, 1.5f);
            sound(l, b, "entity.enderman.teleport", 1, 1.5f);
        });
        use("Gravity Gun", B, Items.LODESTONE, "Pull who you look at to you", 20, (p, l) -> {
            LivingEntity t = lookTarget(p, 40);
            if (t == null) return;
            Vec3 d = p.position().subtract(t.position());
            setVel(t, d.scale(0.25).add(0, 0.5, 0));
            beam(l, ParticleTypes.REVERSE_PORTAL, p.getEyePosition(), t.position().add(0, 1, 0), 0.5);
            sound(l, t.position(), "block.beacon.activate", 1, 2);
        });
        use("Push Gun", B, Items.PISTON, "Shove who you look at very far", 18, (p, l) -> {
            LivingEntity t = lookTarget(p, 40);
            if (t == null) return;
            knock(t, p.position(), 3.5, 0.8);
            beam(l, ParticleTypes.CLOUD, p.getEyePosition(), t.position().add(0, 1, 0), 0.6);
            sound(l, t.position(), "block.piston.extend", 2, 0.7f);
        });
        use("Lightning Wand", Y, Items.LIGHTNING_ROD, "Zap where you look", 30, (p, l) -> lightning(l, lookPoint(p, 60), p));
        use("Triple Lightning", Y, Items.LIGHTNING_ROD, "Zap zap zap", 50, (p, l) -> {
            Vec3 at = lookPoint(p, 60);
            for (int i = 0; i < 3; i++) { int d = i; SillyMod.later(i * 6, () -> lightning(l, at.add((R.nextDouble() - 0.5) * 5 * d, 0, (R.nextDouble() - 0.5) * 5 * d), p)); }
        });
        use("Freeze Ray", A, Items.PRISMARINE_SHARD, "Turn them into an ice cube", 25, (p, l) -> {
            LivingEntity t = lookTarget(p, 40);
            Vec3 end = t != null ? t.position().add(0, 1, 0) : lookPoint(p, 40);
            beam(l, ParticleTypes.SNOWFLAKE, p.getEyePosition(), end, 0.4);
            Impact.FREEZE.apply(l, p, end, 1.3f);
        });
        use("Laser Pointer", R0, Items.REDSTONE_TORCH, "Pew pew! Burning red laser", 6, (p, l) -> {
            LivingEntity t = lookTarget(p, 50);
            Vec3 end = t != null ? t.position().add(0, 1, 0) : lookPoint(p, 50);
            beam(l, ParticleTypes.FLAME, p.getEyePosition().add(0, -0.2, 0), end, 0.5);
            sound(l, p.position(), "block.beacon.deactivate", 0.6f, 2);
            if (t != null) { hurt(t, p, 3); t.igniteForSeconds(2); }
        });
        use("Bubble Wand", A, Items.HEART_OF_THE_SEA, "Trap them in a floating bubble", 25, (p, l) -> {
            LivingEntity t = lookTarget(p, 40);
            if (t == null) return;
            beam(l, ParticleTypes.BUBBLE_POP, p.getEyePosition(), t.position().add(0, 1, 0), 0.5);
            eff(t, MobEffects.LEVITATION, 60, 1);
            parts(l, ParticleTypes.BUBBLE_POP, t.position().add(0, 1, 0), 40, 0.6, 0.05);
            sound(l, t.position(), "block.bubble_column.upwards_ambient", 2, 1);
        });
        use("Disco Stick", P, Items.END_ROD, "Party time! Everyone glows and you get fast", 40, (p, l) -> {
            for (LivingEntity e : around(l, p.position(), 20, p)) eff(e, MobEffects.GLOWING, 200, 0);
            eff(p, MobEffects.SPEED, 200, 2);
            parts(l, ParticleTypes.NOTE, p.position().add(0, 2, 0), 30, 2, 1);
            sound(l, p.position(), "block.note_block.pling", 2, 1.5f);
        });
        use("Cow Rain Stick", ChatFormatting.DARK_GREEN, Items.LEATHER, "Moo. From the sky.", 50, (p, l) -> {
            Vec3 at = lookPoint(p, 60);
            for (int i = 0; i < 12; i++) throwThing(p, EntityTypes.COW, at.add((R.nextDouble() - 0.5) * 12, 18 + R.nextDouble() * 8, (R.nextDouble() - 0.5) * 12), new Vec3(0, -0.4, 0), Impact.KNOCK, 1.2f, false, null);
            sound(l, at, "entity.cow.ambient", 2, 0.6f);
        });
        use("Anvil Rain", ChatFormatting.DARK_GRAY, Items.ANVIL, "Cartoon style anvils fall on them", 60, (p, l) -> {
            Vec3 at = lookPoint(p, 60);
            for (int i = 0; i < 6; i++) {
                BlockPos pos = BlockPos.containing(at.x + (R.nextDouble() - 0.5) * 6, at.y + 14 + i, at.z + (R.nextDouble() - 0.5) * 6);
                placeInAir(l, pos, Blocks.ANVIL.defaultBlockState());
            }
        });
        use("Bee Bazooka", Y, Items.HONEYCOMB, "A swarm of angry bees", 35, (p, l) -> {
            sound(l, p.position(), "entity.bee.loop_aggressive", 2, 1);
            for (int i = 0; i < 6; i++) throwThing(p, EntityTypes.BEE, muzzle(p), spread(p.getLookAngle().scale(1.2), 0.6), Impact.POISON, 0.7f, true, ParticleTypes.FALLING_HONEY);
        });
        use("Sky Launcher", A, Items.FEATHER, "Launch yourself way up, float down slowly", 30, (p, l) -> {
            setVel(p, new Vec3(0, 2.4, 0));
            eff(p, MobEffects.SLOW_FALLING, 120, 0);
            parts(l, ParticleTypes.CLOUD, p.position(), 30, 0.5, 0.1);
            sound(l, p.position(), "entity.firework_rocket.launch", 1.5f, 0.8f);
        });
        use("Pogo Stick", O, Items.BAMBOO, "Boing boing boing", 8, (p, l) -> {
            setVel(p, new Vec3(p.getLookAngle().x * 0.6, 1.1, p.getLookAngle().z * 0.6));
            sound(l, p.position(), "block.slime_block.fall", 1, 1.4f);
            for (LivingEntity e : around(l, p.position(), 2.5, p)) { hurt(e, p, 3); knock(e, p.position(), 1, 0.5); }
        });
        use("Black Hole Gun", ChatFormatting.DARK_PURPLE, Items.ENDER_EYE, "Sucks everyone into one spot", 60, (p, l) -> {
            Vec3 at = lookPoint(p, 40);
            sound(l, at, "block.portal.trigger", 1, 2);
            for (int k = 0; k < 4; k++) SillyMod.later(k * 5, () -> {
                parts(l, ParticleTypes.REVERSE_PORTAL, at, 80, 2, 0.4);
                for (LivingEntity e : around(l, at, 12, p)) setVel(e, at.subtract(e.position()).scale(0.18).add(0, 0.2, 0));
            });
            SillyMod.later(22, () -> boom(l, p, at, 2.5f));
        });
        use("Tornado Stick", W, Items.BREEZE_ROD, "Everyone near you flies up and spins", 40, (p, l) -> {
            for (LivingEntity e : around(l, p.position(), 7, p)) {
                Vec3 d = e.position().subtract(p.position());
                setVel(e, new Vec3(-d.z * 0.3, 1.3, d.x * 0.3));
            }
            parts(l, ParticleTypes.GUST, p.position(), 20, 3, 0);
            sound(l, p.position(), "entity.breeze.wind_burst", 2, 0.7f);
        });
        use("Earthquake Hammer", ChatFormatting.DARK_GRAY, Items.MACE, "Smash the ground: everyone near jumps", 30, (p, l) -> {
            sound(l, p.position(), "entity.generic.explode", 1, 0.5f);
            parts(l, ParticleTypes.EXPLOSION, p.position(), 10, 3, 0);
            for (LivingEntity e : around(l, p.position(), 8, p)) { hurt(e, p, 4); knock(e, p.position(), 1, 1.1); }
        });
        use("Clown Horn", R0, Items.GOAT_HORN, "HONK. Everyone near gets dizzy", 30, (p, l) -> Impact.HONK.apply(l, p, p.position(), 1.5f));
        use("Cake Cannon", P, Items.CAKE, "Happy birthday! Party explosion", 15, (p, l) -> throwThing(p, EntityTypes.SNOWBALL, muzzle(p), p.getLookAngle().scale(1.5), Impact.PARTY, 1.5f, false, ParticleTypes.FIREWORK));
        use("Confetti Cannon", P, Items.DYE.get(DyeColor.PINK), "A blast of confetti in their face", 10, (p, l) -> {
            parts(l, ParticleTypes.FIREWORK, muzzle(p), 60, 0.4, 0.3);
            sound(l, p.position(), "entity.firework_rocket.blast", 1.5f, 1.5f);
            cone(p, 6, 0.6, e -> { hurt(e, p, 2); knock(e, p.position(), 1.2, 0.4); });
        });
        use("Flamethrower", R0, Items.BLAZE_POWDER, "Fire in front of you", 4, (p, l) -> {
            Vec3 look = p.getLookAngle();
            for (int i = 1; i < 7; i++) parts(l, ParticleTypes.FLAME, p.getEyePosition().add(look.scale(i)), 5, 0.15 * i, 0.01);
            sound(l, p.position(), "item.firecharge.use", 0.6f, 1.2f);
            cone(p, 7, 0.75, e -> { e.igniteForSeconds(3); hurt(e, p, 1.5f); });
        });
        use("Snow Blower", A, Items.SNOW_BLOCK, "Freezing wind in front of you", 6, (p, l) -> {
            Vec3 look = p.getLookAngle();
            for (int i = 1; i < 8; i++) parts(l, ParticleTypes.SNOWFLAKE, p.getEyePosition().add(look.scale(i)), 6, 0.15 * i, 0.02);
            cone(p, 8, 0.7, e -> { eff(e, MobEffects.SLOWNESS, 40, 2); e.setTicksFrozen(160); knock(e, p.position(), 0.4, 0.1); });
        });
        use("Leaf Blower", G, Items.OAK_LEAVES, "Blow everyone away", 10, (p, l) -> {
            Vec3 look = p.getLookAngle();
            for (int i = 1; i < 9; i++) parts(l, ParticleTypes.CHERRY_LEAVES, p.getEyePosition().add(look.scale(i)), 6, 0.2 * i, 0.02);
            sound(l, p.position(), "entity.breeze.shoot", 1.5f, 0.8f);
            cone(p, 9, 0.7, e -> knock(e, p.position(), 2, 0.5));
        });
        use("Sneeze Gun", G, Items.SLIME_BALL, "AAAA-CHOOO!", 15, (p, l) -> {
            sound(l, p.position(), "entity.panda.sneeze", 2, 0.8f);
            parts(l, ParticleTypes.SNEEZE, muzzle(p), 60, 0.5, 0.2);
            cone(p, 6, 0.6, e -> { knock(e, p.position(), 2.2, 0.6); eff(e, MobEffects.NAUSEA, 60, 0); });
        });
        use("Magnet", ChatFormatting.GRAY, Items.IRON_INGOT, "Pulls everyone close to you", 30, (p, l) -> {
            for (LivingEntity e : around(l, p.position(), 12, p)) setVel(e, p.position().subtract(e.position()).scale(0.2).add(0, 0.4, 0));
            sound(l, p.position(), "block.beacon.power_select", 1.5f, 0.8f);
        });
        use("Invisibility Cloak", W, Items.GLASS_PANE, "You disappear for 8 seconds", 60, (p, l) -> {
            eff(p, MobEffects.INVISIBILITY, 160, 0);
            parts(l, ParticleTypes.POOF, p.position().add(0, 1, 0), 30, 0.4, 0.05);
            sound(l, p.position(), "entity.illusioner.mirror_move", 1.5f, 1);
        });
        use("Speedy Sneakers", A, Items.SUGAR, "Gotta go fast", 60, (p, l) -> {
            eff(p, MobEffects.SPEED, 200, 4);
            eff(p, MobEffects.JUMP_BOOST, 200, 2);
            sound(l, p.position(), "entity.horse.gallop", 1.5f, 1.5f);
        });
        use("Meteor Wand", O, Items.FIRE_CHARGE, "A meteor crashes where you look", 60, (p, l) -> {
            Vec3 at = lookPoint(p, 60);
            Entity m = throwThing(p, EntityTypes.TNT, at.add(0, 30, 0), new Vec3(0, -1.6, 0), Impact.BOOM, 2.4f, true, ParticleTypes.FLAME);
            sound(l, at, "item.trident.thunder", 1, 0.5f);
            if (m != null) SillyMod.later(1, () -> parts(l, ParticleTypes.LAVA, at, 20, 2, 0));
        });
        use("Duck Duck Boom", Y, Items.FEATHER, "Exploding chickens!", 25, (p, l) -> {
            for (int i = 0; i < 3; i++) throwThing(p, EntityTypes.CHICKEN, muzzle(p), spread(p.getLookAngle().scale(1.2), 0.4), Impact.BOOM, 0.6f, false, ParticleTypes.CLOUD);
            sound(l, p.position(), "entity.chicken.ambient", 2, 1.6f);
        });
        use("Party Popper", P, Items.FIREWORK_STAR, "Pop! Fireworks everywhere around you", 30, (p, l) -> Impact.PARTY.apply(l, p, p.position().add(0, 1, 0), 1.8f));
        use("Honey Gun", O, Items.HONEY_BOTTLE, "Sticky honey makes them super slow", 12, (p, l) -> throwThing(p, EntityTypes.SNOWBALL, muzzle(p), p.getLookAngle().scale(1.5), Impact.SLOW, 1f, false, ParticleTypes.FALLING_HONEY));
        use("Water Pistol", B, Items.WATER_BUCKET, "Splash splash", 5, (p, l) -> throwThing(p, EntityTypes.SNOWBALL, muzzle(p), spread(p.getLookAngle().scale(1.8), 0.1), Impact.SPLASH, 0.5f, false, ParticleTypes.SPLASH));
        use("Super Soaker", B, Items.WATER_BUCKET, "MEGA splash", 25, (p, l) -> {
            for (int i = 0; i < 6; i++) throwThing(p, EntityTypes.SNOWBALL, muzzle(p), spread(p.getLookAngle().scale(1.6), 0.4), Impact.SPLASH, 0.8f, false, ParticleTypes.SPLASH);
        });
        use("Fish Cannon", A, Items.TROPICAL_FISH, "Fires a cod at their face", 10, (p, l) -> throwThing(p, EntityTypes.COD, muzzle(p), p.getLookAngle().scale(1.6), Impact.SPLASH, 1f, false, ParticleTypes.SPLASH));
        use("Pufferfish Grenade", Y, Items.PUFFERFISH_BUCKET, "Poison spikes everywhere", 25, (p, l) -> throwThing(p, EntityTypes.PUFFERFISH, muzzle(p), p.getLookAngle().scale(1.2).add(0, 0.3, 0), Impact.POISON, 1.6f, false, ParticleTypes.BUBBLE_POP));
        use("Squid Ink Blaster", ChatFormatting.DARK_GRAY, Items.INK_SAC, "Blinds everyone it hits", 20, (p, l) -> {
            parts(l, ParticleTypes.SQUID_INK, muzzle(p), 40, 0.5, 0.1);
            cone(p, 8, 0.6, e -> { eff(e, MobEffects.BLINDNESS, 80, 0); eff(e, MobEffects.DARKNESS, 80, 0); });
            sound(l, p.position(), "entity.squid.squirt", 2, 1);
        });
        use("Ghost Mode", W, Items.PHANTOM_MEMBRANE, "Float and fly a bit", 50, (p, l) -> {
            eff(p, MobEffects.LEVITATION, 30, 2);
            eff(p, MobEffects.SLOW_FALLING, 160, 0);
            eff(p, MobEffects.INVISIBILITY, 100, 0);
            sound(l, p.position(), "entity.phantom.flap", 2, 0.8f);
        });
        use("Moon Shoes", ChatFormatting.GRAY, Items.RABBIT_FOOT, "Super high jumps for 10 seconds", 60, (p, l) -> {
            eff(p, MobEffects.JUMP_BOOST, 200, 5);
            eff(p, MobEffects.SLOW_FALLING, 200, 0);
            sound(l, p.position(), "entity.rabbit.jump", 2, 0.6f);
        });
        use("Hulk Smash", G, Items.DYE.get(DyeColor.GREEN), "Super strong and tanky for 10 seconds", 80, (p, l) -> {
            eff(p, MobEffects.STRENGTH, 200, 2);
            eff(p, MobEffects.RESISTANCE, 200, 1);
            sound(l, p.position(), "entity.ravager.roar", 2, 1);
        });
        use("Healing Sandwich", O, Items.BREAD, "Yummy. Heals you", 80, (p, l) -> {
            eff(p, MobEffects.REGENERATION, 100, 2);
            parts(l, ParticleTypes.HEART, p.position().add(0, 2, 0), 8, 0.5, 0.1);
            sound(l, p.position(), "entity.player.burp", 2, 1);
        });
        use("Boomerang", O, Items.STICK, "Flies out and comes back to you", 30, (p, l) -> {
            Entity b = throwThing(p, EntityTypes.ARMOR_STAND, muzzle(p), p.getLookAngle().scale(1.4), Impact.KNOCK, 1f, true, ParticleTypes.CRIT);
            if (b == null) return;
            SillyMod.later(12, () -> { if (b.isAlive()) b.setDeltaMovement(p.getEyePosition().subtract(b.position()).normalize().scale(1.4)); });
        });
        use("Banana Peel Mine", Y, Items.DYE.get(DyeColor.YELLOW), "Everyone near you slips around", 30, (p, l) -> {
            for (LivingEntity e : around(l, p.position(), 6, p)) {
                setVel(e, new Vec3((R.nextDouble() - 0.5) * 2, 0.5, (R.nextDouble() - 0.5) * 2));
                eff(e, MobEffects.NAUSEA, 100, 0);
            }
            sound(l, p.position(), "block.honey_block.slide", 2, 1.5f);
        });
        use("Cobweb Shooter", W, Items.COBWEB, "Sticks them in a web", 25, (p, l) -> {
            LivingEntity t = lookTarget(p, 30);
            if (t == null) return;
            placeInAir(l, t.blockPosition(), Blocks.COBWEB.defaultBlockState());
            placeInAir(l, t.blockPosition().above(), Blocks.COBWEB.defaultBlockState());
            SillyMod.later(100, () -> {
                for (BlockPos q : new BlockPos[] { t.blockPosition(), t.blockPosition().above() })
                    if (l.getBlockState(q).is(Blocks.COBWEB)) l.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
            });
            beam(l, ParticleTypes.WHITE_ASH, p.getEyePosition(), t.position().add(0, 1, 0), 0.4);
        });
        use("Air Horn", R0, Items.GOAT_HORN, "SO LOUD everyone near flies", 40, (p, l) -> {
            sound(l, p.position(), "entity.goat.screaming.prepare_ram", 3, 0.6f);
            sound(l, p.position(), "entity.ravager.roar", 2, 1.4f);
            for (LivingEntity e : around(l, p.position(), 8, p)) knock(e, p.position(), 2.6, 0.9);
        });
        use("Dragon Breath", ChatFormatting.DARK_PURPLE, Items.DRAGON_BREATH, "Purple fire in front of you", 15, (p, l) -> {
            Vec3 look = p.getLookAngle();
            for (int i = 1; i < 8; i++) parts(l, ParticleTypes.PORTAL, p.getEyePosition().add(look.scale(i)), 8, 0.2 * i, 0.1);
            sound(l, p.position(), "entity.ender_dragon.growl", 1, 1.5f);
            cone(p, 8, 0.75, e -> { hurt(e, p, 4); eff(e, MobEffects.WEAKNESS, 60, 0); });
        });
        use("Shulker Blaster", P, Items.SHULKER_SHELL, "Makes them float away", 20, (p, l) -> throwThing(p, EntityTypes.SNOWBALL, muzzle(p), p.getLookAngle().scale(1.6), Impact.FLOAT, 1.2f, true, ParticleTypes.END_ROD));
        use("Stink Bomb", G, Items.FERMENTED_SPIDER_EYE, "Throw it. Run.", 30, (p, l) -> throwThing(p, EntityTypes.SNOWBALL, muzzle(p), p.getLookAngle().scale(1.2).add(0, 0.3, 0), Impact.STINK, 2f, false, ParticleTypes.SNEEZE));
        use("Firework Fist", P, Items.FIREWORK_ROCKET, "Shoot yourself forward with fireworks", 25, (p, l) -> {
            Vec3 look = p.getLookAngle();
            setVel(p, look.scale(2.2).add(0, 0.4, 0));
            parts(l, ParticleTypes.FIREWORK, p.position(), 40, 0.4, 0.2);
            sound(l, p.position(), "entity.firework_rocket.launch", 2, 1);
        });
        use("Ice Rink", A, Items.PACKED_ICE, "Turns them into slippery ice skaters", 40, (p, l) -> {
            for (LivingEntity e : around(l, p.position(), 10, p)) { eff(e, MobEffects.SPEED, 100, 3); eff(e, MobEffects.NAUSEA, 100, 0); setVel(e, new Vec3((R.nextDouble() - 0.5) * 2.5, 0.2, (R.nextDouble() - 0.5) * 2.5)); }
            parts(l, ParticleTypes.SNOWFLAKE, p.position(), 80, 5, 0.02);
        });
        use("Sheep Shield", W, Items.WOOL.get(DyeColor.WHITE), "A ring of sheep explodes out from you", 40, (p, l) -> {
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                throwThing(p, EntityTypes.SHEEP, p.position().add(Math.cos(a) * 1.5, 1, Math.sin(a) * 1.5), new Vec3(Math.cos(a) * 1.2, 0.4, Math.sin(a) * 1.2), Impact.BOUNCE, 0.8f, false, ParticleTypes.CLOUD);
            }
        });
        use("Chicken Jetpack", Y, Items.CHICKEN, "Flap flap flap! Fly a little", 6, (p, l) -> {
            setVel(p, p.getDeltaMovement().multiply(1, 0, 1).add(p.getLookAngle().scale(0.4)).add(0, 0.7, 0));
            parts(l, ParticleTypes.CLOUD, p.position(), 6, 0.3, 0.05);
            sound(l, p.position(), "entity.chicken.ambient", 1, 1.5f);
            eff(p, MobEffects.SLOW_FALLING, 40, 0);
        });
        use("Pumpkin Head", O, Items.CARVED_PUMPKIN, "Spooky! Scares and blinds everyone near", 40, (p, l) -> {
            for (LivingEntity e : around(l, p.position(), 9, p)) { eff(e, MobEffects.DARKNESS, 100, 0); knock(e, p.position(), 1, 0.4); }
            sound(l, p.position(), "entity.witch.celebrate", 2, 0.6f);
        });
        use("Bowling Ball", ChatFormatting.DARK_GRAY, Items.COAL_BLOCK, "STRIKE! Rolls into everyone", 30, (p, l) -> {
            Vec3 look = p.getLookAngle();
            throwThing(p, EntityTypes.SLIME, p.position().add(look.x * 1.5, 0.3, look.z * 1.5), new Vec3(look.x * 1.3, 0, look.z * 1.3), Impact.KNOCK, 2f, false, ParticleTypes.CRIT);
            sound(l, p.position(), "block.stone.place", 2, 0.5f);
        });
        use("Nope Button", R0, Items.STONE_BUTTON, "Everyone near you: NOPE. Bye.", 60, (p, l) -> {
            for (LivingEntity e : around(l, p.position(), 6, p)) knock(e, p.position(), 3.5, 1.5);
            sound(l, p.position(), "entity.villager.no", 3, 0.7f);
            parts(l, ParticleTypes.EXPLOSION_EMITTER, p.position(), 1, 0, 0);
        });
        use("Gnome Cannon", G, Items.RED_MUSHROOM, "Shoots tiny villager gnomes", 15, (p, l) -> {
            Entity v = throwThing(p, EntityTypes.VILLAGER, muzzle(p), p.getLookAngle().scale(1.6), Impact.CONFUSE, 1f, false, ParticleTypes.HAPPY_VILLAGER);
            if (v instanceof net.minecraft.world.entity.AgeableMob a) a.setBaby(true);
            sound(l, p.position(), "entity.villager.celebrate", 2, 1.8f);
        });
    }

    static {
        specials();
        throwers();
    }

    public static Weapon random(Weapon not) {
        Weapon w;
        do { w = ALL.get(R.nextInt(ALL.size())); } while (w == not && ALL.size() > 1);
        return w;
    }
}
