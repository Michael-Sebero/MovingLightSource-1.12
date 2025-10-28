/**
    Copyright (C) 2015 by jabelar

    This file is part of jabelar's Minecraft Forge modding examples; as such,
    you can redistribute it and/or modify it under the terms of the GNU
    General Public License as published by the Free Software Foundation,
    either version 3 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    For a copy of the GNU General Public License see <http://www.gnu.org/licenses/>.
*/

package com.blogspot.michaelsebero.movinglightsource.blocks;

import java.util.HashMap;

import com.blogspot.michaelsebero.movinglightsource.registries.BlockRegistry;
import com.blogspot.michaelsebero.movinglightsource.tileentities.TileEntityMovingLightSource;
import com.blogspot.michaelsebero.movinglightsource.utilities.Utilities;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author jabelar
 * Improved version with dynamic light detection for ALL light-emitting blocks
 */
public class BlockMovingLightSource extends Block implements ITileEntityProvider
{
    // Static mappings for known vanilla light sources (for optimization)
    public static final HashMap<Item, Block> LIGHT_SOURCE_MAP = new HashMap<>();
    
    private static final AxisAlignedBB BOUNDING_BOX = new AxisAlignedBB(0.5D, 0.5D, 0.5D, 0.5D, 0.5D, 0.5D);

    public BlockMovingLightSource(String parName)
    {
        super(Material.AIR);
        Utilities.setBlockName(this, parName);
        setDefaultState(blockState.getBaseState());
        setTickRandomly(false);
        setLightLevel(1.0F);
    }
    
    // Initialize light source mappings - call only after all items/blocks registered
    public static void initMapLightSources()
    {
        LIGHT_SOURCE_MAP.clear();
        
        // Add vanilla light-emitting items for quick lookup
        addLightSource(Item.getItemFromBlock(Blocks.BEACON), BlockRegistry.MOVING_LIGHT_SOURCE_15);
        addLightSource(Item.getItemFromBlock(Blocks.LIT_PUMPKIN), BlockRegistry.MOVING_LIGHT_SOURCE_15);
        addLightSource(Items.LAVA_BUCKET, BlockRegistry.MOVING_LIGHT_SOURCE_15);
        addLightSource(Item.getItemFromBlock(Blocks.GLOWSTONE), BlockRegistry.MOVING_LIGHT_SOURCE_15);
        addLightSource(Items.GLOWSTONE_DUST, BlockRegistry.MOVING_LIGHT_SOURCE_15);
        addLightSource(Item.getItemFromBlock(Blocks.SEA_LANTERN), BlockRegistry.MOVING_LIGHT_SOURCE_15);
        addLightSource(Item.getItemFromBlock(Blocks.END_ROD), BlockRegistry.MOVING_LIGHT_SOURCE_14);
        addLightSource(Item.getItemFromBlock(Blocks.TORCH), BlockRegistry.MOVING_LIGHT_SOURCE_14);
        addLightSource(Item.getItemFromBlock(Blocks.REDSTONE_TORCH), BlockRegistry.MOVING_LIGHT_SOURCE_9);
        addLightSource(Item.getItemFromBlock(Blocks.REDSTONE_ORE), BlockRegistry.MOVING_LIGHT_SOURCE_7);
        
        LIGHT_SOURCE_MAP.remove(Items.AIR);
        
        System.out.println("[MovingLightSource] Registered " + LIGHT_SOURCE_MAP.size() + " vanilla light-emitting items");
        System.out.println("[MovingLightSource] Dynamic detection enabled for modded light sources");
    }
    
    private static void addLightSource(Item item, Block block)
    {
        if (item != null && item != Items.AIR && block != null)
        {
            LIGHT_SOURCE_MAP.put(item, block);
        }
    }

    public BlockMovingLightSource(String parName, float parLightLevel)
    {
        this(parName);
        setLightLevel(parLightLevel);
    }
    
    /**
     * Get the light level an ItemStack emits (works for ANY mod's items)
     * This is the KEY method that makes modded items work!
     */
    public static int getItemLightLevel(ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return 0;
        }
        
        Item item = stack.getItem();
        
        // First check if it's an ItemBlock with a light-emitting block
        if (item instanceof ItemBlock)
        {
            ItemBlock itemBlock = (ItemBlock) item;
            Block block = itemBlock.getBlock();
            
            if (block != null && block != Blocks.AIR)
            {
                // This will work for ANY modded block that emits light!
                int lightLevel = block.getLightValue(block.getDefaultState());
                if (lightLevel > 0)
                {
                    return lightLevel;
                }
            }
        }
        
        // Check static map for special items (like lava bucket, glowstone dust)
        Block staticBlock = LIGHT_SOURCE_MAP.get(item);
        if (staticBlock != null)
        {
            return staticBlock.getLightValue(staticBlock.getDefaultState());
        }
        
        return 0;
    }
    
    /**
     * Get the appropriate moving light block for a given light level
     * PUBLIC so EventHandler can use it for dropped items
     */
    public static Block getLightBlockForLevel(int lightLevel)
    {
        if (lightLevel >= 15)
        {
            return BlockRegistry.MOVING_LIGHT_SOURCE_15;
        }
        else if (lightLevel >= 14)
        {
            return BlockRegistry.MOVING_LIGHT_SOURCE_14;
        }
        else if (lightLevel >= 9)
        {
            return BlockRegistry.MOVING_LIGHT_SOURCE_9;
        }
        else if (lightLevel >= 7)
        {
            return BlockRegistry.MOVING_LIGHT_SOURCE_7;
        }
        else if (lightLevel > 0)
        {
            // For any other positive light level, use the closest available
            return BlockRegistry.MOVING_LIGHT_SOURCE_7;
        }
        
        return Blocks.AIR;
    }
    
    /**
     * Check if entity is holding a light-emitting item in either hand
     * NOW WORKS WITH ALL MODS!
     */
    public static boolean isHoldingLightItem(EntityLivingBase entity)
    {
        if (entity == null) return false;
        
        ItemStack mainHand = entity.getHeldItemMainhand();
        ItemStack offHand = entity.getHeldItemOffhand();
        
        return getItemLightLevel(mainHand) > 0 || getItemLightLevel(offHand) > 0;
    }
    
    /**
     * Determine which light block to place based on held items
     * NOW WORKS WITH ALL MODS!
     */
    public static Block lightBlockToPlace(EntityLivingBase entity)
    {
        if (entity == null)
        {
            return Blocks.AIR;
        }
        
        ItemStack mainHand = entity.getHeldItemMainhand();
        ItemStack offHand = entity.getHeldItemOffhand();
        
        // Get light levels for both hands
        int mainHandLight = getItemLightLevel(mainHand);
        int offHandLight = getItemLightLevel(offHand);
        
        // Use the higher light level
        int maxLight = Math.max(mainHandLight, offHandLight);
        
        return getLightBlockForLevel(maxLight);
    }
    
    /**
     * Clear cache - called when needed (e.g., dimension change, world unload)
     */
    public static void clearCache()
    {
        // This method exists for compatibility with EventHandler
        // No actual cache to clear in this simplified version
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess worldIn, BlockPos pos)
    {
        return BOUNDING_BOX;
    }
    
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState blockState, IBlockAccess worldIn, BlockPos pos)
    {
        return NULL_AABB;
    }

    @Override
    public boolean canCollideCheck(IBlockState state, boolean hitIfLiquid)
    {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state)
    {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state)
    {
        return false;
    }

    @Override
    public boolean canPlaceBlockAt(World worldIn, BlockPos pos)
    {
        return true;
    }

    @Override
    public void onBlockAdded(World worldIn, BlockPos pos, IBlockState state)
    {
        // Intentionally empty
    }

    @Override
    public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos)
    {
        // Intentionally empty
    }

    @Override
    public IBlockState getStateFromMeta(int meta)
    {
        return getDefaultState();
    }

    @Override
    public int getMetaFromState(IBlockState state)
    {
        return 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getBlockLayer()
    {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public void onFallenUpon(World worldIn, BlockPos pos, Entity entityIn, float fallDistance)
    {
        // Entities should fall through this block
    }

    @Override
    public void onLanded(World worldIn, Entity entityIn)
    {
        // No landing effects
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta)
    {
        return new TileEntityMovingLightSource();
    }
    
    @Override
    public boolean hasTileEntity(IBlockState state)
    {
        return true;
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockState blockState, IBlockAccess blockAccess, BlockPos pos, EnumFacing side)
    {
        return false;
    }
}
