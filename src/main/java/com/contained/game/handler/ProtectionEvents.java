package com.contained.game.handler;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import codechicken.lib.vec.BlockCoord;

import com.contained.game.Contained;
import com.contained.game.Settings;
import com.contained.game.data.Data;
import com.contained.game.user.PlayerTeam;
import com.contained.game.user.PlayerTeamIndividual;
import com.contained.game.user.PlayerTeamPermission;
import com.contained.game.util.EntityUtil;
import com.contained.game.util.MiniGameUtil;
import com.contained.game.util.Resources;
import com.contained.game.util.Util;
import com.contained.game.world.block.HarvestedOre;
import com.contained.game.world.block.HarvestedOreTE;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAnvil;
import net.minecraft.block.BlockBrewingStand;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockEnchantmentTable;
import net.minecraft.block.BlockFurnace;
import net.minecraft.block.BlockHopper;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.item.EntityMinecartChest;
import net.minecraft.entity.item.EntityMinecartFurnace;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerBrewingStand;
import net.minecraft.inventory.ContainerDispenser;
import net.minecraft.inventory.ContainerEnchantment;
import net.minecraft.inventory.ContainerFurnace;
import net.minecraft.inventory.ContainerHopper;
import net.minecraft.inventory.ContainerHorseInventory;
import net.minecraft.inventory.ContainerRepair;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.StatList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.minecart.MinecartUpdateEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;
import net.minecraftforge.event.entity.player.PlayerOpenContainerEvent;
import net.minecraftforge.event.entity.player.PlayerPickupXpEvent;
import net.minecraftforge.event.world.BlockEvent;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Event handlers
 */
public class ProtectionEvents {	
	
	// Blocks that should not be able to be harvested if not within a team's
	// territory (ie: you cannot harvest them if you're not in a team, and you
	// cannot harvest them if you are in a team, but the block is not in your
	// territory)
	public static Block[] bannedWildernessHarvest = {
		Blocks.iron_ore,
		Blocks.gold_ore,
		Blocks.coal_ore,
		Blocks.diamond_ore,
		Blocks.redstone_ore,
		Blocks.emerald_ore,
		Blocks.lapis_ore,
		Blocks.quartz_block,
		Blocks.glowstone
	};
	
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	//Break Protection
    public void onPlayerBreaksBlock(BlockEvent.BreakEvent ev) {
		boolean shouldCancel = false;
		if (getPermissions(ev.world, ev.getPlayer(), ev.x, ev.y, ev.z).breakDisable) {
			shouldCancel = true;
		}
		else {
			Block b = ev.world.getBlock(ev.x, ev.y, ev.z);
			// Even if break protection is disabled, if chest protection is enabled,
			// don't allow chests to be broken.
			if (b.equals(Blocks.chest)
				&& getPermissions(ev.world, ev.getPlayer(), ev.x, ev.y, ev.z).chestDisable) 
			{
				shouldCancel = true;
			}
			// Same for container protection.
			else if (isProtectedContainerBlock(b)
				&& getPermissions(ev.world, ev.getPlayer(), ev.x, ev.y, ev.z).containerDisable) 
			{
				shouldCancel = true;
			}
		}
		
		if (shouldCancel && !isExempt(ev.world, ev.getPlayer()))
			ev.setCanceled(true);
		else {
			//Is this a wilderness protected block?
			Block check = ev.world.getBlock(ev.x, ev.y, ev.z);
			boolean isSpecial = false;
			for(Block b : bannedWildernessHarvest) {
				if (b.equals(check)) {
					isSpecial = true;
					break;
				}
			}
			
			if (isSpecial && Contained.configs.harvestRequiresTerritory[Settings.getDimConfig(ev.world.provider.dimensionId)]) {
				//This block type needs additional special permission checks.
				PlayerTeamIndividual pdata = PlayerTeamIndividual.get(ev.getPlayer());
				boolean canHarvest = true;
				if (pdata.teamID == null)
					canHarvest = false;
				else {
					String ownedTeam = Contained.getTerritoryMap(ev.getPlayer().dimension).get(new Point(ev.x, ev.z));
					if (ownedTeam == null || !ownedTeam.equals(pdata.teamID))
						canHarvest = false;
				}
				
				if (!canHarvest && !isExempt(ev.world, ev.getPlayer())) {
					Util.displayError(ev.getPlayer(), "This block must be in team-owned territory to be harvested.");
					ev.setCanceled(true);
				}
			}
		}
		
		if (!ev.isCanceled() && !ev.world.isRemote) {
			//Handle special ore harvesting behavior
			Block check = ev.world.getBlock(ev.x, ev.y, ev.z);
			for(Block b : Resources.oreTypes) {
				if (b.equals(check)) {
					ItemStack itemstack = ev.getPlayer().getCurrentEquippedItem();
					if (itemstack != null) {
	                    itemstack.func_150999_a(ev.world, check, ev.x, ev.y, ev.z, ev.getPlayer());
	                    if (itemstack.stackSize == 0)
	                        ev.getPlayer().destroyCurrentEquippedItem();
	                }
					harvestBlock(ev.world, check, ev.getPlayer(), ev.x, ev.y, ev.z);
					check.dropXpOnBlockBreak(ev.world, ev.x, ev.y, ev.z, ev.getExpToDrop());
					
					if (!b.equals(Blocks.obsidian) && Contained.configs.maxOreRegen[Settings.getDimConfig(ev.world.provider.dimensionId)] > 0) {
						ev.world.setBlock(ev.x, ev.y, ev.z, HarvestedOre.instance);
						TileEntity te = ev.world.getTileEntity(ev.x, ev.y, ev.z);
						if (te != null && te instanceof HarvestedOreTE) {
							HarvestedOreTE harvestTE = (HarvestedOreTE)te;
							harvestTE.blockToRespawn = b;
						}
					} else
						ev.world.setBlock(ev.x, ev.y, ev.z, Blocks.air);
					ev.setCanceled(true);
					break;
				}
			}
		}
	}
	
	public static void harvestBlock(World w, Block b, EntityPlayer p, int x, int y, int z) {
		p.addStat(StatList.mineBlockStatArray[Block.getIdFromBlock(b)], 1);
		p.addExhaustion(0.025F);
		int fortune = EnchantmentHelper.getFortuneModifier(p);
		int meta = w.getBlockMetadata(x, y, z);
		ArrayList<ItemStack> items;
		if (b == Blocks.iron_ore || b == Blocks.gold_ore) {
			items = new ArrayList<ItemStack>();
			int count = Blocks.diamond_ore.quantityDropped(meta, fortune, w.rand);
			for(int i=0; i<count; i++) {
				if (b == Blocks.iron_ore)
					items.add(new ItemStack(Items.iron_ingot, 1));
				else if (b == Blocks.gold_ore)
					items.add(new ItemStack(Items.gold_ingot, 1));
			}
		}
		else
			items = b.getDrops(w, x, y, z, meta, fortune);
		for (ItemStack item : items)
			Util.dropBlockAsItem(w, x, y, z, item);
	}
	
	@SubscribeEvent
	public void onMinecartTick(MinecartUpdateEvent ev) {
		EntityMinecart m = ev.minecart;
		if (!m.worldObj.isRemote) {
			// Make minecarts invulnerable to damage unless a trusted player is nearby.
			@SuppressWarnings("unchecked")
			List<EntityPlayer> nearbyPlayers = m.worldObj.getEntitiesWithinAABB(
					EntityPlayer.class, 
					AxisAlignedBB.getBoundingBox(m.posX-4,m.posY-4,m.posZ-4,
												 m.posX+4,m.posY+4,m.posZ+4));
			if (nearbyPlayers.size() == 0)
				Util.setEntityInvulnerability(m, true);
			else {
				boolean foundAllowedPlayer = false;
				for(EntityPlayer p : nearbyPlayers) {
					if (getPermissions(m.worldObj, p, m.posX, m.posY, m.posZ)
							.breakDisable) 
					{
						continue;
					}
					else if (m instanceof EntityMinecartChest
							&& getPermissions(m.worldObj, p, m.posX, m.posY, m.posZ)
							.chestDisable) 
					{
						continue;
					}
					else if (m instanceof EntityMinecartFurnace
							&& getPermissions(m.worldObj, p, m.posX, m.posY, m.posZ)
							.containerDisable) 
					{
						continue;
					}
					foundAllowedPlayer = true;
					break;					
				}
				
				if (!foundAllowedPlayer)
					Util.setEntityInvulnerability(m, true);
				else
					Util.setEntityInvulnerability(m, false);
			}
		}
	}
	
    @SubscribeEvent
    //Place Protection
    public void onBlockPlacement(BlockEvent.PlaceEvent ev) {
    	placeProtection(ev.player, ev);
    }

    @SubscribeEvent
    public void onMultiBlockPlacement(BlockEvent.MultiPlaceEvent ev) {
    	placeProtection(ev.player, ev);
    }
	
	public void placeProtection(EntityPlayer player, BlockEvent.PlaceEvent ev) {
		if (getPermissions(ev.world, player, ev.x, ev.y, ev.z).buildDisable)
			ev.setCanceled(true);
	}
	
	@SubscribeEvent
	//Damage & Death Protection
	public void onEntityDamaged(LivingHurtEvent event) {
		Entity damageSource = event.source.getEntity();
		if (!(damageSource instanceof EntityPlayer)) {
			if (damageSource instanceof EntityCreature) {
				if (EntityUtil.isSameTeam((EntityCreature)damageSource, event.entityLiving))
					event.setCanceled(true);
			}
			return;
		}
		EntityPlayer attacker = (EntityPlayer)damageSource;
		
		if (event.entityLiving instanceof EntityCreature && !(event.entityLiving instanceof EntityPlayer)) {
			if (EntityUtil.isSameTeam(event.entityLiving, attacker)) {
				event.setCanceled(true);
				// Try to teleport the entity somewhere nearby.
				boolean teleportSuccess = false;
				int newPosX = 0, newPosY = 0, newPosZ = 0;
				for(int i=0; i<25; i+=1) {
					newPosX = (int)(event.entityLiving.posX+Util.randomBoth(4));
					newPosY = (int)(event.entityLiving.posY+Util.randomBoth(2));
					newPosZ = (int)(event.entityLiving.posZ+Util.randomBoth(4));
					if (event.entityLiving.worldObj.isAirBlock(newPosX, newPosY, newPosZ) && event.entityLiving.worldObj.isAirBlock(newPosX, newPosY+1, newPosZ))
					{
						event.entityLiving.setPositionAndUpdate(newPosX, newPosY, newPosZ);
						teleportSuccess = true;
						break;
					}
				}
				
				if (!teleportSuccess)
					event.entityLiving.setPositionAndUpdate(newPosX, event.entityLiving.worldObj.getTopSolidOrLiquidBlock(newPosX, newPosZ), newPosZ);
				return;
			}
		}
		
		if (event.entityLiving instanceof EntityMob 
				&& getPermissions(attacker.worldObj, attacker, event.entityLiving.posX, event.entityLiving.posY, event.entityLiving.posZ)
				.mobDisable)
			event.setCanceled(true);
		else if (event.entityLiving != null && event.entityLiving instanceof EntityPlayer) {
			// Player versus Player: Disable PvP in the following cases:
			//	  -Playing the Treasure Hunt mini-game.
			//    -Players cannot attack their own teammates.
			//    -Players not in a team cannot be attacked nor can they attack
			//      other players.
			//    -Players cannot be attacked within their territory if the
			//      territory is still in its "infancy" (determined by config file)
			
			EntityPlayer victim = (EntityPlayer)event.entityLiving;
			String victimTeam = PlayerTeamIndividual.get(victim).teamID;
			String attackerTeam = PlayerTeamIndividual.get(attacker).teamID;
			Point check = new Point((int)event.entityLiving.posX, (int)event.entityLiving.posZ);
			boolean shouldCancel = false;
			
			if (MiniGameUtil.isTreasure(event.entityLiving.dimension))
				shouldCancel = true;
			if (victimTeam == null || attackerTeam == null)
				shouldCancel = true;
			else if (victimTeam.equals(attackerTeam))
				shouldCancel = true;
			else if (Contained.getTerritoryMap(event.entityLiving.dimension).containsKey(check)) {
				String territoryTeamID = Contained.getTerritoryMap(event.entityLiving.dimension).get(check);
				if (victimTeam.equals(territoryTeamID)) {
					PlayerTeam territoryTeam = PlayerTeam.get(territoryTeamID, event.entityLiving.dimension);
					if (territoryTeam.territoryCount() < Contained.configs.largeTeamSize[Settings.getDimConfig(event.entityLiving.dimension)])
						shouldCancel = true;
				}
			}
			
			if (shouldCancel)
				event.setCanceled(true);
		}
		else if (event.entityLiving != null && (event.entityLiving instanceof EntityAnimal 
				|| event.entityLiving instanceof IMerchant
				|| event.entityLiving instanceof EntityGolem)
				&& getPermissions(attacker.worldObj, attacker, event.entityLiving.posX, event.entityLiving.posY, event.entityLiving.posZ)
				.animalDisable)
			event.setCanceled(true);
	}
	
	@SubscribeEvent
	//Entity Interaction Protection
	public void onEntityInteract(EntityInteractEvent ev) {
		if (getPermissions(ev.entity.worldObj, ev.entityPlayer, ev.target.posX, ev.target.posY, ev.target.posZ)
				.interactDisable) 
			ev.setCanceled(true);
	}
	
	@SubscribeEvent
	//Bucket Protection
	public void onBucketFill(FillBucketEvent ev) {
		if (getPermissions(ev.world, ev.entityPlayer, ev.target.blockX, ev.target.blockY, ev.target.blockZ)
				.bucketDisable) 
			ev.setCanceled(true);
	}
	
	@SubscribeEvent
	//Item Pickup Protection
	public void onItemCollect(EntityItemPickupEvent event) {
		event.setCanceled(pickupProtection(event, event.item));
	}
	
	@SubscribeEvent
	public void onEXPCollect(PlayerPickupXpEvent event) {
		event.setCanceled(pickupProtection(event, null));
	}
	
	public boolean pickupProtection(PlayerEvent ev, EntityItem item) {
		if (getPermissions(ev.entityPlayer.worldObj, ev.entityPlayer, ev.entity.posX, ev.entity.posY, ev.entity.posZ)
				.itemDisable) {
			if (item != null) {
				// If this item belongs to the person trying to pick it up, allow them
				// to, regardless of territory permissions.
				NBTTagCompound ntc = Data.getTagCompound(item.getEntityItem());
				if (ntc.hasKey("owner") && ntc.getString("owner").equals(ev.entityPlayer.getDisplayName()))
					return false;
			}
			return true;
		}
		return false;
	}
	
	@SubscribeEvent
	//Harvest handling & Container Interaction
	public void onBlockInteract(PlayerInteractEvent ev) {
		if (ev.action == Action.RIGHT_CLICK_BLOCK && !ev.world.isRemote) {
			Block b = ev.world.getBlock(ev.x, ev.y, ev.z);
			if (b != null && b instanceof BlockCrops) {
				if (getPermissions(ev.world, ev.entityPlayer, ev.x, ev.y, ev.z)
						.harvestDisable) 
					return;
				else {
					BlockCrops crop = (BlockCrops)b;
					int meta = ev.world.getBlockMetadata(ev.x, ev.y, ev.z);
					if (meta == 7) {
						//Crop is fully grown; revert to seedling & drop item
						ev.world.setBlockMetadataWithNotify(ev.x, ev.y, ev.z, 0, 2);
						ev.world.spawnEntityInWorld(new EntityItem(ev.world, ev.x, ev.y+1, ev.z, 
								new ItemStack(crop.getItemDropped(meta, ev.world.rand, 0), 1)));
					}
				}
			}
			if (b != null && isProtectedContainerBlock(b) && getPermissions(ev.entityPlayer.worldObj, ev.entityPlayer, ev.x, ev.y, ev.z)
					.containerDisable)
				ev.setResult(Event.Result.DENY);
			if (b != null && b instanceof BlockChest && getPermissions(ev.entityPlayer.worldObj, ev.entityPlayer, ev.x, ev.y, ev.z)
					.chestDisable) {
				// Protect chest UNLESS we're in the treasure mini-game and this chest
				// is one of the auto-spawned ones.
				if (!MiniGameUtil.isTreasure(ev.entityPlayer.dimension)
						|| !Contained.getActiveTreasures(ev.entityPlayer.dimension).contains(new BlockCoord(ev.x, ev.y, ev.z)))
					ev.setResult(Event.Result.DENY);
			}
		}
	}
	
	@SubscribeEvent
	//Container Protection
	public void onContainerOpen(PlayerOpenContainerEvent ev) {
		Container c = ev.entityPlayer.openContainer;
		if (c != null && isProtectedContainer(c)
				&& getPermissions(ev.entityPlayer.worldObj, ev.entityPlayer, ev.entity.posX, ev.entity.posY, ev.entity.posZ)
				.containerDisable) 
			ev.setResult(Event.Result.DENY);
	}
	
	/**
	 * Returns the territory permissions that the given entity has at the block of land they
	 * are currently occupying.
	 * 
	 * @param ent The entity performing the action
	 * @param x   The x coordinate of the block/entity being affected by the action
	 * @param y   The y coordinate of the block/entity being affected by the action
	 * @param z   The z coordinate of the block/entity being affected by the action
	 * @return
	 */
	public static PlayerTeamPermission getPermissions(World w, EntityPlayer ent, double x, double y, double z) {	
		if (ent != null && !isExempt(w, ent)) {
			Point check = new Point((int)x, (int)z);
			if (Contained.getTerritoryMap(ent.dimension).containsKey(check)) {
				//This player is in owned territory.
				PlayerTeamIndividual entData = PlayerTeamIndividual.get(ent);
				PlayerTeam team = PlayerTeam.get(Contained.getTerritoryMap(ent.dimension).get(check), ent.dimension);
				return team.getPermissions(entData.teamID);  
			}
		}
		
		//This player is not inside owned territory.
		PlayerTeamPermission returnPerm = new PlayerTeamPermission();
		returnPerm.setAllowAll();
		return returnPerm; 
	}
	
	/**
	 * Should this entity be exempt from the protection rules?
	 */
	public static boolean isExempt(World w, EntityPlayer ent) {
		if (w.isRemote)
			return true;
		if (ent == null)
			return true;
		if (ent.capabilities.isCreativeMode && Contained.configs.creativeOverride)
			return true;
		return false;
	}
	
	/**
	 * Does this constitute something that we consider to fall into the
	 * category of containers that should be protected by the containerDisable
	 * rule?
	 */
	public static boolean isProtectedContainer(Container c) {
		if (c instanceof ContainerDispenser
				|| c instanceof ContainerBrewingStand
				|| c instanceof ContainerHorseInventory
				|| c instanceof ContainerFurnace
				|| c instanceof ContainerRepair
				|| c instanceof ContainerEnchantment
				|| c instanceof ContainerHopper)
			return true;
		return false;
	}
	
	public static boolean isProtectedContainerBlock(Block b) {
		if (b instanceof BlockDispenser
				|| b instanceof BlockBrewingStand
				|| b instanceof BlockFurnace
				|| b instanceof BlockAnvil
				|| b instanceof BlockEnchantmentTable
				|| b instanceof BlockHopper)
			return true;
		return false;
	}
}
