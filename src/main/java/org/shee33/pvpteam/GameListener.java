package org.shee33.pvpteam;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class GameListener implements Listener {

    private final PVP_Team plugin;

    public GameListener(PVP_Team plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Arena arena = plugin.getArenaManager().getArena(event.getPlayer());
        if (arena != null) {
            arena.quit(event.getPlayer());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        Arena arena = plugin.getArenaManager().getArena(p);
        if (arena == null) return;

        if (arena.getState() == ArenaState.RUNNING) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setDeathMessage(null); // Hide vanilla death message
            
            // Handle stats and broadcast kill message using vanilla logic
            arena.handleDeath(p, p.getKiller());
            
            // Save death location for respawn
            arena.setDeathLocation(p.getUniqueId(), p.getLocation());
            
            // Force instant respawn
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                p.spigot().respawn();
            }, 1L);
        }
    }
    
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        Arena arena = plugin.getArenaManager().getArena(p);
        if (arena != null && arena.getState() == ArenaState.RUNNING) {
             Location deathLoc = arena.getDeathLocation(p.getUniqueId());
             if (deathLoc != null) {
                 event.setRespawnLocation(deathLoc);
                 // After respawn, set spectator and start countdown
                 plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                     arena.startRespawnSequence(p);
                 }, 1L);
             }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        Arena arena = plugin.getArenaManager().getArena(victim);
        
        // Friendly fire and arena checks
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            Arena arena2 = plugin.getArenaManager().getArena(attacker);

            // Not in game
            if (arena == null && arena2 == null) return;

            // One in game, one not? Cancel
            if (arena == null || arena2 == null || !arena.equals(arena2)) {
                event.setCancelled(true);
                return;
            }

            if (arena.getState() != ArenaState.RUNNING) {
                event.setCancelled(true);
                return;
            }

            // Friendly fire check
            TeamType t1 = arena.getTeam(victim);
            TeamType t2 = arena.getTeam(attacker);

            if (t1 != null && t1 == t2) {
                event.setCancelled(true);
                return;
            }
        }
        
        // Removed simulated death check to use vanilla death event
    }
}
