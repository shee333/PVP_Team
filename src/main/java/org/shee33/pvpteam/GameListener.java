package org.shee33.pvpteam;

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
        Player p = event.getEntity();
        Arena arena = plugin.getArenaManager().getArena(p);
        if (arena == null) return;

        if (arena.getState() == ArenaState.RUNNING) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setDeathMessage(null); // Custom handling or just hide
            
            // Auto respawn strategy:
            // We schedule the handling for next tick to avoid issues during death event
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                p.spigot().respawn();
                arena.handleDeath(p);
            }, 1L);
        }
    }
    
    // In case they manually respawn or some other plugin respawns them
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        Arena arena = plugin.getArenaManager().getArena(p);
        if (arena != null && arena.getState() == ArenaState.RUNNING) {
            // We handle teleportation in handleDeath -> task -> spawnPlayer
            // But if respawn happens before spectator logic, we might need to intercept.
            // With the strategy above (force respawn then handleDeath), 
            // handleDeath sets spectator. So this event will fire before handleDeath completes logic.
            // Let's just set the respawn location to current location to avoid jumping around before spectator.
            event.setRespawnLocation(p.getLocation());
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        Arena arena = plugin.getArenaManager().getArena(victim);
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
        }
    }
}
