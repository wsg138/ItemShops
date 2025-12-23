package dev.enthusia.itemshops.model;

import dev.enthusia.itemshops.util.Pos;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public final class Shop {
    private Pos sign;              
    private final Pos container;   
    private final UUID owner;

    private final ReentrantLock lock = new ReentrantLock();

    private ItemStack sell;
    private ItemStack cost;

    private final Set<UUID> trusted = new HashSet<>();
    private boolean hopperAllowIn;
    private boolean hopperAllowOut;

    
    private boolean searchEnabled;
    private boolean frozen;
    private long frozenUntilMs;

    public Shop(Pos sign, Pos container, UUID owner, ItemStack sell, ItemStack cost) {
        this.sign = sign;
        this.container = container;
        this.owner = owner;
        this.sell = sell;
        this.cost = cost;
        this.hopperAllowIn  = false;
        this.hopperAllowOut = false;
        this.searchEnabled  = true; 
        this.frozen = false;
        this.frozenUntilMs = 0L;
    }

    public ReentrantLock lock(){ return lock; }

    public Pos sign(){ return sign; }
    public void setSign(Pos p){ this.sign = p; }

    public Pos container(){ return container; }
    public UUID owner(){ return owner; }

    public ItemStack sell(){ return sell; }
    public ItemStack cost(){ return cost; }

    public void setSell(ItemStack s){ this.sell = s; }
    public void setCost(ItemStack c){ this.cost = c; }

    public Set<UUID> trusted(){ return trusted; }
    public void addTrusted(UUID u){ trusted.add(u); }
    public void removeTrusted(UUID u){ trusted.remove(u); }

    public boolean isHopperAllowIn(){ return hopperAllowIn; }
    public boolean isHopperAllowOut(){ return hopperAllowOut; }
    public void setHopperAllowIn(boolean v){ hopperAllowIn = v; }
    public void setHopperAllowOut(boolean v){ hopperAllowOut = v; }

    
    public boolean isSearchEnabled(){ return searchEnabled; }
    public void setSearchEnabled(boolean v){ searchEnabled = v; }

    public boolean isFrozen(){ return isFrozen(System.currentTimeMillis()); }
    public boolean isFrozen(long nowMs){
        if (!frozen) return false;
        if (frozenUntilMs > 0 && nowMs > frozenUntilMs) return false;
        return true;
    }
    public boolean clearIfFreezeExpired(long nowMs){
        if (frozen && frozenUntilMs > 0 && nowMs > frozenUntilMs) {
            frozen = false;
            frozenUntilMs = 0L;
            return true;
        }
        return false;
    }
    public void freezeIndefinitely(){ frozen = true; frozenUntilMs = 0L; }
    public void freezeUntil(long untilMs){ frozen = true; frozenUntilMs = untilMs; }
    public void unfreeze(){ frozen = false; frozenUntilMs = 0L; }
    public long frozenUntilMs(){ return frozenUntilMs; }
}
