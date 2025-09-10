package com.mineswarm.tools;
import org.bukkit.plugin.java.JavaPlugin;

public class MineswarmBuilderTools extends JavaPlugin {
	public Placement copiedItems = null;
	
    @Override
    public void onEnable() 
    {
    	
    	this.getCommand("buildertools").setExecutor(new Commands(this));
    	
    }
    // Fired when plugin is disabled
    @Override
    public void onDisable() {
    }
}
