package org.shee33.pvpteam;

import org.bukkit.plugin.java.JavaPlugin;

public final class PVP_Team extends JavaPlugin {

    private static PVP_Team instance;
    private ArenaManager arenaManager;

    @Override
    public void onEnable() {
        instance = this;
        
        // Load config
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        // Initialize managers
        this.arenaManager = new ArenaManager(this);
        
        // Register commands
        getCommand("pvpteam").setExecutor(new PVPTeamCommand(this));
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        
        getLogger().info("PVP_Team has been enabled!");
    }

    @Override
    public void onDisable() {
        if (arenaManager != null) {
            arenaManager.disable();
        }
        getLogger().info("PVP_Team has been disabled!");
    }

    public static PVP_Team getInstance() {
        return instance;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }
}
