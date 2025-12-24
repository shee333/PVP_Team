package org.shee33.pvpteam;

import org.bukkit.Location;
import org.bukkit.entity.Player;
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
        // Fallback in case they die from something else (e.g. /kill)
        Player p = event.getEntity();
        Arena arena = plugin.getArenaManager().getArena(p);
        if (arena == null) return;

        if (arena.getState() == ArenaState.RUNNING) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setDeathMessage(null); 
            
            Location deathLoc = p.getLocation();
            
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                p.spigot().respawn();
                arena.handleDeath(p, deathLoc);
            }, 1L);
        }
    }
    
    // In case they manually respawn or some other plugin respawns them
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        Arena arena = plugin.getArenaManager().getArena(p);
        if (arena != null && arena.getState() == ArenaState.RUNNING) {
             event.setRespawnLocation(p.getLocation());
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
        
        // Simulated Death Check
        if (arena != null && arena.getState() == ArenaState.RUNNING) {
            if (victim.getHealth() - event.getFinalDamage() <= 0) {
                event.setCancelled(true);
                arena.handleDeath(victim, victim.getLocation());
                victim.setHealth(20); // Heal them so they don't actually die
            }
        }
    }
}
