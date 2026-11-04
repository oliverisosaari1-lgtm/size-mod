package com.iikka.smp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Settings for your own game (main menu DonutSMP button). Saved in config/donutsmp-client.json. */
public class ClientConfig {
    public boolean moneyBox = true;
    public boolean priceTooltips = true;
    public int zoom = 4;
    public boolean appliedGameSettings = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("donutsmp-client.json");
    public static ClientConfig INSTANCE = load();

    private static ClientConfig load() {
        try {
            if (Files.exists(FILE)) {
                ClientConfig c = GSON.fromJson(Files.readString(FILE), ClientConfig.class);
                if (c != null) return c;
            }
        } catch (Exception ignored) { }
        return new ClientConfig();
    }

    public void save() {
        try { Files.writeString(FILE, GSON.toJson(this)); } catch (Exception ignored) { }
    }
}
