package org.shee33.pvpteam;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class Utils {
    public static String locToString(Location loc) {
        if (loc == null) return null;
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch();
    }

    public static Location stringToLoc(String str) {
        if (str == null) return null;
        String[] parts = str.split(",");
        if (parts.length < 6) return null;
        World w = Bukkit.getWorld(parts[0]);
        if (w == null) return null;
        double x = Double.parseDouble(parts[1]);
        double y = Double.parseDouble(parts[2]);
        double z = Double.parseDouble(parts[3]);
        float yaw = Float.parseFloat(parts[4]);
        float pitch = Float.parseFloat(parts[5]);
        return new Location(w, x, y, z, yaw, pitch);
    }
}
