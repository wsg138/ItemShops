package dev.enthusia.itemshops.service;

import dev.enthusia.itemshops.config.ItemShopsConfig;
import dev.enthusia.itemshops.util.Pos;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;

public final class ShopLocator {
    private final ItemShopsConfig config;

    public ShopLocator(ItemShopsConfig config) {
        this.config = config;
    }

    public boolean isAllowedContainer(Block block) {
        return block != null
                && block.getState() instanceof Container
                && config.allowedContainers().contains(block.getType());
    }

    public Pos unifyContainerPos(Block containerBlock) {
        if (containerBlock == null || !(containerBlock.getState() instanceof Container)) {
            return Pos.of(containerBlock.getLocation());
        }
        if (containerBlock.getState() instanceof org.bukkit.block.Chest chest) {
            if (chest.getInventory() instanceof DoubleChestInventory dci) {
                var left = ((Container) dci.getLeftSide().getHolder()).getBlock().getLocation();
                var right = ((Container) dci.getRightSide().getHolder()).getBlock().getLocation();
                Pos pl = Pos.of(left);
                Pos pr = Pos.of(right);
                int cmp = pl.world.compareTo(pr.world);
                if (cmp != 0) return cmp < 0 ? pl : pr;
                if (pl.x != pr.x) return pl.x < pr.x ? pl : pr;
                if (pl.y != pr.y) return pl.y < pr.y ? pl : pr;
                return pl.z <= pr.z ? pl : pr;
            }
        }
        return Pos.of(containerBlock.getLocation());
    }

    public Inventory getLinkedInventory(Block containerBlock) {
        if (!(containerBlock.getState() instanceof Container container)) return null;
        return container.getInventory();
    }

    public Block findAttachedContainer(Sign sign) {
        Block signBlock = sign.getBlock();
        if (sign.getBlockData() instanceof WallSign wall) {
            BlockFace attachedTo = wall.getFacing().getOppositeFace();
            Block possible = signBlock.getRelative(attachedTo);
            if (isAllowedContainer(possible)) return possible;
        }
        Block below = signBlock.getRelative(BlockFace.DOWN);
        if (isAllowedContainer(below)) return below;
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block nearby = signBlock.getRelative(face);
            if (isAllowedContainer(nearby)) return nearby;
        }
        return null;
    }
}
