package org.shee33.pvpteam;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ArenaManager {

    private final PVP_Team plugin;
    private final Map<String, Arena> arenas = new HashMap<>();
    private File file;
    private FileConfiguration config;

    public ArenaManager(PVP_Team plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        loadArenas();
    }

    public void createArena(String name) {
        if (arenas.containsKey(name)) return;
        arenas.put(name, new Arena(plugin, name));
        saveArena(name);
    }

    public void deleteArena(String name) {
        if (arenas.containsKey(name)) {
            arenas.remove(name);
            config.set("arenas." + name, null);
            try {
                config.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public Arena getArena(String name) {
        return arenas.get(name);
    }

    public Arena getArena(Player p) {
        for (Arena arena : arenas.values()) {
            if (arena.hasPlayer(p)) {
                return arena;
            }
        }
        return null;
    }

    public void loadArenas() {
        ConfigurationSection section = config.getConfigurationSection("arenas");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            Arena arena = new Arena(plugin, key);
            
            String lobbyStr = section.getString(key + ".lobby");
            if (lobbyStr != null) arena.setLobby(Utils.stringToLoc(lobbyStr));
            
            List<String> redSpawns = section.getStringList(key + ".redSpawns");
            for (String s : redSpawns) arena.addSpawn(TeamType.RED, Utils.stringToLoc(s));
            
            List<String> blueSpawns = section.getStringList(key + ".blueSpawns");
            for (String s : blueSpawns) arena.addSpawn(TeamType.BLUE, Utils.stringToLoc(s));
            
            arena.setDuration(section.getInt(key + ".duration", 360));
            arena.setTargetKills(section.getInt(key + ".targetKills", 30));
            arena.setMinPlayers(section.getInt(key + ".minPlayers", 2));
            
            arenas.put(key, arena);
        }
    }

    public void saveArena(String name) {
        Arena arena = arenas.get(name);
        if (arena == null) return;

        String path = "arenas." + name;
        config.set(path + ".lobby", Utils.locToString(arena.getLobby()));
        
        List<String> redList = arena.getRedSpawns().stream().map(Utils::locToString).collect(Collectors.toList());
        config.set(path + ".redSpawns", redList);
        
        List<String> blueList = arena.getBlueSpawns().stream().map(Utils::locToString).collect(Collectors.toList());
        config.set(path + ".blueSpawns", blueList);
        
        config.set(path + ".duration", arena.getDuration());
        config.set(path + ".targetKills", arena.getTargetKills());
        config.set(path + ".minPlayers", arena.getMinPlayers());
        
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void disable() {
        for (Arena arena : arenas.values()) {
            // Force end games if running?
            // Just simple cleanup not to leave players stranded
        }
    }
}
