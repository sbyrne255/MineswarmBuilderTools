package com.mineswarm.tools;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Commands implements CommandExecutor{
	//private MineswarmBuilderTools instance = null;
	private BlockCopy bc = null;
	
	
	public Commands(MineswarmBuilderTools coreInstance) {
		//this.instance = coreInstance;
		bc = new BlockCopy(coreInstance);
	}
	@Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) { 
		if (sender instanceof Player) {
			Player player = (Player) sender;
            
			if(args[0].compareToIgnoreCase("copy") == 0) {
				player.sendMessage("Copying...");
				int scale = 5;
	            bc.CopyBlocks(player, player.getLocation().add(100*scale, scale, 100*scale));
	            player.sendMessage("Blocks Scheduled!");
			} else {
				player.sendMessage("Pasting...");
				bc.PasteBlocks(player);
			}
		}
		return true;
    }
}
