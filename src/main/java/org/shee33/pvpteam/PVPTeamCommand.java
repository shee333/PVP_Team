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
            sender.sendMessage(ChatColor.RED + "只有玩家可以使用此命令。");
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
                    player.sendMessage(ChatColor.RED + "没有权限。");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "用法：/pvpt create <名称>");
                    return true;
                }
                plugin.getArenaManager().createArena(args[1]);
                player.sendMessage(ChatColor.GREEN + "竞技场 " + args[1] + " 已创建！");
                break;
                
            case "setlobby":
                if (!player.hasPermission("pvpteam.admin")) {
                    player.sendMessage(ChatColor.RED + "没有权限。");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "用法：/pvpt setlobby <竞技场>");
                    return true;
                }
                Arena arenaLobby = plugin.getArenaManager().getArena(args[1]);
                if (arenaLobby == null) {
                    player.sendMessage(ChatColor.RED + "未找到竞技场。");
                    return true;
                }
                arenaLobby.setLobby(player.getLocation());
                plugin.getArenaManager().saveArena(args[1]);
                player.sendMessage(ChatColor.GREEN + "已设置 " + args[1] + " 的大厅。");
                break;
                
            case "addspawn":
                if (!player.hasPermission("pvpteam.admin")) {
                    player.sendMessage(ChatColor.RED + "没有权限。");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "用法：/pvpt addspawn <竞技场> <red/blue>");
                    return true;
                }
                Arena arenaSpawn = plugin.getArenaManager().getArena(args[1]);
                if (arenaSpawn == null) {
                    player.sendMessage(ChatColor.RED + "未找到竞技场。");
                    return true;
                }
                String teamStr = args[2].toLowerCase();
                if (teamStr.equals("red")) {
                    arenaSpawn.addSpawn(TeamType.RED, player.getLocation());
                    player.sendMessage(ChatColor.GREEN + "已添加红队出生点。");
                } else if (teamStr.equals("blue")) {
                    arenaSpawn.addSpawn(TeamType.BLUE, player.getLocation());
                    player.sendMessage(ChatColor.GREEN + "已添加蓝队出生点。");
                } else {
                    player.sendMessage(ChatColor.RED + "无效队伍。请使用 red 或 blue。");
                    return true;
                }
                plugin.getArenaManager().saveArena(args[1]);
                break;
                
            case "settime":
                if (!player.hasPermission("pvpteam.admin")) {
                    player.sendMessage(ChatColor.RED + "没有权限。");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "用法：/pvpt settime <竞技场> <秒数>");
                    return true;
                }
                try {
                    int seconds = Integer.parseInt(args[2]);
                    Arena a = plugin.getArenaManager().getArena(args[1]);
                    if (a == null) {
                        player.sendMessage(ChatColor.RED + "未找到竞技场。");
                        return true;
                    }
                    a.setDuration(seconds);
                    plugin.getArenaManager().saveArena(args[1]);
                    player.sendMessage(ChatColor.GREEN + "时间已设置为 " + seconds + " 秒。");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "无效数字。");
                }
                break;
                
            case "setkills":
                if (!player.hasPermission("pvpteam.admin")) {
                    player.sendMessage(ChatColor.RED + "没有权限。");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "用法：/pvpt setkills <竞技场> <数量>");
                    return true;
                }
                try {
                    int kills = Integer.parseInt(args[2]);
                    Arena a = plugin.getArenaManager().getArena(args[1]);
                    if (a == null) {
                        player.sendMessage(ChatColor.RED + "未找到竞技场。");
                        return true;
                    }
                    a.setTargetKills(kills);
                    plugin.getArenaManager().saveArena(args[1]);
                    player.sendMessage(ChatColor.GREEN + "目标击杀数已设置为 " + kills + "。");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "无效数字。");
                }
                break;
                
            case "setmin":
                if (!player.hasPermission("pvpteam.admin")) {
                    player.sendMessage(ChatColor.RED + "没有权限。");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "用法：/pvpt setmin <竞技场> <数量>");
                    return true;
                }
                try {
                    int min = Integer.parseInt(args[2]);
                    Arena a = plugin.getArenaManager().getArena(args[1]);
                    if (a == null) {
                        player.sendMessage(ChatColor.RED + "未找到竞技场。");
                        return true;
                    }
                    a.setMinPlayers(min);
                    plugin.getArenaManager().saveArena(args[1]);
                    player.sendMessage(ChatColor.GREEN + "最小人数已设置为 " + min + "。");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "无效数字。");
                }
                break;
                
            case "join":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "用法：/pvpt join <竞技场>");
                    return true;
                }
                Arena arenaJoin = plugin.getArenaManager().getArena(args[1]);
                if (arenaJoin == null) {
                    player.sendMessage(ChatColor.RED + "未找到竞技场。");
                    return true;
                }
                if (!arenaJoin.isConfigured()) {
                     player.sendMessage(ChatColor.RED + "竞技场尚未配置完成。");
                     return true;
                }
                arenaJoin.join(player);
                break;
                
            case "leave":
                Arena current = plugin.getArenaManager().getArena(player);
                if (current != null) {
                    current.quit(player);
                } else {
                    player.sendMessage(ChatColor.RED + "你不在游戏中。");
                }
                break;
                
            default:
                help(player);
                break;
        }

        return true;
    }
    
    private void help(Player p) {
        p.sendMessage(ChatColor.GOLD + "PVP 团队指令：");
        p.sendMessage("/pvpt join <竞技场>");
        p.sendMessage("/pvpt leave");
        if (p.hasPermission("pvpteam.admin")) {
            p.sendMessage("/pvpt create <名称>");
            p.sendMessage("/pvpt setlobby <竞技场>");
            p.sendMessage("/pvpt addspawn <竞技场> <red/blue>");
            p.sendMessage("/pvpt settime <竞技场> <秒数>");
            p.sendMessage("/pvpt setkills <竞技场> <数量>");
            p.sendMessage("/pvpt setmin <竞技场> <数量>");
        }
    }
}
