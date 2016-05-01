package com.contained.game.user;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import codechicken.lib.packet.PacketCustom;

import com.contained.game.Contained;
import com.contained.game.data.DataLogger;
import com.contained.game.entity.ExtendedPlayer;
import com.contained.game.network.ClientPacketHandlerUtil;
import com.contained.game.util.MiniGameUtil;
import com.contained.game.util.Resources;
import com.contained.game.util.Util;

public class PlayerMiniGame {
	private String[] intro = {"The", "League of", "Demons of"
			, " Avengers of", "Call of", "Warlords of", "Clan of"
			, "The Order of", "Gods of", "Knights of", "Guardians of"};

	private String[] words = {"Greater", "Lesser", "Beast", "Demon", "Your Mother", "My Mother", "His Mother"
			, "Your Father", "My Father", "Family Matters", "Nerds", "PvP", "Treasures", "His Father"
			, "Unforgiven", "Guards", "Oblivion", "Wrath", "Sin", "War", "Prophecy", "Creepers", "Notch"};

	private String[] combine = {"And", "Or", "With", "Rather Than", "In Contrast", "But", "Besides"
			, "Coupled With", "Beyond", "Under", "Above", "Nearly", "Aside From", "In Essence"};

	private int gameMode, gameID, dim;

	public PlayerMiniGame(int playersPending) {
		this(playersPending >= MiniGameUtil.getCapacity(Resources.PVP)
				, playersPending >= MiniGameUtil.getCapacity(Resources.TREASURE));		
	}

	public PlayerMiniGame(boolean enablePvP, boolean enableTreasure){
		Random rand = new Random();
		gameMode = -1;
		if(Contained.PVP_GAMES < Resources.MAX_PVP_GAMES 
				&& Contained.TREASURE_GAMES < Resources.MAX_TREASURE_GAMES
				&& enablePvP && enableTreasure) {
			if(rand.nextBoolean())
				gameMode = Resources.PVP;
			else
				gameMode = Resources.TREASURE;
		} 
		else if (Contained.PVP_GAMES < Resources.MAX_PVP_GAMES && enablePvP)
			gameMode = Resources.PVP;
		else if(Contained.TREASURE_GAMES < Resources.MAX_TREASURE_GAMES && enableTreasure)
			gameMode = Resources.TREASURE;

		if (gameMode != -1) {
			dim = getEmptyWorld(gameMode);
			if(dim == -1)
				gameMode = -1;
		}

		if (gameMode == -1)
			return;
		else if (gameMode == Resources.TREASURE)
			Contained.TREASURE_GAMES++;
		else if (gameMode == Resources.PVP)
			Contained.PVP_GAMES++;

		gameID = Contained.GAME_COUNT;
		Contained.GAME_COUNT++;
	}

	public PlayerMiniGame(int dimID, int gameMode){
		super();
		this.dim = dimID;
		this.gameMode = gameMode;
	}

	public PlayerMiniGame(NBTTagCompound ntc) {
		this.readFromNBT(ntc);
	}

	//Game Player To Random Team
	public void addPlayer(EntityPlayer player){
		if(player == null)
			return;

		ArrayList<PlayerTeam> teams = Contained.getTeamList(dim);
		if (teams.size() < Contained.configs.gameNumTeams[gameMode])
			createTeam(player);
		else { //Randomize Teams
			ArrayList<Integer> candidateTeams = new ArrayList<Integer>();
			for(int i=0; i<teams.size(); i++) {
				if (teams.get(i).numMembers() < Contained.configs.maxTeamSize[gameMode])
					candidateTeams.add(i);
			}
			Collections.shuffle(candidateTeams);
			if (candidateTeams.size() == 0)
				Util.serverDebugMessage("[ERROR] Failed to add player to mini-game team, because they were all already full!");
			else
				addPlayerToTeam(player, candidateTeams.get(0));
		}

		if(PlayerTeamIndividual.get(player) != null 
				&& PlayerTeamIndividual.get(player).teamID != null
				&& PlayerTeam.get(PlayerTeamIndividual.get(player).teamID) != null)
			Util.serverDebugMessage(player.getDisplayName()+" is now on team "+PlayerTeam.get(PlayerTeamIndividual.get(player).teamID).displayName);
	}

	private void addPlayerToTeam(EntityPlayer player, int team) {
		PlayerTeamIndividual pdata = PlayerTeamIndividual.get(player);
		pdata.joinMiniTeam(Contained.getTeamList(dim).get(team).id);
		DataLogger.insertMiniGamePlayer(Util.getServerID(), gameID, gameMode, player.getDisplayName(), Contained.getTeamList(dim).get(team).displayName, Util.getDate());
	}

	public void removePlayer(EntityPlayerMP player) {
		PlayerTeamIndividual pdata = PlayerTeamIndividual.get(player);
		if(getTeamID(pdata) != -1){
			pdata.revertMiniGameChanges();
			DataLogger.deleteMiniGamePlayer(player.getDisplayName());
		}
	}

	public void launchGame(ArrayList<EntityPlayer> playersJoining){
		if (MiniGameUtil.isPvP(dim))
			gameMode = Resources.PVP;
		else if (MiniGameUtil.isTreasure(dim))
			gameMode = Resources.TREASURE;

		DataLogger.insertNewMiniGame(Util.getServerID(), gameID, gameMode, Util.getDate());

		if(isGameReady()){
			pickRandomTeamLeaders();
			MiniGameUtil.startGame(this, playersJoining);
		}
	}

	public void endGame(){
		ArrayList<PlayerTeam> teams = Contained.getTeamList(dim);
		for(int i = 0; i < teams.size(); i++)
			DataLogger.insertGameResults(Util.getServerID(), 
					gameID, gameMode, teams.get(i).displayName, 
					Contained.gameScores[dim][i], Contained.timeLeft[dim], Util.getDate());

		Util.serverDebugMessage("Ending DIM"+dim+" game");

		Contained.gameActive[dim] = false;
		Contained.timeLeft[dim] = 0;
		ClientPacketHandlerUtil.syncMinigameTime(dim);
		Contained.getActiveTreasures(dim).clear();
		Contained.getTeamList(dim).clear();
		for(PlayerMiniGame game : Contained.miniGames)
			if(game.getGameDimension() == dim){
				Contained.miniGames.remove(game);
				break;
			}
		for(int i = 0; i < Contained.gameScores[dim].length; i++)
			Contained.gameScores[dim][i] = 0;

		if(MiniGameUtil.isTreasure(dim))
			Contained.getActiveTreasures(dim).clear();

		for(EntityPlayer player : getOnlinePlayers()){
			PlayerTeamIndividual pdata = PlayerTeamIndividual.get(player);
			ExtendedPlayer properties = ExtendedPlayer.get(player);

			if(MiniGameUtil.isPvP(dim) && pdata.teamID != null)
				DataLogger.insertPVPScore(Util.getServerID(), gameID, player.getDisplayName(), pdata.teamID, properties.curKills, properties.curDeaths, Util.getDate());
			else if(MiniGameUtil.isTreasure(dim) && pdata.teamID != null)
				DataLogger.insertTreasureScore(Util.getServerID(), gameID, player.getDisplayName(), pdata.teamID, properties.curTreasuresOpened, Util.getDate());

			Util.travelToDimension(0, player);
		}

		PacketCustom miniGamePacket = new PacketCustom(Resources.MOD_ID, ClientPacketHandlerUtil.MINIGAME_ENDED);
		miniGamePacket.writeInt(dim);
		Contained.channel.sendToDimension(miniGamePacket.toPacket(), 0);
	}

	public ArrayList<PlayerTeamIndividual> getGamePlayers() {
		ArrayList<PlayerTeamIndividual> players = new ArrayList<PlayerTeamIndividual>();
		for(PlayerTeamIndividual pdata : Contained.teamMemberData)
			if(getTeamID(pdata) != -1) 
				players.add(pdata);	

		return players;
	}
	
	public List<EntityPlayer> getOnlinePlayers() {
		WorldServer w = DimensionManager.getWorld(dim);
		if (w != null && w.playerEntities != null)
			return new ArrayList<EntityPlayer>(w.playerEntities);
		else
			return new ArrayList<EntityPlayer>();
	}

	/*
	private ItemStack rewardItem(int score, int totalScore){
		ItemStack reward = null;

		Random rand = new Random();
		double probability = ((double) score/(double) totalScore);
		Iterator<Item> items = GameData.getItemRegistry().iterator();
		while(reward == null){
			while(items.hasNext()){
				Item item = items.next();

			}
		}

		return reward;
	}

	private int rewardXP(int curScore, int score, int totalScore){
		return (curScore) * (score/totalScore);
	}
	 */

	public boolean isGameReady() {		
		int teamPlayerCount = 0;
		for(PlayerTeam team : Contained.getTeamList(dim))
			teamPlayerCount += team.numMembers();

		if (teamPlayerCount >= getCapacity())
			return true;
		return false;
	}

	// Index of this player's team in this dimension's team arraylist, or -1
	// if the player does not currently belong to any of this dimension's teams.
	public int getTeamID(PlayerTeamIndividual pdata) {
		if (pdata.teamID == null)
			return -1;

		for (int i=0; i<Contained.getTeamList(dim).size(); i++) {
			if (pdata.teamID.equals(Contained.getTeamList(dim).get(i).id))
				return i;
		}
		return -1;
	}

	public int getGameDimension(){
		return dim;
	}

	public int getGameID(){
		return gameID;
	}

	public int getGameMode(){
		return gameMode;
	}

	public int getCapacity(){
		return MiniGameUtil.getCapacity(this.gameMode);
	}

	public int numPlayers() {
		int count = 0;
		for(PlayerTeamIndividual pdata : Contained.teamMemberData)
			if(getTeamID(pdata) != -1)
				count++;
		return count;
	}
	
	public int numOnlinePlayers() {
		return Math.min(getOnlinePlayers().size(), numPlayers());
	}

	private void pickRandomTeamLeaders(){
		for(PlayerTeam team : Contained.getTeamList(dim)) {
			List<String> teamPlayers = team.getTeamPlayers();
			if (teamPlayers.size() != 0) {
				Collections.shuffle(teamPlayers);
				PlayerTeamIndividual pdata = PlayerTeamIndividual.get(teamPlayers.get(0));
				pdata.setTeamLeader();
			}
			else
				Util.serverDebugMessage("[ERROR] Tried to set a leader for a team that had no members.");
		}			
	}

	private String generateName(){
		Random rand = new Random();
		String teamName = "";
		do {
			teamName = intro[rand.nextInt(intro.length)] + " " + words[rand.nextInt(words.length)];
			if(rand.nextBoolean())
				teamName += " " + combine[rand.nextInt(combine.length)] + " " + words[rand.nextInt(words.length)];
		} while(teamName.length() > 20);

		return teamName;
	}

	private boolean teamExists(String teamName){
		WorldServer[] worlds = DimensionManager.getWorlds();
		for(WorldServer world : worlds)
			for(PlayerTeam team : Contained.getTeamList(world.provider.dimensionId))
				if (team.displayName.toLowerCase().equals(teamName.toLowerCase()))
					return true;

		return false;
	}

	private int getEmptyWorld(int gameMode){
		int dim = -1;

		ArrayList<Integer> pvpDims = new ArrayList<Integer>();
		ArrayList<Integer> treasureDims = new ArrayList<Integer>();
		for(int i=Resources.MIN_PVP_DIMID; i<=Resources.MAX_PVP_DIMID; i++)
			pvpDims.add(i);
		for(int i=Resources.MIN_TREASURE_DIMID; i<=Resources.MAX_TREASURE_DIMID; i++)
			treasureDims.add(i);

		for(PlayerMiniGame game : Contained.miniGames){
			if(game != null){
				if(game.gameMode == gameMode){
					if(gameMode == Resources.PVP)
						pvpDims.remove(new Integer(game.dim));
					else if(gameMode == Resources.TREASURE)
						treasureDims.remove(new Integer(game.dim));
				}
			}
		}

		if(gameMode == Resources.PVP && !pvpDims.isEmpty())
			return pvpDims.get(0);
		else if(!treasureDims.isEmpty())
			return treasureDims.get(0);

		return dim;
	}

	private void createTeam(EntityPlayer player){
		Random rand = new Random();
		String teamName = generateName();
		while(teamExists(teamName))
			teamName = generateName();

		PlayerTeam newTeam = new PlayerTeam(teamName, rand.nextInt(PlayerTeam.formatColors.length), dim);
		Contained.getTeamList(dim).add(newTeam);
		PlayerTeamIndividual pdata = PlayerTeamIndividual.get(player);
		pdata.joinMiniTeam(newTeam.id);	

		DataLogger.insertMiniGamePlayer(Util.getServerID(), gameID, gameMode, player.getDisplayName(), teamName, Util.getDate());
	}

	public void testLaunch(EntityPlayer player){
		createTeam(player);
		PlayerTeamIndividual pdata = PlayerTeamIndividual.get(player);
		pdata.setTeamLeader();
	}

	public void writeToNBT(NBTTagCompound ntc) {
		ntc.setInteger("dimID", this.dim);
		ntc.setInteger("gameID", this.gameID);
		ntc.setInteger("gameMode", this.gameMode);
	}

	public void readFromNBT(NBTTagCompound ntc) {
		this.dim = ntc.getInteger("dimID");
		this.gameID =  ntc.getInteger("gameID");
		this.gameMode = ntc.getInteger("gameMode");
	}

	public static PlayerMiniGame get(int dim){
		for(PlayerMiniGame game : Contained.miniGames)
			if(game.dim == dim)
				return game;

		return null;
	}

	public static PlayerMiniGame get(String teamName){
		for(PlayerMiniGame game : Contained.miniGames)
			if(game.teamExists(teamName))
				return game;

		return null;
	}
}