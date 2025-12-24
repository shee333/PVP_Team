package org.shee33.pvpteam;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

public class Arena {

    private final PVP_Team plugin;
    private final String name;
    private ArenaState state = ArenaState.WAITING;

    // Settings
    private Location lobby;
    private List<Location> redSpawns = new ArrayList<>();
    private List<Location> blueSpawns = new ArrayList<>();
    private int duration = 6 * 60; // 6 minutes in seconds
    private int targetKills = 30;
    private int minPlayers = 2;

    // Runtime
    private final Set<UUID> players = new HashSet<>();
    private final Map<UUID, TeamType> playerTeams = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Integer> deaths = new HashMap<>();
    private final Map<UUID, Location> deathLocations = new HashMap<>();
    private int redKills = 0;
    private int blueKills = 0;
    private int timeLeft;
    
    // Shared Scoreboard
    private Scoreboard scoreboard;
    private Team redTeam;
    private Team blueTeam;
    
    // Tasks
    private BukkitRunnable countdownTask;
    private BukkitRunnable gameTask;

    public Arena(PVP_Team plugin, String name) {
        this.plugin = plugin;
        this.name = name;
        initSharedScoreboard();
    }
    
    private void initSharedScoreboard() {
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = scoreboard.registerNewObjective("pvpteam", "dummy", ChatColor.GOLD + "ACT/0/ - 团队竞技");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        redTeam = scoreboard.registerNewTeam("Red");
        redTeam.setColor(ChatColor.RED);
        redTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM);
        redTeam.setAllowFriendlyFire(false);
        
        blueTeam = scoreboard.registerNewTeam("Blue");
        blueTeam.setColor(ChatColor.BLUE);
        blueTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM);
        blueTeam.setAllowFriendlyFire(false);
    }

    public String getName() {
        return name;
    }

    public ArenaState getState() {
        return state;
    }

    public void setLobby(Location lobby) {
        this.lobby = lobby;
    }

    public Location getLobby() {
        return lobby;
    }

    public void addSpawn(TeamType team, Location loc) {
        if (team == TeamType.RED) {
            redSpawns.add(loc);
        } else {
            blueSpawns.add(loc);
        }
    }

    public void setDuration(int seconds) {
        this.duration = seconds;
    }

    public void setTargetKills(int kills) {
        this.targetKills = kills;
    }

    public void setMinPlayers(int min) {
        this.minPlayers = min;
    }

    public List<Location> getRedSpawns() {
        return redSpawns;
    }

    public List<Location> getBlueSpawns() {
        return blueSpawns;
    }

    public int getDuration() {
        return duration;
    }

    public int getTargetKills() {
        return targetKills;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public boolean isConfigured() {
        return lobby != null && !redSpawns.isEmpty() && !blueSpawns.isEmpty();
    }

    public void join(Player player) {
        if (state == ArenaState.ENDING) {
            player.sendMessage(ChatColor.RED + "比赛即将结束。");
            return;
        }
        
        if (players.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "你已经在这场比赛中了。");
            return;
        }

        players.add(player.getUniqueId());
        kills.put(player.getUniqueId(), 0);
        deaths.put(player.getUniqueId(), 0);

        // Assign Team
        int redCount = (int) playerTeams.values().stream().filter(t -> t == TeamType.RED).count();
        int blueCount = (int) playerTeams.values().stream().filter(t -> t == TeamType.BLUE).count();

        TeamType team = (redCount <= blueCount) ? TeamType.RED : TeamType.BLUE;
        playerTeams.put(player.getUniqueId(), team);
        
        player.sendMessage(ChatColor.GREEN + "加入了竞技场 " + name + " 队伍：" + team.getChatColor() + team.getName());
        broadcast(ChatColor.YELLOW + player.getName() + " 加入了游戏 (" + players.size() + "/" + minPlayers + " 人即可开始)。");

        // Join Shared Scoreboard
        player.setScoreboard(scoreboard);
        if (team == TeamType.RED) {
            redTeam.addEntry(player.getName());
        } else {
            blueTeam.addEntry(player.getName());
        }
        updateScoreboard();

        // If game is running, spawn immediately
        if (state == ArenaState.RUNNING) {
            spawnPlayer(player);
        } else {
            // Teleport to lobby if waiting
            if (lobby != null) {
                player.teleport(lobby);
            }
            checkStart();
        }
    }

    public void quit(Player player) {
        if (!players.contains(player.getUniqueId())) return;

        players.remove(player.getUniqueId());
        playerTeams.remove(player.getUniqueId());
        kills.remove(player.getUniqueId());
        deaths.remove(player.getUniqueId());
        
        // Remove from shared scoreboard teams
        redTeam.removeEntry(player.getName());
        blueTeam.removeEntry(player.getName());

        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard()); // Reset scoreboard
        
        // Restore inventory if needed (simplified: just clear)
        player.getInventory().clear();
        
        if (lobby != null) {
            player.teleport(lobby);
        }

        broadcast(ChatColor.YELLOW + player.getName() + " 离开了游戏。");

        if (state == ArenaState.RUNNING && players.size() < 2) {
            // Optional: End game if too few players?
            // User didn't specify, but usually yes.
            // For now, let it run or end.
            endGame(null); // Force end
        } else if (state == ArenaState.COUNTDOWN && players.size() < minPlayers) {
            state = ArenaState.WAITING;
            if (countdownTask != null) {
                countdownTask.cancel();
                countdownTask = null;
            }
            broadcast(ChatColor.RED + "倒计时取消。人数不足。");
        }
    }

    private void checkStart() {
        if (state == ArenaState.WAITING && players.size() >= minPlayers) {
            startCountdown();
        }
    }

    private void startCountdown() {
        state = ArenaState.COUNTDOWN;
        countdownTask = new BukkitRunnable() {
            int seconds = 5;
            @Override
            public void run() {
                if (seconds <= 0) {
                    startGame();
                    cancel();
                    return;
                }
                
                // Title and Sound
                for (UUID uuid : players) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.sendTitle(ChatColor.YELLOW + "距离开始还有 " + seconds + " 秒", "", 0, 20, 0);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                    }
                }
                seconds--;
            }
        };
        countdownTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void startGame() {
        state = ArenaState.RUNNING;
        timeLeft = duration;
        
        broadcast(ChatColor.GREEN + "比赛开始！");
        
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                spawnPlayer(p);
            }
        }

        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (timeLeft <= 0) {
                    // Time over, determine winner by kills
                    TeamType winner = null;
                    if (redKills > blueKills) winner = TeamType.RED;
                    else if (blueKills > redKills) winner = TeamType.BLUE;
                    endGame(winner);
                    cancel();
                    return;
                }
                timeLeft--;
                updateScoreboard();
            }
        };
        gameTask.runTaskTimer(plugin, 0L, 20L);
    }

    public void spawnPlayer(Player p) {
        TeamType team = playerTeams.get(p.getUniqueId());
        List<Location> spawns = (team == TeamType.RED) ? redSpawns : blueSpawns;
        if (spawns.isEmpty()) {
            p.sendMessage(ChatColor.RED + "Error: No spawn points found for team " + team.getName());
            return;
        }
        
        // Random spawn or round robin? User said "Set spawn points (multiple)". Random is usually best.
        Location loc = spawns.get(new Random().nextInt(spawns.size()));
        
        if (loc == null || loc.getWorld() == null) {
             p.sendMessage(ChatColor.RED + "Error: Invalid spawn location (World not loaded?).");
             return;
        }
        
        p.teleport(loc);
        p.setGameMode(GameMode.SURVIVAL);
        p.setHealth(20);
        p.setFoodLevel(20);
        p.getInventory().clear(); // Clear inventory before equip
        
        // Execute /clo equip
        try {
            p.performCommand("clo equip");
        } catch (Exception e) {
            // Ignore command errors if plugin not present
        }
        
        // Ensure scoreboard is set (in case of respawn/rejoin oddities)
        if (p.getScoreboard() != scoreboard) {
            p.setScoreboard(scoreboard);
        }
        updateScoreboard();
    }

    public void setDeathLocation(UUID uuid, Location loc) {
        deathLocations.put(uuid, loc);
    }

    public Location getDeathLocation(UUID uuid) {
        return deathLocations.get(uuid);
    }

    public void handleDeath(Player victim, Player killer) {
        if (!players.contains(victim.getUniqueId())) return;
        
        deaths.merge(victim.getUniqueId(), 1, Integer::sum);
        
        if (killer != null && players.contains(killer.getUniqueId())) {
            TeamType killerTeam = playerTeams.get(killer.getUniqueId());
            TeamType victimTeam = playerTeams.get(victim.getUniqueId());
            
            if (killerTeam != victimTeam) {
                kills.merge(killer.getUniqueId(), 1, Integer::sum);
                if (killerTeam == TeamType.RED) redKills++;
                else blueKills++;
                
                checkWin();
            }
            // Kill Message
            broadcast(killerTeam.getChatColor() + killer.getName() + ChatColor.YELLOW + " 击杀了 " + victimTeam.getChatColor() + victim.getName());
        } else {
             // Suicide or other death
             TeamType victimTeam = playerTeams.get(victim.getUniqueId());
             broadcast(victimTeam.getChatColor() + victim.getName() + ChatColor.YELLOW + " 意外死亡了。");
        }
    }

    public void startRespawnSequence(Player victim) {
        victim.setGameMode(GameMode.SPECTATOR);
        victim.getInventory().clear();
        
        new BukkitRunnable() {
            int count = 3;
            @Override
            public void run() {
                if (!players.contains(victim.getUniqueId()) || state != ArenaState.RUNNING) {
                    cancel();
                    return;
                }
                
                if (count <= 0) {
                    spawnPlayer(victim);
                    cancel();
                    return;
                }
                
                victim.sendTitle(ChatColor.RED + "复活倒计时 " + count + " 秒", "", 0, 20, 0);
                count--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void checkWin() {
        if (redKills >= targetKills) {
            endGame(TeamType.RED);
        } else if (blueKills >= targetKills) {
            endGame(TeamType.BLUE);
        }
    }

    private void endGame(TeamType winner) {
        state = ArenaState.ENDING;
        if (gameTask != null) gameTask.cancel();
        
        String winMsg = (winner == null) ? "平局！" : winner.getChatColor() + winner.getName() + " 队获胜！";
        broadcast(ChatColor.BOLD + "" + ChatColor.GOLD + "比赛结束！ " + winMsg);
        
        // Play sound
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                p.sendTitle(winMsg, "", 10, 60, 20);
            }
        }

        // Stats
        StringBuilder stats = new StringBuilder();
        stats.append(ChatColor.GRAY).append("----------------\n");
        stats.append(ChatColor.GOLD).append("获胜者：").append(winMsg).append("\n");
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                stats.append(playerTeams.get(uuid).getChatColor()).append(p.getName())
                     .append(ChatColor.GRAY).append(": ")
                     .append(kills.get(uuid)).append(" 击杀，")
                     .append(deaths.get(uuid)).append(" 死亡\n");
            }
        }
        stats.append(ChatColor.GRAY).append("----------------");
        broadcast(stats.toString());

        // Reset later
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanup();
            }
        }.runTaskLater(plugin, 100L); // 5 seconds post-game
    }

    private void cleanup() {
        for (UUID uuid : new HashSet<>(players)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.getInventory().clear();
                p.setGameMode(GameMode.SURVIVAL);
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                if (lobby != null) p.teleport(lobby);
            }
        }
        players.clear();
        playerTeams.clear();
        kills.clear();
        deaths.clear();
        
        // Clear teams entries
        for (String entry : redTeam.getEntries()) redTeam.removeEntry(entry);
        for (String entry : blueTeam.getEntries()) blueTeam.removeEntry(entry);
        
        redKills = 0;
        blueKills = 0;
        state = ArenaState.WAITING;
    }

    private void broadcast(String msg) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(msg);
        }
    }

    // Scoreboard & Teams
    // initScoreboard method removed as we use shared scoreboard now

    private void updateScoreboard() {
        Objective obj = scoreboard.getObjective("pvpteam");
        if (obj == null) return; 

        obj.getScore(ChatColor.RED + "红队：" + ChatColor.WHITE + redKills + "/" + targetKills).setScore(3);
        obj.getScore(ChatColor.BLUE + "蓝队：" + ChatColor.WHITE + blueKills + "/" + targetKills).setScore(2);
        
        int mins = timeLeft / 60;
        int secs = timeLeft % 60;
        String timeStr = String.format("%02d:%02d", mins, secs);
        obj.getScore(ChatColor.YELLOW + "时间：" + ChatColor.WHITE + timeStr).setScore(1);
        
        // Clear old entries to prevent flickering/duplication
        for (String entry : scoreboard.getEntries()) {
            if (entry.contains("红队") && !entry.equals(ChatColor.RED + "红队：" + ChatColor.WHITE + redKills + "/" + targetKills)) {
                scoreboard.resetScores(entry);
            }
            if (entry.contains("蓝队") && !entry.equals(ChatColor.BLUE + "蓝队：" + ChatColor.WHITE + blueKills + "/" + targetKills)) {
                scoreboard.resetScores(entry);
            }
            if (entry.contains("时间") && !entry.equals(ChatColor.YELLOW + "时间：" + ChatColor.WHITE + timeStr)) {
                scoreboard.resetScores(entry);
            }
        }
    }

    private void updateScoreboard(Player p) {
        // Deprecated/Unused in Shared Scoreboard mode, redirect to global update
        updateScoreboard();
    }

    public boolean hasPlayer(Player p) {
        return players.contains(p.getUniqueId());
    }
    
    public TeamType getTeam(Player p) {
        return playerTeams.get(p.getUniqueId());
    }
}
