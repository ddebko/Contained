package com.contained.game.commands;

import java.util.ArrayList;
import java.util.List;

import com.contained.game.ContainedRegistry;
import com.contained.game.item.TutorialBook;
import com.contained.game.util.Util;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

public class CommandTutorialBook implements ICommand {
	private List aliases;
	public CommandTutorialBook(){
		this.aliases = new ArrayList();
		this.aliases.add("tutorial");
		this.aliases.add("tutorialbook");
		this.aliases.add("howtoplay");
	}
	
	@Override
	public String getCommandName(){
		return "tutorial";
	}
	
	@Override
	public String getCommandUsage(ICommandSender sender){
		return "/tutorial";
	}
	
	@Override
	public List getCommandAliases(){
		return this.aliases;
	}
	
	@Override
	public void processCommand(ICommandSender sender, String[] astring){
		if (!sender.getEntityWorld().isRemote && sender instanceof EntityPlayer) {
			EntityPlayer player = (EntityPlayer) sender;
			int emptySlot = player.inventory.getFirstEmptyStack();
			if(emptySlot >= 0 && !player.inventory.hasItemStack(new ItemStack(ContainedRegistry.book, 1)))
				player.inventory.addItemStackToInventory(new ItemStack(ContainedRegistry.book, 1));
			else
				sender.addChatMessage(new ChatComponentText(Util.errorCode + "Error: Already Have Item Or No Inventory Space Available"));
				
		}
	}

	@Override
	public int compareTo(Object o) {
		return 0;
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		return true;
	}

	@Override
	public List addTabCompletionOptions(ICommandSender sender, String[] astring) {
		return null;
	}

	@Override
	public boolean isUsernameIndex(String[] astring, int i) {
		return false;
	}
}
