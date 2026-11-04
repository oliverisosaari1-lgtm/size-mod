package com.iikka.pvp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Saved in config/pvpmenu.json */
public class Config {
    public double x = 0, y = 0, z = 0;          // move the hand (blocks)
    public double scale = 1;                     // size
    public double rotX = 0, rotY = 0, rotZ = 0;  // turn (degrees)

    public static Config INSTANCE = new Config();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("pvpmenu.json");
    }

    public static void load() {
        try { if (Files.exists(file())) INSTANCE = GSON.fromJson(Files.readString(file()), Config.class); } catch (Exception ignored) { }
        if (INSTANCE == null) INSTANCE = new Config();
    }

    public static void save() {
        try { Files.writeString(file(), GSON.toJson(INSTANCE)); } catch (Exception ignored) { }
    }
}
