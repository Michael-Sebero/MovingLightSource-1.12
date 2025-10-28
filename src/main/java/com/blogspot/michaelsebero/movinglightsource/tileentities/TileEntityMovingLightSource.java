package com.blogspot.michaelsebero.movinglightsource.tileentities;

import com.blogspot.michaelsebero.movinglightsource.blocks.BlockMovingLightSource;
import com.blogspot.michaelsebero.movinglightsource.utilities.Utilities;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Improved tile entity with faster cleanup and dynamic light support
 */
public class TileEntityMovingLightSource extends TileEntity implements ITickable
{
    private EntityLivingBase theEntityLiving;
    private EntityItem trackedItem;
    private boolean isItemLight = false;
    private boolean shouldDie = false;
    private int deathTimer = 1; // REDUCED from 2 to 1 for faster cleanup
    private int tickCounter = 0;
    private static final int UPDATE_FREQUENCY = 3; // REDUCED from 5 to 3 for more responsive updates
    private static final double MAX_DISTANCE_SQ = 5.0D;
    private static final double ITEM_MAX_DISTANCE_SQ = 3.0D;
    
    public TileEntityMovingLightSource()
    {
        // Constructor
    }
 
    @Override
    public void update()
    {
        // Performance: Only check every N ticks (unless dying)
        tickCounter++;
        if (tickCounter < UPDATE_FREQUENCY && !shouldDie)
        {
            return;
        }
        tickCounter = 0;
        
        // Check if already dying - immediate cleanup when dying
        if (shouldDie)
        {
            if (deathTimer > 0)
            {
                deathTimer--;
                return;
            }
            else
            {
                if (world != null && !world.isRemote)
                {
                    world.setBlockToAir(getPos());
                }
                return;
            }
        }
        
        // Early return if world is null
        if (world == null)
        {
            return;
        }
        
        // Handle item lights separately
        if (isItemLight || trackedItem != null)
        {
            updateItemLight();
            return;
        }
        
        // Handle living entity lights
        updateLivingEntityLight();
    }
    
    /**
     * Update logic for EntityItem lights
     */
    private void updateItemLight()
    {
        // If marked as item light but no tracked item, try to find one nearby
        if (trackedItem == null || trackedItem.isDead)
        {
            trackedItem = findNearbyLightItem();
            
            // If still no item found, this light should die immediately
            if (trackedItem == null)
            {
                Block blockAtLocation = world.getBlockState(getPos()).getBlock();
                if (blockAtLocation instanceof BlockMovingLightSource)
                {
                    shouldDie = true;
                    deathTimer = 0; // Die immediately for items
                }
                return;
            }
        }
        
        // Check if item moved too far
        double distanceSquared = getDistanceSqToEntity(trackedItem);
        if (distanceSquared > ITEM_MAX_DISTANCE_SQ)
        {
            Block blockAtLocation = world.getBlockState(getPos()).getBlock();
            if (blockAtLocation instanceof BlockMovingLightSource)
            {
                shouldDie = true;
                deathTimer = 0; // Die immediately for items
            }
            return;
        }
        
        // Check if item still emits light using the improved detection
        if (trackedItem.getItem().isEmpty() || !isItemEmittingLight(trackedItem))
        {
            Block blockAtLocation = world.getBlockState(getPos()).getBlock();
            if (blockAtLocation instanceof BlockMovingLightSource)
            {
                shouldDie = true;
                deathTimer = 0; // Die immediately for items
            }
        }
    }
    
    /**
     * Check if an EntityItem emits light (using dynamic detection)
     */
    private boolean isItemEmittingLight(EntityItem entityItem)
    {
        if (entityItem == null || entityItem.getItem().isEmpty())
        {
            return false;
        }
        
        // Use the improved dynamic detection from BlockMovingLightSource
        // This will work for any mod's light-emitting items
        return BlockMovingLightSource.LIGHT_SOURCE_MAP.containsKey(entityItem.getItem().getItem()) ||
               getLightLevelForItem(entityItem.getItem().getItem()) > 0;
    }
    
    /**
     * Get dynamic light level for an item
     */
    private int getLightLevelForItem(net.minecraft.item.Item item)
    {
        if (item instanceof ItemBlock)
        {
            ItemBlock itemBlock = (ItemBlock) item;
            Block block = itemBlock.getBlock();
            
            if (block != null && block != net.minecraft.init.Blocks.AIR)
            {
                return block.getLightValue(block.getDefaultState());
            }
        }
        
        return 0;
    }
    
    /**
     * Update logic for EntityLivingBase lights
     */
    private void updateLivingEntityLight()
    {
        Block blockAtLocation = world.getBlockState(getPos()).getBlock();
        
        // Clean up if entity is null
        if (theEntityLiving == null)
        {
            if (blockAtLocation instanceof BlockMovingLightSource)
            {
                shouldDie = true;
            }
            return;
        }
        
        // Clean up if entity is dead
        if (theEntityLiving.isDead)
        {
            if (blockAtLocation instanceof BlockMovingLightSource)
            {
                shouldDie = true;
            }
            return;
        }

        // Check if entity moved too far
        double distanceSquared = getDistanceSqToEntity(theEntityLiving);
        if (distanceSquared > MAX_DISTANCE_SQ) 
        {
            if (blockAtLocation instanceof BlockMovingLightSource)
            {
                shouldDie = true;
            }
            return;
        }
        
        // Handle entity not burning and not holding light item
        if (!theEntityLiving.isBurning())
        {
            if (!BlockMovingLightSource.isHoldingLightItem(theEntityLiving))
            {
                if (blockAtLocation instanceof BlockMovingLightSource)
                {
                    shouldDie = true;
                }
            }
            else 
            {
                // Handle light level changes - now supports dynamic light levels
                Block expectedBlock = BlockMovingLightSource.lightBlockToPlace(theEntityLiving);
                if (blockAtLocation != expectedBlock)
                {
                    shouldDie = true;
                }
            }
        }
    }
    
    /**
     * Find nearby EntityItem that emits light (using dynamic detection)
     */
    private EntityItem findNearbyLightItem()
    {
        if (world == null) return null;
        
        AxisAlignedBB searchBox = new AxisAlignedBB(
            pos.getX() - 2, pos.getY() - 2, pos.getZ() - 2,
            pos.getX() + 3, pos.getY() + 3, pos.getZ() + 3
        );
        
        List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, searchBox);
        
        for (EntityItem item : items)
        {
            if (!item.isDead && !item.getItem().isEmpty())
            {
                // Use dynamic light detection
                if (isItemEmittingLight(item))
                {
                    double distSq = getDistanceSqToEntity(item);
                    if (distSq <= ITEM_MAX_DISTANCE_SQ)
                    {
                        return item;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Optimized distance calculation
     */
    private double getDistanceSqToEntity(Entity entity)
    {
        double dx = entity.posX - (pos.getX() + 0.5D);
        double dy = entity.posY - pos.getY();
        double dz = entity.posZ - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }
    
    public void setEntityLiving(EntityLivingBase parEntityLiving)
    {
        theEntityLiving = parEntityLiving;
        isItemLight = false;
        trackedItem = null;
    }
    
    public EntityLivingBase getEntityLiving()
    {
        return theEntityLiving;
    }
    
    /**
     * Set this tile entity to track an EntityItem instead of EntityLivingBase
     */
    public void setTrackedItem(EntityItem item)
    {
        trackedItem = item;
        isItemLight = true;
        theEntityLiving = null;
    }
    
    /**
     * Mark this as an item light
     */
    public void markAsItemLight()
    {
        isItemLight = true;
    }

    @Override
    public void setPos(BlockPos posIn)
    {
        super.setPos(posIn);
        
        if (world != null)
        {
            // First try to find a living entity
            setEntityLiving(Utilities.getClosestEntityLiving(world, pos, 2.0D));
            
            // If no living entity found, try to find an item
            if (theEntityLiving == null)
            {
                EntityItem nearbyItem = findNearbyLightItem();
                if (nearbyItem != null)
                {
                    setTrackedItem(nearbyItem);
                }
            }
        }
    }
    
    @Override
    public void invalidate()
    {
        super.invalidate();
        theEntityLiving = null;
        trackedItem = null;
    }
}
