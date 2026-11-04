package com.iikka.illusion;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** All the illusions: what the arrow leaves behind while flying, and what happens when it hits. */
public enum Illusion {
    RAINBOW("Rainbow", Items.PINK_DYE, "A rainbow trail and a firework pop"),
    FIRE("Inferno", Items.BLAZE_POWDER, "Flames behind the arrow, a fire blast on hit"),
    LOVE("Love", Items.POPPY, "Hearts everywhere"),
    GALAXY("Galaxy", Items.ENDER_EYE, "Stars and a purple portal swirl"),
    THUNDER("Thunder", Items.LIGHTNING_ROD, "Sparks, then lightning strikes where it lands (no fire)"),
    FROST("Frost", Items.SNOWBALL, "Snowflakes and an icy shatter"),
    TOTEM("Totem", Items.TOTEM_OF_UNDYING, "The totem pop effect when it hits"),
    MUSIC("Music", Items.NOTE_BLOCK, "Music notes and a happy chord"),
    SOUL("Soul", Items.SOUL_LANTERN, "Blue soul fire and souls flying up"),
    EMERALD("Lucky", Items.EMERALD, "Green sparkles and a shower of happy villager stars"),
    BUBBLES("Bubbles", Items.HEART_OF_THE_SEA, "Bubbles that pop when it hits"),
    CHERRY("Cherry Blossom", Items.CHERRY_SAPLING, "Pink petals floating everywhere");

    public final String name;
    public final Item icon;
    public final String info;

    Illusion(String name, Item icon, String info) {
        this.name = name;
        this.icon = icon;
        this.info = info;
    }

    private static final int[] RAINBOW_COLORS = {0xFF0000, 0xFF8800, 0xFFFF00, 0x00FF00, 0x00CCFF, 0x0044FF, 0xAA00FF};

    private static void p(ServerLevel l, ParticleOptions type, Vec3 at, int count, double spread, double speed) {
        l.sendParticles(type, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    private static void sound(ServerLevel l, Vec3 at, Object event, float volume, float pitch) {
        SoundEvent s = event instanceof Holder<?> h ? (SoundEvent) h.value() : (SoundEvent) event;
        l.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
    }

    /** Every tick while the arrow flies. */
    public void trail(ServerLevel l, Vec3 at, int age) {
        switch (this) {
            case RAINBOW -> p(l, new DustParticleOptions(RAINBOW_COLORS[age % RAINBOW_COLORS.length], 1.5f), at, 3, 0.05, 0);
            case FIRE -> { p(l, ParticleTypes.FLAME, at, 3, 0.05, 0.01); p(l, ParticleTypes.SMOKE, at, 1, 0.05, 0); }
            case LOVE -> { if (age % 2 == 0) p(l, ParticleTypes.HEART, at, 1, 0.1, 0); }
            case GALAXY -> { p(l, ParticleTypes.END_ROD, at, 1, 0.05, 0.01); p(l, ParticleTypes.PORTAL, at, 4, 0.1, 0.3); }
            case THUNDER -> p(l, ParticleTypes.ELECTRIC_SPARK, at, 4, 0.1, 0.05);
            case FROST -> { p(l, ParticleTypes.SNOWFLAKE, at, 3, 0.05, 0.01); }
            case TOTEM -> p(l, ParticleTypes.TOTEM_OF_UNDYING, at, 2, 0.05, 0.05);
            case MUSIC -> { if (age % 2 == 0) l.sendParticles(ParticleTypes.NOTE, at.x, at.y, at.z, 0, (age % 24) / 24.0, 0, 0, 1); }
            case SOUL -> { p(l, ParticleTypes.SOUL_FIRE_FLAME, at, 2, 0.05, 0.01); }
            case EMERALD -> p(l, ParticleTypes.HAPPY_VILLAGER, at, 2, 0.1, 0);
            case BUBBLES -> p(l, ParticleTypes.BUBBLE_POP, at, 3, 0.1, 0.02);
            case CHERRY -> p(l, ParticleTypes.CHERRY_LEAVES, at, 3, 0.15, 0);
        }
    }

    /** When the arrow lands or hits something. */
    public void hit(ServerLevel l, Vec3 at) {
        switch (this) {
            case RAINBOW -> {
                for (int c : RAINBOW_COLORS) p(l, new DustParticleOptions(c, 2f), at, 12, 0.8, 0);
                p(l, ParticleTypes.FIREWORK, at, 60, 0.1, 0.3);
                sound(l, at, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.5f, 1f);
                sound(l, at, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.5f, 1f);
            }
            case FIRE -> {
                p(l, ParticleTypes.EXPLOSION_EMITTER, at, 1, 0, 0);
                p(l, ParticleTypes.FLAME, at, 80, 0.2, 0.25);
                p(l, ParticleTypes.LAVA, at, 15, 0.3, 0);
                sound(l, at, SoundEvents.GENERIC_EXPLODE, 1f, 1.2f);
            }
            case LOVE -> {
                p(l, ParticleTypes.HEART, at, 25, 1.0, 0);
                sound(l, at, SoundEvents.PLAYER_LEVELUP, 1f, 1.5f);
            }
            case GALAXY -> {
                p(l, ParticleTypes.PORTAL, at, 200, 0.5, 1.5);
                p(l, ParticleTypes.END_ROD, at, 40, 0.1, 0.2);
                p(l, ParticleTypes.REVERSE_PORTAL, at, 80, 0.3, 0.3);
                sound(l, at, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.6f);
                sound(l, at, SoundEvents.AMETHYST_BLOCK_CHIME, 2f, 0.8f);
            }
            case THUNDER -> {
                var bolt = EntityType.LIGHTNING_BOLT.create(l, EntitySpawnReason.TRIGGERED);
                if (bolt != null) { bolt.setPos(at.x, at.y, at.z); bolt.setVisualOnly(true); l.addFreshEntity(bolt); }
                p(l, ParticleTypes.ELECTRIC_SPARK, at, 80, 0.5, 0.5);
                sound(l, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 2f, 1.2f);
            }
            case FROST -> {
                p(l, ParticleTypes.SNOWFLAKE, at, 80, 0.4, 0.15);
                p(l, ParticleTypes.ITEM_SNOWBALL, at, 40, 0.4, 0.1);
                sound(l, at, SoundEvents.GLASS_BREAK, 1.2f, 0.8f);
                sound(l, at, SoundEvents.POWDER_SNOW_BREAK, 1.2f, 1f);
            }
            case TOTEM -> {
                p(l, ParticleTypes.TOTEM_OF_UNDYING, at, 150, 0.2, 0.6);
                sound(l, at, SoundEvents.TOTEM_USE, 0.8f, 1.2f);
            }
            case MUSIC -> {
                for (int i = 0; i < 20; i++)
                    l.sendParticles(ParticleTypes.NOTE, at.x + Math.random() * 2 - 1, at.y + Math.random() * 1.5, at.z + Math.random() * 2 - 1, 0, i / 24.0, 0, 0, 1);
                sound(l, at, SoundEvents.NOTE_BLOCK_BELL, 1.5f, 1.0f);
                sound(l, at, SoundEvents.NOTE_BLOCK_BELL, 1.5f, 1.26f);
                sound(l, at, SoundEvents.NOTE_BLOCK_BELL, 1.5f, 1.5f);
            }
            case SOUL -> {
                p(l, ParticleTypes.SOUL, at, 30, 0.5, 0.05);
                p(l, ParticleTypes.SOUL_FIRE_FLAME, at, 60, 0.3, 0.15);
                p(l, ParticleTypes.SCULK_SOUL, at, 20, 0.4, 0.05);
                sound(l, at, SoundEvents.SOUL_ESCAPE, 2f, 1f);
            }
            case EMERALD -> {
                p(l, ParticleTypes.HAPPY_VILLAGER, at, 80, 1.0, 0);
                p(l, ParticleTypes.WAX_ON, at, 40, 0.6, 0.5);
                sound(l, at, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.5f, 1.2f);
                sound(l, at, SoundEvents.VILLAGER_YES, 1f, 1f);
            }
            case BUBBLES -> {
                p(l, ParticleTypes.BUBBLE_POP, at, 80, 0.6, 0.1);
                p(l, ParticleTypes.SPLASH, at, 60, 0.6, 0.1);
                sound(l, at, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 2f, 1f);
                sound(l, at, SoundEvents.PLAYER_SPLASH, 1f, 1.3f);
            }
            case CHERRY -> {
                p(l, ParticleTypes.CHERRY_LEAVES, at, 120, 1.2, 0);
                sound(l, at, SoundEvents.CHERRY_LEAVES_BREAK, 2f, 1f);
                sound(l, at, SoundEvents.AMETHYST_BLOCK_CHIME, 2f, 1.4f);
            }
        }
    }
}
