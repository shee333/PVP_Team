package org.shee33.pvpteam;

import org.bukkit.ChatColor;
import org.bukkit.Color;

public enum TeamType {
    RED("红队", ChatColor.RED, Color.RED),
    BLUE("蓝队", ChatColor.BLUE, Color.BLUE);

    private final String name;
    private final ChatColor chatColor;
    private final Color color;

    TeamType(String name, ChatColor chatColor, Color color) {
        this.name = name;
        this.chatColor = chatColor;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public ChatColor getChatColor() {
        return chatColor;
    }
    
    public Color getColor() {
        return color;
    }
}
