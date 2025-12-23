package org.shee33.pvpteam;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PVPTeamCommand implements CommandExecutor {

    private final PVP_Team plugin;

    public PVPTeamCommand(PVP_Team plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        
        if (args.length == 0) {
            help(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        
        switch (sub) {
            case "create":
                if (!player.hasPermission("pvpteam.admin")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /pvpt create <name>");
                    return true;
                }
                plugin.getArenaManager().createArena(args[1]);
                player.sendMessage(ChatColor.GREEN + "Arena " + args[1] + " created!");
                break;
                
            case "setlobby":
                if (!player.hasPermission("pvpteam.admin")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /pvpt setlobby <arena>");
                    return true;
                }
                Arena arenaLobby = plugin.getArenaManager().getArena(args[1]);
                if (arenaLobby == null) {
                    player.sendMessage(ChatColor.RED + "Arena not found.");
                    return true;
                }
                arenaLobby.setLobby(player.getLocation());
                plugin.getArenaManager().saveArena(args[1]);
                player.sendMessage(ChatColor.GREEN + "Lobby set for " + args[1]);
                break;
                
            case "addspawn":
                if (!player.hasPermission("pvpteam.admin")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /pvpt addspawn <arena> <red/blue>");
                    return true;
                }
                Arena arenaSpawn = plugin.getArenaManager().getArena(args[1]);
                if (arenaSpawn == null) {
                    player.sendMessage(ChatColor.RED + "Arena not found.");
                    return true;
                }
                String teamStr = args[2].toLowerCase();
                if (teamStr.equals("red")) {
                    arenaSpawn.addSpawn(TeamType.RED, player.getLocation());
                    player.sendMessage(ChatColor.GREEN + "Added Red spawn.");
                } else if (teamStr.equals("blue")) {
                    arenaSpawn.addSpawn(TeamType.BLUE, player.getLocation());
                    player.sendMessage(ChatColor.GREEN + "Added Blue spawn.");
                } else {
                    player.sendMessage(ChatColor.RED + "Invalid team. Use red or blue.");
                    return true;
                }
                plugin.getArenaManager().saveArena(args[1]);
                break;
                
            case "settime":
                if (!player.hasPermission("pvpteam.admin")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /pvpt settime <arena> <seconds>");
                    return true;
                }
                try {
                    int seconds = Integer.parseInt(args[2]);
                    Arena a = plugin.getArenaManager().getArena(args[1]);
                    if (a == null) {
                        player.sendMessage(ChatColor.RED + "Arena not found.");
                        return true;
                    }
                    a.setDuration(seconds);
                    plugin.getArenaManager().saveArena(args[1]);
                    player.sendMessage(ChatColor.GREEN + "Time set to " + seconds + "s.");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid number.");
                }
                break;
                
            case "setkills":
                if (!player.hasPermission("pvpteam.admin")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /pvpt setkills <arena> <amount>");
                    return true;
                }
                try {
                    int kills = Integer.parseInt(args[2]);
                    Arena a = plugin.getArenaManager().getArena(args[1]);
                    if (a == null) {
                        player.sendMessage(ChatColor.RED + "Arena not found.");
                        return true;
                    }
                    a.setTargetKills(kills);
                    plugin.getArenaManager().saveArena(args[1]);
                    player.sendMessage(ChatColor.GREEN + "Target kills set to " + kills + ".");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid number.");
                }
                break;
                
            case "setmin":
                if (!player.hasPermission("pvpteam.admin")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /pvpt setmin <arena> <amount>");
                    return true;
                }
                try {
                    int min = Integer.parseInt(args[2]);
                    Arena a = plugin.getArenaManager().getArena(args[1]);
                    if (a == null) {
                        player.sendMessage(ChatColor.RED + "Arena not found.");
                        return true;
                    }
                    a.setMinPlayers(min);
                    plugin.getArenaManager().saveArena(args[1]);
                    player.sendMessage(ChatColor.GREEN + "Min players set to " + min + ".");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid number.");
                }
                break;
                
            case "join":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /pvpt join <arena>");
                    return true;
                }
                Arena arenaJoin = plugin.getArenaManager().getArena(args[1]);
                if (arenaJoin == null) {
                    player.sendMessage(ChatColor.RED + "Arena not found.");
                    return true;
                }
                if (!arenaJoin.isConfigured()) {
                     player.sendMessage(ChatColor.RED + "Arena is not fully configured yet.");
                     return true;
                }
                arenaJoin.join(player);
                break;
                
            case "leave":
                Arena current = plugin.getArenaManager().getArena(player);
                if (current != null) {
                    current.quit(player);
                } else {
                    player.sendMessage(ChatColor.RED + "You are not in a game.");
                }
                break;
                
            default:
                help(player);
                break;
        }

        return true;
    }
    
    private void help(Player p) {
        p.sendMessage(ChatColor.GOLD + "PVP Team Commands:");
        p.sendMessage("/pvpt join <arena>");
        p.sendMessage("/pvpt leave");
        if (p.hasPermission("pvpteam.admin")) {
            p.sendMessage("/pvpt create <name>");
            p.sendMessage("/pvpt setlobby <arena>");
            p.sendMessage("/pvpt addspawn <arena> <red/blue>");
            p.sendMessage("/pvpt settime <arena> <seconds>");
            p.sendMessage("/pvpt setkills <arena> <amount>");
            p.sendMessage("/pvpt setmin <arena> <amount>");
        }
    }
}
