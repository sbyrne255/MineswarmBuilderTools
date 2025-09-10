package com.mineswarm.tools;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class BlockCopy {
	private BuildQueue bq = null;
	private BuildQueue.CopyHandle lastHandle = null;
	
	public BlockCopy(MineswarmBuilderTools coreIstance) {
		this.bq = new BuildQueue(coreIstance);
	}
	
	
	public void CopyBlocks(Player player, Location end) {
	    bq.setOpsPerTick(1000);	    
	    
	    lastHandle =  bq.enqueueRegionCopy(
            player.getLocation(),
            end,
            16,8,16,
            () -> PostConsoleAndSender(player, "Copying Complete, homie!")
	    );
		
	}
	private void PostConsoleAndSender(Player player, String msg) {
		Bukkit.getLogger().info(msg);
		player.sendMessage(msg);
	}
	public void PasteBlocks(Player player) {
		if(lastHandle.isDone()) {
			bq.enqueueRegionPaste(lastHandle.getPlacements(), player.getLocation(), 
					() -> PostConsoleAndSender(player, "Pasted, homie!")
				);
		} else {
			player.sendMessage("We gotta wait, the handle is still running...");
		}
	}
	public void SetBlocks(Player player, Location end, Material blockType) {
		
	    bq.enqueueRegionFill(
            player.getLocation(),
            end,
            blockType,
            () -> PostConsoleAndSender(player, "Setted, homie!")
	    );
	}
}




/*
private String read(String path) {
	 String data = "";
     try {
    	 BufferedReader reader = new BufferedReader(new FileReader(path));
         String line;
         while ((line = reader.readLine()) != null) {
        	 data = line;
         }
         reader.close();
     }  catch (Exception e) {
         System.err.println("An error occurred while READING from the file: " + e.getMessage());
     }
     return data;
}
private void write(String path, String data) {
    try {
    	BufferedWriter writer = new BufferedWriter(new FileWriter(path));
        writer.write(data);
        writer.close();
    } catch (Exception e) {
        System.err.println("An error occurred while writing to the file: " + e.getMessage());
    }
}*/
