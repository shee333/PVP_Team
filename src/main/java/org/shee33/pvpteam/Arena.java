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
    private int redKills = 0;
    private int blueKills = 0;
    private int timeLeft;
    
    // Tasks
    private BukkitRunnable countdownTask;
    private BukkitRunnable gameTask;

    public Arena(PVP_Team plugin, String name) {
        this.plugin = plugin;
        this.name = name;
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
            player.sendMessage(ChatColor.RED + "Game is ending.");
            return;
        }
        
        if (players.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are already in this game.");
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
        
        player.sendMessage(ChatColor.GREEN + "Joined arena " + name + " as Team " + team.getChatColor() + team.getName());
        broadcast(ChatColor.YELLOW + player.getName() + " joined the game (" + players.size() + "/" + minPlayers + " to start).");

        setupScoreboard(player);

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

        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard()); // Reset scoreboard
        
        // Restore inventory if needed (simplified: just clear)
        player.getInventory().clear();
        
        if (lobby != null) {
            player.teleport(lobby);
        }

        broadcast(ChatColor.YELLOW + player.getName() + " left the game.");

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
            broadcast(ChatColor.RED + "Countdown cancelled. Not enough players.");
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
                        p.sendTitle(ChatColor.YELLOW + "Starting in " + seconds, "", 0, 20, 0);
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
        
        broadcast(ChatColor.GREEN + "Game Started!");
        
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
        if (spawns.isEmpty()) return;
        
        // Random spawn or round robin? User said "Set spawn points (multiple)". Random is usually best.
        Location loc = spawns.get(new Random().nextInt(spawns.size()));
        p.teleport(loc);
        p.setGameMode(GameMode.SURVIVAL);
        p.setHealth(20);
        p.setFoodLevel(20);
        p.getInventory().clear(); // Clear inventory before equip
        
        // Execute /clo equip
        p.performCommand("clo equip");
        
        // Setup visuals again just in case
        setupScoreboard(p);
    }

    public void handleDeath(Player victim) {
        if (!players.contains(victim.getUniqueId())) return;
        
        deaths.merge(victim.getUniqueId(), 1, Integer::sum);
        
        Player killer = victim.getKiller();
        if (killer != null && players.contains(killer.getUniqueId())) {
            TeamType killerTeam = playerTeams.get(killer.getUniqueId());
            TeamType victimTeam = playerTeams.get(victim.getUniqueId());
            
            if (killerTeam != victimTeam) {
                kills.merge(killer.getUniqueId(), 1, Integer::sum);
                if (killerTeam == TeamType.RED) redKills++;
                else blueKills++;
                
                checkWin();
            }
        }

        // Logic: Clear drops handled by gamerule or event cancellation? 
        // User says: "Player death -> Clear inventory -> Spectator -> 3s -> Respawn"
        // I should handle PlayerDeathEvent to keepInventory = true (to avoid drops) and then clear it.
        
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
                
                victim.sendTitle(ChatColor.RED + "Respawning in " + count, "", 0, 20, 0);
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
        
        String winMsg = (winner == null) ? "Draw!" : winner.getChatColor() + winner.getName() + " Team Wins!";
        broadcast(ChatColor.BOLD + "" + ChatColor.GOLD + "Game Over! " + winMsg);
        
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
        stats.append(ChatColor.GOLD).append("Winner: ").append(winMsg).append("\n");
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                stats.append(playerTeams.get(uuid).getChatColor()).append(p.getName())
                     .append(ChatColor.GRAY).append(": ")
                     .append(kills.get(uuid)).append(" Kills, ")
                     .append(deaths.get(uuid)).append(" Deaths\n");
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
    private void setupScoreboard(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective("pvpteam", "dummy", ChatColor.GOLD + "ACT/0/ - 团队竞技");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Teams for nametags
        Team red = sb.registerNewTeam("Red");
        red.setColor(ChatColor.RED);
        red.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OTHER_TEAMS); 
        // Wait, "Hide enemy nametags" -> FOR_OWN_TEAM (Show to own team, hide from others)
        red.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM);
        red.setAllowFriendlyFire(false);

        Team blue = sb.registerNewTeam("Blue");
        blue.setColor(ChatColor.BLUE);
        blue.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM);
        blue.setAllowFriendlyFire(false);

        // Add existing players to teams on THIS player's scoreboard
        for (UUID uuid : players) {
            Player target = Bukkit.getPlayer(uuid);
            if (target != null) {
                TeamType t = playerTeams.get(uuid);
                if (t == TeamType.RED) red.addEntry(target.getName());
                else blue.addEntry(target.getName());
            }
        }

        p.setScoreboard(sb);
        updateScoreboard(p);
    }
    
    private void updateScoreboard() {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) updateScoreboard(p);
        }
    }

    private void updateScoreboard(Player p) {
        Scoreboard sb = p.getScoreboard();
        Objective obj = sb.getObjective("pvpteam");
        if (obj == null) return; // Should not happen

        // Simple scoreboard update: Clear and re-add entries? 
        // Or use dynamic entries (ChatColor trick).
        // For simplicity, let's just set scores.
        
        // Lines:
        // 1. Red: X
        // 2. Blue: Y
        // 3. Time: Z
        
        // Note: To avoid flickering, usually we use teams for values, but for simple needs:
        // Resetting scores is okay if not too frequent. 20 ticks is fine.
        
        // Actually, just clearing specific scores is hard. 
        // Let's use specific fake entries.
        
        obj.getScore(ChatColor.RED + "Team Red: " + ChatColor.WHITE + redKills + "/" + targetKills).setScore(3);
        obj.getScore(ChatColor.BLUE + "Team Blue: " + ChatColor.WHITE + blueKills + "/" + targetKills).setScore(2);
        
        int mins = timeLeft / 60;
        int secs = timeLeft % 60;
        String timeStr = String.format("%02d:%02d", mins, secs);
        obj.getScore(ChatColor.YELLOW + "Time: " + ChatColor.WHITE + timeStr).setScore(1);
        
        // We need to clear old scores if values change to avoid duplicates.
        // A common way is to use unique identifiers and update suffixes.
        // But for this MVP, let's keep it simple. Duplicate lines might appear if I don't clear.
        // To fix:
        for (String entry : sb.getEntries()) {
            if (entry.contains("Team Red") && !entry.equals(ChatColor.RED + "Team Red: " + ChatColor.WHITE + redKills + "/" + targetKills)) {
                sb.resetScores(entry);
            }
            if (entry.contains("Team Blue") && !entry.equals(ChatColor.BLUE + "Team Blue: " + ChatColor.WHITE + blueKills + "/" + targetKills)) {
                sb.resetScores(entry);
            }
            if (entry.contains("Time") && !entry.equals(ChatColor.YELLOW + "Time: " + ChatColor.WHITE + timeStr)) {
                sb.resetScores(entry);
            }
        }
    }

    public boolean hasPlayer(Player p) {
        return players.contains(p.getUniqueId());
    }
    
    public TeamType getTeam(Player p) {
        return playerTeams.get(p.getUniqueId());
    }
}
