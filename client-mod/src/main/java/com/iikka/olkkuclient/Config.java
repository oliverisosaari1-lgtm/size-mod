package com.iikka.olkkuclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** What is turned on in the Right Shift menu. Saved in config/olkkus-client.json. */
public class Config {
    public boolean fps = true, cps = true, keystrokes = true, armor = true, coords = false, ping = true,
        direction = false, autoSprint = false, zoom = true;
    public String cape = "olkku";

    public static Config INSTANCE = new Config();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("olkkus-client.json"); }

    public static void load() {
        try {
            if (Files.exists(file())) {
                Config c = GSON.fromJson(Files.readString(file()), Config.class);
                if (c != null) INSTANCE = c;
            }
        } catch (Exception ignored) { }
    }

    public static void save() {
        try { Files.writeString(file(), GSON.toJson(INSTANCE)); } catch (Exception ignored) { }
    }
}
