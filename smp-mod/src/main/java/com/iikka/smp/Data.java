package com.iikka.smp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Everything the mod remembers, saved as donutsmp_mod.json in the world folder. */
public class Data {
    public static final long START_MONEY = 1000;

    public static class Home {
        public String dimension;
        public double x, y, z;
        public float yaw, pitch;
    }

    public static class PlayerData {
        public String name = "";
        public long money = START_MONEY;
        public Map<String, Home> homes = new LinkedHashMap<>();
        public boolean tpaRequests = true;
        public boolean combatTimer = true;
    }

    public boolean moneySidebar = true;
    public Map<String, PlayerData> players = new HashMap<>();

    public PlayerData get(UUID id) {
        return players.computeIfAbsent(id.toString(), k -> new PlayerData());
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Data load(Path file) {
        try {
            if (Files.exists(file)) {
                Data d = GSON.fromJson(Files.readString(file), Data.class);
                if (d != null) {
                    if (d.players == null) d.players = new HashMap<>();
                    return d;
                }
            }
        } catch (Exception e) {
            SmpMod.LOG.error("Could not read " + file, e);
        }
        return new Data();
    }

    public void save(Path file) {
        try {
            Files.writeString(file, GSON.toJson(this));
        } catch (Exception e) {
            SmpMod.LOG.error("Could not save " + file, e);
        }
    }
}
