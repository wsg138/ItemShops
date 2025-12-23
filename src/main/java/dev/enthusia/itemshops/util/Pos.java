package dev.enthusia.itemshops.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

public final class Pos {
    public final String world; public final int x,y,z;

    public Pos(String world, int x, int y, int z) {
        this.world = world; this.x=x; this.y=y; this.z=z;
    }

    public static Pos of(Location loc) {
        return new Pos(Objects.requireNonNull(loc.getWorld()).getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        return (w == null) ? null : new Location(w, x, y, z);
    }

    @Override public boolean equals(Object o){
        if (this==o) return true;
        if (!(o instanceof Pos)) return false;
        Pos p=(Pos)o; return x==p.x && y==p.y && z==p.z && java.util.Objects.equals(world,p.world);
    }
    @Override public int hashCode(){ return java.util.Objects.hash(world,x,y,z);}
    @Override public String toString(){ return world+"@"+x+","+y+","+z; }
}
