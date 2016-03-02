package com.contained.game.handler;

import java.util.ArrayList;

import com.contained.game.Contained;
import com.contained.game.ContainedRegistry;
import com.contained.game.data.Data;
import com.contained.game.data.DataItemStack;
import com.contained.game.data.Data.OccupationRank;
import com.contained.game.entity.ExtendedPlayer;
import com.contained.game.item.BlockInteractItem;
import com.contained.game.network.ClientPacketHandler;
import com.contained.game.user.PlayerTeamIndividual;
import com.contained.game.util.Resources;

import codechicken.lib.packet.PacketCustom;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.EntityEvent.EntityConstructing;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;
import net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent;

public class PlayerEvents {
	@SubscribeEvent
	//When a player joins the server, send their client the territory & team data.
	public void onJoin(EntityJoinWorldEvent event) {
		if (event.entity instanceof EntityPlayer && !event.world.isRemote) {
			EntityPlayer joined = (EntityPlayer)event.entity;
			
			//Future TODO:
			//  -Remind the player on join if they have pending invitations.
			//  -Notify the player on join if one of their invitations got accepted
			//   since the last time they were online.
			
			if (PlayerTeamIndividual.get(joined) == null) {
				// Server has no info about this player, this must be their first
				// time joining. Initialize their custom data.
				Contained.teamMemberData.add(new PlayerTeamIndividual(joined.getDisplayName()));
			
				//Give first time players a tutorial book.
				if (!joined.inventory.hasItem(ContainedRegistry.book))
					event.world.spawnEntityInWorld(new EntityItem(event.world, 
							joined.posX, joined.posY+1, joined.posZ, 
							new ItemStack(ContainedRegistry.book, 1)));
			}
			
			ClientPacketHandler.packetSyncTeams(Contained.teamData).sendToPlayer(joined);
			ClientPacketHandler.packetSyncTerritories(Contained.territoryData).sendToPlayer(joined);			
		}
	}
	
	@SubscribeEvent
	public void onEntityLiving(LivingUpdateEvent event) {
		if (event.entity != null && event.entity instanceof EntityPlayer
				&& !event.entity.worldObj.isRemote) {
			EntityPlayer player = (EntityPlayer)event.entity;
			
			//Intermittently sync data logging information with the client
			//so the visualizations can be updated.
			if (player != null && Math.random() <= 1.0/20.0) {
				int[] occupationData = ExtendedPlayer.get(player).getOccupationValues();
				ArrayList<String> achievements = ExtendedPlayer.get(player).getAchievement();
				
				PacketCustom occPacket = new PacketCustom(Resources.MOD_ID, ClientPacketHandler.OCCUPATIONAL_DATA);
				for(int i=0; i<occupationData.length; i++)
					occPacket.writeInt(occupationData[i]);
				occPacket.sendToPlayer(player);
				
				PacketCustom usePacket = new PacketCustom(Resources.MOD_ID, ClientPacketHandler.ITEM_USAGE_DATA);
				usePacket.writeInt(ExtendedPlayer.get(player).usedOwnItems);
				usePacket.writeInt(ExtendedPlayer.get(player).usedOthersItems);
				usePacket.writeInt(ExtendedPlayer.get(player).usedByOthers);
				usePacket.sendToPlayer(player);
			}
			
			//Update items in a player's inventory which
			//have no owner to be owned by them
			if (player != null) {
				ItemStack[] inventory = player.inventory.mainInventory;
				for(ItemStack stack : inventory)
					if (stack != null)
						processNewOwnership(player, stack);
			}
		}
	}
	
	@SubscribeEvent
	public void onEntityHurt(LivingHurtEvent event){
		String damage = event.source.damageType;
		if(event.entity instanceof EntityPlayer){
			int occupation = ExtendedPlayer.get((EntityPlayer) event.entity).getOccupationClass();
			if(occupation == Data.FIGHTER && event.source.isFireDamage())
				event.setCanceled(true);
			if(occupation == Data.POTION && event.source.isMagicDamage())
				event.setCanceled(true);
			if(occupation == Data.MACHINE && event.source.isExplosion())
				event.setCanceled(true);
			if(occupation == Data.FARMING && damage.compareTo("thorns") == 0)
				event.setCanceled(true);
			if(occupation == Data.BUILDING && damage.compareTo("fallingBlock") == 0)
				event.setCanceled(true);
			if(occupation == Data.FISHING && damage.compareTo("drown") == 0)
				event.setCanceled(true);
			if(occupation == Data.COOKING && damage.compareTo("starve") == 0)
				event.setCanceled(true);
			if(occupation == Data.MINING && damage.compareTo("fall") == 0)
				event.setCanceled(true);
			if(occupation == Data.LUMBER && damage.compareTo("lightingBolt") == 0)
				event.setCanceled(true);
		}
	}
	
	@SubscribeEvent
	public void onEntityConstructing(EntityConstructing event) {
		if (event.entity instanceof EntityPlayer && ExtendedPlayer.get((EntityPlayer) event.entity) == null)
			ExtendedPlayer.register((EntityPlayer) event.entity);
	}
	
	/*
	 * =====================================================================
	 *  ITEM USAGE FUNCTIONS
	 * =====================================================================
	 */
	
	//Handle all data collection for a player using (consuming) an item.
	public void processItemUsage(EntityPlayer user, ItemStack item) {
		if (!user.worldObj.isRemote) {
			NBTTagCompound itemData = Data.getTagCompound(item);	
			String ownerName = itemData.getString("owner");
			EntityPlayer owner = user.worldObj.getPlayerEntityByName(ownerName);
			
			//Use own item
			if (ownerName.equals(user.getDisplayName())) {
				ExtendedPlayer.get(user).usedOwnItems += 1;
			}
			//Use other person's item
			else {
				ExtendedPlayer.get(user).usedOthersItems += 1;
				if (owner != null)
					//Note: Data logging here may be flawed, as I don't
					//think it will be able to update this parameter
					//if the owner of the item is not currently online.
					ExtendedPlayer.get(owner).usedByOthers += 1;
			}
		}
	}
	
	@SubscribeEvent
	//When an item is used, if it is consumed by the usage, log it.
	public void onItemUsed(PlayerUseItemEvent.Finish event) {
		if (event.entityPlayer != null && event.item != null)
			processItemUsage(event.entityPlayer, event.item);
	}
	
	@SubscribeEvent
	//Handle firing the functionality of BlockInteractItems when right-clicked on a block.
	public void onItemUse(PlayerInteractEvent event) {
		if (!event.world.isRemote) {
			ItemStack usedItem = event.entityPlayer.getHeldItem();
			if (usedItem == null)
				return;
			if (usedItem.getItem() instanceof BlockInteractItem
					&& event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
				BlockInteractItem intItem = (BlockInteractItem)usedItem.getItem();
				intItem.onBlockInteract(event.entityPlayer, event.x, event.y, event.z);
			}
		}
	}
	
	/*
	 * =====================================================================
	 *  ITEM OWNERSHIP FUNCTIONS
	 * =====================================================================
	 */
	
	//Handle all data collection procedures for when a player becomes
	//the owner of a new item.
	public void processNewOwnership(EntityPlayer newOwner, ItemStack item) {
		//First make sure this item isn't owned by someone already...
		NBTTagCompound itemData = Data.getTagCompound(item);	
		String owner = itemData.getString("owner");
		if ((owner == null || owner.equals("")) && newOwner != null) {			
			//Check if this item corresponds to an "occupation", and update
			//player's values accordingly
			OccupationRank occ = Data.occupationMap.get(new DataItemStack(item));
			if (occ != null) {
				ExtendedPlayer.get(newOwner)
					.increaseOccupation(occ.occupationID, occ.rank*item.stackSize);
			}
			
			//Set this player as the new owner of the item
			itemData.setString("owner", newOwner.getDisplayName());
			item.setTagCompound(itemData);
		}
	}
	
	@SubscribeEvent
	//When an unowned item is collected, it is owned by the collector.
	public void onItemCollected(EntityItemPickupEvent event) {
		if (event.entityPlayer != null)
			processNewOwnership(event.entityPlayer, event.item.getEntityItem());
	}
	
	@SubscribeEvent
	//When a block is harvested, the dropped items are owned by the harvester.
	public void onItemHarvested(HarvestDropsEvent event) {
		ArrayList<ItemStack> drops = event.drops;
		for (ItemStack stack : drops)
			processNewOwnership(event.harvester, stack);
	}
	
	@SubscribeEvent
	//When a creature is killed, the dropped items are owned by the killer.
	public void onCreatureDropItems(LivingDropsEvent event) {
		DamageSource ds = event.source;
		if (ds != null && ds.getEntity() != null
				&& ds.getEntity() instanceof EntityPlayer) 
		{
			EntityPlayer killer = (EntityPlayer)ds.getEntity();
			ArrayList<EntityItem> drops = event.drops;
			for (EntityItem item : drops)
				processNewOwnership(killer, item.getEntityItem());
		}
	}
	
	@SubscribeEvent
	//Show the owner of an item in the item's mouse-over tooltip.
	public void itemInformation(ItemTooltipEvent event) {
		ItemStack stack = event.itemStack;
		NBTTagCompound itemData = Data.getTagCompound(stack);
		String owner = itemData.getString("owner");
		
		if (owner == null || owner.equals(""))
			event.toolTip.add("Not Owned");
		else
			event.toolTip.add("Owner: "+owner);
	}
	
	@SubscribeEvent
	//Make a player wait a little longer to loot an item if it belongs
	//to someone else.
	public void onItemPickup(EntityItemPickupEvent event) {
		int secondsToWait = 10;
		ItemStack stack = event.item.getEntityItem();
		NBTTagCompound itemData = Data.getTagCompound(stack);		
		String owner = itemData.getString("owner");
		
		if (event.entityPlayer != null && !(owner == null || owner.equals("") 
				|| owner.equals(event.entityPlayer.getDisplayName()))) {
			if (event.item.ticksExisted < 20*secondsToWait)
				event.setCanceled(true);
		}
	}
}