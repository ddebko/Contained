package com.contained.game.network;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import com.contained.game.Contained;
import com.contained.game.data.Data;
import com.contained.game.entity.ExtendedPlayer;
import com.contained.game.ui.ClassPerks;
import com.contained.game.ui.DataVisualization;
import com.contained.game.ui.GuiGuild;
import com.contained.game.ui.GuiTownManage;
import com.contained.game.ui.TerritoryRender;
import com.contained.game.ui.guild.GuildBase;
import com.contained.game.ui.guild.GuildLeader;
import com.contained.game.user.PlayerTeam;
import com.contained.game.user.PlayerTeamIndividual;
import com.contained.game.user.PlayerTeamInvitation;
import com.contained.game.user.PlayerTrade;
import com.contained.game.util.Resources;
import com.contained.game.world.block.TerritoryMachineTE;

import codechicken.lib.packet.PacketCustom;
import codechicken.lib.vec.BlockCoord;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientCustomPacketEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * Handling of packets sent from server to client.
 */
public class ClientPacketHandler extends ServerPacketHandler {
	private DataVisualization gui;
	private TerritoryRender render;
	
	public ClientPacketHandler(DataVisualization gui, TerritoryRender render) {
		this.gui = gui;
		this.render = render;
	}
	
	@SubscribeEvent
	public void handlePacket(ClientCustomPacketEvent event) {	
		BlockCoord bc;
		TileEntity te;
		String teamID;
		int num;
		channelName = event.packet.channel();
		Minecraft mc = Minecraft.getMinecraft();
		
		if (channelName.equals(Resources.MOD_ID) && event.packet.getTarget() == Side.CLIENT) {
			PacketCustom packet = new PacketCustom(event.packet.payload());
			
			String status;
			int color;
			switch(packet.getType()) {
				case ClientPacketHandlerUtil.OCCUPATIONAL_DATA:
					for(int i=0; i<Data.occupationNames.length; i++)
						ExtendedPlayer.get(mc.thePlayer).setOccupation(i, packet.readInt());
					break;
					
				case ClientPacketHandlerUtil.ITEM_USAGE_DATA:
					ExtendedPlayer.get(mc.thePlayer).usedOwnItems = packet.readInt();
					ExtendedPlayer.get(mc.thePlayer).usedOthersItems = packet.readInt();
					ExtendedPlayer.get(mc.thePlayer).usedByOthers = packet.readInt();
					break;
					
				case ClientPacketHandlerUtil.FULL_TERRITORY_SYNC:
					int numBlocks = packet.readInt();
					Contained.territoryData.clear();
					for(int i=0; i<numBlocks; i++) {
						bc = packet.readCoord();
						Contained.territoryData.put(new Point(bc.x, bc.z), packet.readString());
					}
					render.regenerateEdges();
				break;
					
				case ClientPacketHandlerUtil.ADD_TERRITORY_BLOCK:
					bc = packet.readCoord();
					Contained.territoryData.put(new Point(bc.x, bc.z), packet.readString());
					render.regenerateEdges();
				break;
				
				case ClientPacketHandlerUtil.REMOVE_TERRITORY_BLOCK:
					bc = packet.readCoord();
					Contained.territoryData.remove(new Point(bc.x, bc.z));
					render.regenerateEdges();
				break;
					
				case ClientPacketHandlerUtil.SYNC_TEAMS:
					int numTeamsBefore = Contained.teamData.size();
					Contained.teamData.clear();
					int numTeams = packet.readInt();
					for(int i=0; i<numTeams; i++) {
						PlayerTeam readTeam = new PlayerTeam(packet.readNBTTagCompound());
						Contained.teamData.add(readTeam);
					}
						
					if (Contained.teamData.size() < numTeamsBefore) {
						//Some team got disbanded. Need to remove stale territory blocks.
						ArrayList<Point> terrToRemove = new ArrayList<Point>();
						for(Point p : Contained.territoryData.keySet()) {
							String terrID = Contained.territoryData.get(p);
							if (PlayerTeam.get(terrID) == null)
								terrToRemove.add(p);
						}
						for (Point p : terrToRemove)
							Contained.territoryData.remove(p);
						render.regenerateEdges();
					}
				break;
				
				case ClientPacketHandlerUtil.TE_PARTICLE:
					te = mc.theWorld.getTileEntity(packet.readInt(), packet.readInt(), packet.readInt());
					if (te instanceof TerritoryMachineTE) {
						TerritoryMachineTE machine = (TerritoryMachineTE)te;
						machine.displayParticle = packet.readString();
					}
				break;
				
				case ClientPacketHandlerUtil.TMACHINE_STATE:
					te = mc.theWorld.getTileEntity(packet.readInt(), packet.readInt(), packet.readInt());
					if (te instanceof TerritoryMachineTE) {
						TerritoryMachineTE machine = (TerritoryMachineTE)te;
						machine.tickTimer = packet.readInt();
						teamID = packet.readString();
						if (teamID.equals(""))
							teamID = null;
						machine.teamID = teamID;
						machine.shouldClaim = packet.readBoolean();
						machine.refreshColor();
					}
				break;
				
				case ClientPacketHandlerUtil.GUILD_JOIN:
					status = packet.readString();
					color = packet.readInt();
					if(status.equals("Joined Team")){
						mc.displayGuiScreen(new GuiGuild());
					}else if(mc.currentScreen instanceof GuiGuild){
						GuildBase.statusInfo = status;
						GuildBase.statusColor = new Color(color);
					}
				break;
				
				case ClientPacketHandlerUtil.GUILD_LEAVE:
					if(mc.currentScreen instanceof GuiGuild)
						mc.displayGuiScreen(new GuiGuild());
				break;
				
				case ClientPacketHandlerUtil.GUILD_CREATE:
					status = packet.readString();
					color = packet.readInt();
					if(status.equals("Team Successfully Created")){
						mc.displayGuiScreen(new GuiGuild());
					}else if(mc.currentScreen instanceof GuiGuild){
						GuildBase.statusInfo = status;
						GuildBase.statusColor = new Color(color);
					}
				break;
				
				case ClientPacketHandlerUtil.GUILD_DISBAND:
					if(mc.currentScreen instanceof GuiGuild)
						mc.displayGuiScreen(new GuiGuild());
				break;
				
				case ClientPacketHandlerUtil.GUILD_UPDATE:
					status = packet.readString();
					color = packet.readInt();
					if(status.equals("Changed Saved")){
						if(mc.currentScreen instanceof GuiGuild){
							GuildLeader.teamUpdateStatus = status;
							GuildLeader.teamUpdateColor = new Color(color);
						}
					}
				break;
				
				case ClientPacketHandlerUtil.PLAYER_INVITE:
					
				break;
				
				case ClientPacketHandlerUtil.PLAYER_DECLINE:
					status = packet.readString();
					color = packet.readInt();
					if(status.equals("Invitation has been removed")){
						if(mc.currentScreen instanceof GuiGuild){
							GuildBase.invites.remove(GuildBase.currentCol);
							GuildBase.currentCol = (GuildBase.currentCol < GuildBase.invites.size()) ? GuildBase.currentCol++ : 0;
						}
					}else if(mc.currentScreen instanceof GuiGuild){
						GuildBase.statusInfo = status;
						GuildBase.statusColor = new Color(color);
					}
				break;
				
				case ClientPacketHandlerUtil.PLAYER_KICK:
					if(mc.currentScreen instanceof GuiGuild)
						mc.displayGuiScreen(new GuiGuild());
				break;
				
				case ClientPacketHandlerUtil.PLAYER_PROMOTE:
					
				break;
				
				case ClientPacketHandlerUtil.PLAYER_DEMOTE:
					status = packet.readString();
					color = packet.readInt();
					if(status.equals("Successfully Demoted")){
						if(mc.currentScreen instanceof GuiGuild)
							mc.displayGuiScreen(new GuiGuild());
					}
				break;
					
				case ClientPacketHandlerUtil.LEVEL_UP:
					ExtendedPlayer.get(mc.thePlayer).occupationLevel = packet.readInt();
					ExtendedPlayer.get(mc.thePlayer).addPerk(packet.readInt());
					if(mc.currentScreen instanceof ClassPerks)
						mc.displayGuiScreen(new ClassPerks());
				break;
					
				case ClientPacketHandlerUtil.SELECT_CLASS:
					ExtendedPlayer.get(mc.thePlayer).occupationClass = packet.readInt();
					if(mc.currentScreen instanceof ClassPerks)
						mc.displayGuiScreen(new ClassPerks());
				break;
				
				case ClientPacketHandlerUtil.PERK_INFO:
					int perkID;
					for(int i = 0; i < 5; i++)
						if((perkID = packet.readInt()) != -1)
							ExtendedPlayer.get(mc.thePlayer).addPerk(perkID);
					ExtendedPlayer.get(mc.thePlayer).occupationClass = packet.readInt();
					ExtendedPlayer.get(mc.thePlayer).occupationLevel = packet.readInt();
				break;
				
				case ClientPacketHandlerUtil.UPDATE_PERMISSIONS:
					PlayerTeam team = new PlayerTeam(packet.readNBTTagCompound());					
					PlayerTeam toModify = PlayerTeam.get(team);
					toModify.permissions = team.permissions;
				break;
				
				case ClientPacketHandlerUtil.SYNC_LOCAL_PLAYER:
					NBTTagCompound ntc = packet.readNBTTagCompound();
					PlayerTeamIndividual pdata = new PlayerTeamIndividual(ntc);
					boolean found = false;
					for(PlayerTeamIndividual player : Contained.teamMemberData) {
						if (player.playerName.equals(pdata.playerName)) {
							found = true;
							player.readFromNBT(ntc);
							break;
						}
					}
					if (!found)
						Contained.teamMemberData.add(pdata);
				break;
				
				case ClientPacketHandlerUtil.REMOVE_ITEM:
					int slotId = packet.readInt() + 9;
					if(slotId >= mc.thePlayer.inventory.mainInventory.length)
						slotId -= mc.thePlayer.inventory.mainInventory.length;
					
					if(mc.thePlayer.inventory.getStackInSlot(slotId) != null)
						mc.thePlayer.inventory.setInventorySlotContents(slotId, null);
					
					if(mc.currentScreen instanceof GuiTownManage)
						mc.displayGuiScreen(new GuiTownManage(mc.thePlayer.inventory, GuiTownManage.te, GuiTownManage.blockTeamID, GuiTownManage.playerTeamID));
				break;
				
				case ClientPacketHandlerUtil.ADD_ITEM:
					ItemStack item = packet.readItemStack();
					if(mc.thePlayer.inventory.getFirstEmptyStack() > -1 && item != null)
						mc.thePlayer.inventory.addItemStackToInventory(item);
					
					if(mc.currentScreen instanceof GuiTownManage)
						mc.displayGuiScreen(new GuiTownManage(mc.thePlayer.inventory, GuiTownManage.te, GuiTownManage.blockTeamID, GuiTownManage.playerTeamID));
				break;
				
				case ClientPacketHandlerUtil.CREATE_TRADE:
					PlayerTrade addTrade = new PlayerTrade(packet.readNBTTagCompound());
					if(addTrade != null && addTrade.offer != null && addTrade.request != null)
						Contained.trades.add(addTrade);
					
					if(mc.currentScreen instanceof GuiTownManage)
						mc.displayGuiScreen(new GuiTownManage(mc.thePlayer.inventory, GuiTownManage.te, GuiTownManage.blockTeamID, GuiTownManage.playerTeamID));
				break;
				
				case ClientPacketHandlerUtil.REMOVE_TRADE:
					String UUID = packet.readString();
					if(UUID.isEmpty())
						return;
					
					for(PlayerTrade remTrade : Contained.trades)
						if(remTrade.id.equals(UUID)){
							Contained.trades.remove(remTrade);
							break;
						}
					
					if(mc.currentScreen instanceof GuiTownManage)
						mc.displayGuiScreen(new GuiTownManage(mc.thePlayer.inventory, GuiTownManage.te, GuiTownManage.blockTeamID, GuiTownManage.playerTeamID));
				break;
				
				case ClientPacketHandlerUtil.TRADE_TRANS:
					ItemStack offer = packet.readItemStack();
					ItemStack request = packet.readItemStack();
					
					if(offer == null){
						mc.thePlayer.inventory.addItemStackToInventory(request);
					}else{
						int count = request.stackSize;
						for(int i = 0; i < mc.thePlayer.inventory.getSizeInventory(); i++){
							ItemStack itemRemove = mc.thePlayer.inventory.getStackInSlot(i);
							if(itemRemove != null && itemRemove.getItem().equals(request.getItem())){
								if((count-itemRemove.stackSize) > 0){
									mc.thePlayer.inventory.setInventorySlotContents(i, null);
									count -= itemRemove.stackSize;
								}else{
									mc.thePlayer.inventory.decrStackSize(i, itemRemove.stackSize-count);
									count = 0;
									break;
								}
							}
						}
						
						mc.thePlayer.inventory.addItemStackToInventory(offer);
					}
					
					if(mc.currentScreen instanceof GuiTownManage)
						mc.displayGuiScreen(new GuiTownManage(mc.thePlayer.inventory, GuiTownManage.te, GuiTownManage.blockTeamID, GuiTownManage.playerTeamID));
				break;
				
				case ClientPacketHandlerUtil.SYNC_TRADE:
					Contained.trades.clear();
					int numTrades = packet.readInt();
					for(int i=0; i<numTrades; i++) {
						PlayerTrade readTrade = new PlayerTrade(packet.readNBTTagCompound());
						if(readTrade != null && readTrade.offer != null && readTrade.request != null)
							Contained.trades.add(readTrade);
					}
				break;
				
				case ClientPacketHandlerUtil.PLAYER_ADMIN:
					mc.thePlayer.setInvisible(true);
					mc.thePlayer.capabilities.allowFlying = true;
					mc.thePlayer.capabilities.disableDamage = true;
					ExtendedPlayer.get(mc.thePlayer).setAdminRights(true);
				break;
				
				case ClientPacketHandlerUtil.NEW_PLAYER:
					Contained.teamMemberData.add(new PlayerTeamIndividual(packet.readString()));
				break;
				
				case ClientPacketHandlerUtil.UPDATE_PLAYER:
					String name = packet.readString();
					PlayerTeamIndividual toUpdate = null;
					for(PlayerTeamIndividual player : Contained.teamMemberData) {
						if (player.playerName.equals(name)) {
							toUpdate = player;
							break;
						}
					}
					
					if (toUpdate == null) {
						toUpdate = new PlayerTeamIndividual(name);
						Contained.teamMemberData.add(toUpdate);
					}
					
					teamID = packet.readString();
					if (!teamID.equals(""))
						toUpdate.teamID = teamID;
				break;
				
				case ClientPacketHandlerUtil.PLAYER_LIST:
					PlayerTeamIndividual self = PlayerTeamIndividual.get(mc.thePlayer);
					Contained.teamMemberData.clear();
					if (self != null)
						Contained.teamMemberData.add(self);
					num = packet.readInt();
					for(int i=0; i<num; i++) {
						PlayerTeamIndividual newPlayer = new PlayerTeamIndividual(packet.readString());
						teamID = packet.readString();
						if (newPlayer.playerName.equals(mc.thePlayer.getDisplayName()))
							continue;
						else {
							if (!teamID.equals(""))
								newPlayer.teamID = teamID;
							Contained.teamMemberData.add(newPlayer);
						}
					}
				break;
				
				case ClientPacketHandlerUtil.SYNC_INVITATIONS:
					Contained.teamInvitations.clear();
					num = packet.readInt();
					for(int i=0; i<num; i++)
						Contained.teamInvitations.add(new PlayerTeamInvitation(packet.readNBTTagCompound()));
				break;
			}
		}
	}
}
