package com.mineswarm.tools;

import java.io.BufferedWriter;
import java.io.FileWriter;

import org.bukkit.Material;

public final class Placement {
    public final String worldName;
    public final int x, y, z;
    public final Material material;
    public final Material originalMaterial;

    public Placement(String worldName, int x, int y, int z, Material material, Material orginal) {
        this.worldName = worldName;
        this.x = x; this.y = y; this.z = z;
        this.material = material;
        this.originalMaterial = orginal;
    }

    private String toJsonString() {
    	return "{"
    			+ "x:" + x + ","
    			+ "y:" + y + ","
    			+ "z:" + z + ","
    			+ "block:" +"\""+ material + "\","
    			+ "oldBlock:" +"\"" + originalMaterial + "\","
    			+ "world:" + "\"" + worldName + "\""
    			+ "},\n";
    }
    
    @Override
    public String toString() {
        return "Placement[" + worldName + " @ (" + x + "," + y + "," + z + ") = " + material + "]";
    }
    public void writeToDisk() {
    	write("testingCopy.json", toJsonString());
    }

    private void write(String path, String data) {
        try {
        	BufferedWriter writer = new BufferedWriter(new FileWriter(path, true));
            writer.write(data);
            writer.close();
        } catch (Exception e) {
            System.err.println("An error occurred while writing to the file: " + e.getMessage());
        }
    }
}