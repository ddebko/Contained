package com.contained.game.util;

import java.awt.Point;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.DimensionManager;

import com.contained.game.Contained;
import com.contained.game.user.PlayerTeam;
import com.contained.game.user.PlayerTeamIndividual;
import com.contained.game.user.PlayerTeamInvitation;
import com.contained.game.world.GenerateWorld;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

public class Save {
	public static void saveWorldData() {
		//Save world generation data
		NBTTagCompound ntc = new NBTTagCompound();
		ntc.setInteger("worldRadius", Resources.worldRadius);
		saveNBTFile("worldProperties.dat", ntc);
		for(int i=0; i<GenerateWorld.oreSpawnProperties.length; i++)
			GenerateWorld.oreSpawnProperties[i].saveToFile();
		GenerateWorld.biomeProperties.saveToFile();
		
		//Categorize owned territory by team
		ntc = new NBTTagCompound();
		HashMap<String, ArrayList<Integer>> terrX = new HashMap<String, ArrayList<Integer>>();
		HashMap<String, ArrayList<Integer>> terrZ = new HashMap<String, ArrayList<Integer>>();
		
		for (Point p : Contained.territoryData.keySet()) {
			String team = Contained.territoryData.get(p);
			if (!terrX.containsKey(team)) {
				terrX.put(team, new ArrayList<Integer>());
				terrZ.put(team, new ArrayList<Integer>());
			}
			terrX.get(team).add(p.x);
			terrZ.get(team).add(p.y);
		}
		
		//Save team data
		NBTTagList teamList = new NBTTagList();
		for(PlayerTeam team : Contained.teamData) {
			NBTTagCompound teamNBT = new NBTTagCompound();
			team.writeToNBT(teamNBT);
			
			if (terrX.containsKey(team.id)) {
				int[] teamOwnX = new int[terrX.get(team.id).size()];
				int[] teamOwnZ = new int[terrX.get(team.id).size()];
				for (int i=0; i<teamOwnX.length; i++) {
					teamOwnX[i] = terrX.get(team.id).get(i);
					teamOwnZ[i] = terrZ.get(team.id).get(i);
				}
				teamNBT.setIntArray("territoryX", teamOwnX);
				teamNBT.setIntArray("territoryZ", teamOwnZ);
			}
			
			teamList.appendTag(teamNBT);
		}
		ntc.setTag("teamList", teamList);		
		
		//Save player data
		NBTTagList playerList = new NBTTagList();
		for(PlayerTeamIndividual player : Contained.teamMemberData) {
			NBTTagCompound playerNBT = new NBTTagCompound();
			player.writeToNBT(playerNBT);
			playerList.appendTag(playerNBT);
		}
		ntc.setTag("playerList", playerList);
		
		//Save invitations
		NBTTagList invitationList = new NBTTagList();
		for(PlayerTeamInvitation invite : Contained.teamInvitations) {
			NBTTagCompound inviteNBT = new NBTTagCompound();
			invite.writeToNBT(inviteNBT);
			invitationList.appendTag(inviteNBT);
		}
		ntc.setTag("invitationList", invitationList);
		
		saveNBTFile("territoryInfo.dat", ntc);
	}
	
	/**
	 * Save an NBT compound to a local file.
	 */
	public static boolean saveNBTFile(String fileName, NBTTagCompound ntc) {
		if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) 
			return false;
		
		try {
			File saveDir = new File(DimensionManager.getCurrentSaveRootDirectory(), "FiniteWorldData");
			if (!saveDir.exists())
				saveDir.mkdirs();
			File save = new File(saveDir, fileName);
			if (!save.exists())
				save.createNewFile();			
			DataOutputStream data = new DataOutputStream(new FileOutputStream(save));
			CompressedStreamTools.writeCompressed(ntc, data);
			data.close();
		} catch (Exception e) {
			System.out.println("Failed to save NBT to file "+fileName+".");
			return false;
		}
		return true;
	}
}