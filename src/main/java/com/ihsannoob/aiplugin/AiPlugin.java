package com.ihsannoob.aiplugin;

import com.google.gson.Gson;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class AiPlugin extends JavaPlugin {

    private CerebrasClient cerebrasClient;
    private DatabaseManager databaseManager;
    private Gson gson;
    private GuiManager guiManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        gson = new Gson();

        // Ensure plugin data folder exists
        File data = getDataFolder();
        if (!data.exists()) data.mkdirs();

        // Init DB manager (per-player sqlite files inside plugin folder)
        databaseManager = new DatabaseManager(getDataFolder(), getLogger());

        // Init Cerebras client using config values
        reloadCerebrasClient();

        // Init GUI manager (registers events)
        guiManager = new GuiManager(this);

        // Register command
        if (getCommand("ai") != null) {
            getCommand("ai").setExecutor(new AiCommand(this));
        } else {
            getLogger().warning("Command 'ai' not defined in plugin.yml");
        }

        getLogger().info("AICerebrasPlugin enabled.");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.closeAll();
        }
        getLogger().info("AICerebrasPlugin disabled.");
    }

    public CerebrasClient getCerebrasClient() {
        return cerebrasClient;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public Gson getGson() {
        return gson;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public void reloadCerebrasClient() {
        // Reload config and recreate client
        reloadConfig();
        String apiKey = getConfig().getString("api_key", "");
        String endpoint = getConfig().getString("endpoint", "https://api.cerebras.net/v1/generate");
        String model = getConfig().getString("model", "llama-3.3-70b");
        int maxTokens = getConfig().getInt("max_tokens", 512);
        int timeoutSeconds = getConfig().getInt("timeout_seconds", 30);

        cerebrasClient = new CerebrasClient(apiKey, endpoint, model, maxTokens, timeoutSeconds, getLogger(), getGson());
        getLogger().info("Cerebras client reloaded (model=" + model + ")");
    }

}
