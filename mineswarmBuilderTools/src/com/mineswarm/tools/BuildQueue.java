// BuildQueue.java
package com.mineswarm.tools;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;


public final class BuildQueue {
    private static final int DEFAULT_BLOCKS_PER_TICK = 100;
    private static final int DEFAULT_STEP = 16;

    private final Plugin plugin;
    private final Deque<Job> jobs = new ArrayDeque<>();
    private BukkitRunnable runner;
    private int opsPerTick = DEFAULT_BLOCKS_PER_TICK;
    private int stepSize = DEFAULT_STEP;

    public BuildQueue(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Tune how many operations to perform per tick (default 100). */
    public BuildQueue setOpsPerTick(int n) {
        this.opsPerTick = Math.max(1, n);
        return this;
    }

    /** Handle to cancel or query a queued job. */
    public static class Handle {
        private final Job job;
        private Handle(Job job) { this.job = job; }
        public void cancel() { job.cancelled = true; }
        public boolean isDone() { return job.done; }
    }
    public static final class CopyHandle extends Handle {
        private final List<Placement> placements;
        private CopyHandle(Job job, List<Placement> placements) {
            super(job);
            this.placements = placements;
        }
        /** Thread-safe view if you need to read while copying. */
        public List<Placement> getPlacements() { return placements; }
    }

    // ----- Enqueue APIs -----

    public Handle enqueueRegionFill(Location l1, Location l2, Material material, Runnable onDone) {
    	
        Iterator<int[]> coords = new TiledBlockIterator(l1,l2, 16,8,16);
        
        
        Job job = new Job(() -> 
        {
            if (!coords.hasNext()) {	return null; 	}
            
            int[] p = coords.next();
            return WorldManipulation.fill(l1.getWorld(), p[0], p[1], p[2], material);
        }, onDone);

        jobs.addLast(job);
        startRunnerIfNeeded();
        return new Handle(job);
    }

    public Handle enqueueRegionFill(Location l1, Location l2, int stepX, int stepY, int stepZ, Material material, Runnable onDone) {
        Iterator<int[]> coords = new TiledBlockIterator(l1,l2, stepSize,stepSize,stepSize);
        
        
        Job job = new Job(() -> 
        {
            if (!coords.hasNext()) {	return null; 	}
            
            int[] p = coords.next();
            return WorldManipulation.fill(l1.getWorld(), p[0], p[1], p[2], material);
        }, onDone);

        jobs.addLast(job);
        startRunnerIfNeeded();
        return new Handle(job);
    }
    public Handle enqueueRegionPaste(List<Placement> placements, Location originLocation, Runnable onDone) 
    {
		if (placements == null || placements.isEmpty()) {
			Bukkit.getLogger().warning("[BuilderTools] enqueueRegionPaste: empty placements.");
			if (onDone != null) {
				onDone.run();
			}
			return null;
		}

		
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
	    for (Placement p : placements) {
	        if (p.x < minX) minX = p.x;
	        if (p.y < minY) minY = p.y;
	        if (p.z < minZ) minZ = p.z;
	    }
	    final int offX = originLocation.getBlockX() - minX;
	    final int offY = originLocation.getBlockY() - minY;
	    final int offZ = originLocation.getBlockZ() - minZ;
		
	    final Iterator<Placement> it = placements.iterator();
		Job job = new Job(() -> 
		{
			if (!it.hasNext()) return null;
			Placement p = it.next();
			
	        World world = Bukkit.getWorld(p.worldName);
	        
	        return WorldManipulation.fill(world, p.x + offX, p.y + offY, p.z + offZ, p.material);

		}, onDone);
		
		jobs.addLast(job);
		startRunnerIfNeeded();
		return new Handle(job);
		}

    public CopyHandle enqueueRegionCopy(Location l1, Location l2, int stepX, int stepY, int stepZ, Runnable onDone) {
        Iterator<int[]> coords = new TiledBlockIterator(l1, l2, stepX, stepY, stepZ);

        // thread-safe list since edits happen on main thread but you might read later
        List<Placement> out = Collections.synchronizedList(new ArrayList<>());

        Job job = new Job(() -> {
            if (!coords.hasNext()) return null;
            int[] p = coords.next();
            return WorldManipulation.copy(l1.getWorld(), p[0], p[1], p[2], out::add);
        }, onDone);

        jobs.addLast(job);
        startRunnerIfNeeded();
        return new CopyHandle(job, out);
    }

    // ----- Runner loop -----

    private void startRunnerIfNeeded() {
        if (runner != null) {	return;	}

        runner = new BukkitRunnable() {
            @Override public void run() {
                int opsThisTick = 0;

                while (opsThisTick < opsPerTick && !jobs.isEmpty()) {
                    Job job = jobs.peekFirst();
                    if (job.cancelled) {
                        jobs.pollFirst();
                        continue;
                    }

                    WorldManipulation manipulate = job.next();
                    if (manipulate == null) {
                        jobs.pollFirst();
                        job.done = true;
                        if (job.onDone != null) {
                            try { job.onDone.run(); } catch (Throwable t) { /* ignore */ }
                        }
                        continue;
                    }

                    manipulate.apply();
                    opsThisTick++;
                }

                if (jobs.isEmpty()) {
                    cancel();
                    runner = null;
                } else {
                    rotateJobs();
                }
            }
        };

        // start next tick, then every tick
        runner.runTaskTimer(plugin, 1L, 1L);
    }

    private void rotateJobs() {
        if (jobs.isEmpty()) {	return;	}
        Job j = jobs.pollFirst();
        if (j != null) {	jobs.addLast(j);	}
    }

    // ----- Internals -----
    private enum Mode { 
    	FILL,	//World edit set equivalent, same block over region 
    	COPY, 	//Copies to memory the world, x,y,z, and block material at instance in the region
    	PASTE, 	//Sets the specific blocks that were copied (or saved)
    	CUT, 	//Copy function with FILL functionality setting air.
    	SAVE	//Copy with added database back-end saving.
    }

    /** One operation to perform at (x,y,z). */
    private static final class WorldManipulation {
        final World world; 
        final int x,y,z;
        final Mode mode;
        final Material intended;                 // for FILL or CUT
        final Consumer<Placement> sink; // add field, may be null

        private WorldManipulation(Location l, Mode m) {
            this.world = l.getWorld(); 
            this.x = l.getBlockX(); 
            this.y = l.getBlockY();
            this.z = l.getBlockZ();
            this.mode = m;
            this.intended = Material.AIR;
            this.sink = null;
        }
        //Used for/by copy.
        private WorldManipulation(Location l, Mode m, Consumer<Placement> sink) {
            this.world = l.getWorld(); 
            this.x = l.getBlockX(); 
            this.y = l.getBlockY();
            this.z = l.getBlockZ();
            this.mode = m;
            this.intended = Material.AIR;
            this.sink = sink;
        }
        private WorldManipulation(Location l, Mode m, Material intended) {
            this.world = l.getWorld(); 
            this.x = l.getBlockX(); 
            this.y = l.getBlockY();
            this.z = l.getBlockZ();
            this.mode = m;
            this.intended = intended;
            this.sink = null;
        }
        
        private WorldManipulation(World w, int x, int y, int z, Mode m) {
            this.world = w; 
            this.x = x; 
            this.y = y; 
            this.z = z;
            this.mode = m;
            this.intended = Material.AIR;
            this.sink = null;
        }
        private WorldManipulation(World w, int x, int y, int z, Mode m, Consumer<Placement> sink) {
            this.world = w; 
            this.x = x; 
            this.y = y; 
            this.z = z;
            this.mode = m;
            this.intended = Material.AIR;
            this.sink = sink;
        }
        private WorldManipulation(World w, int x, int y, int z, Mode m, Material intended) {
            this.world = w; 
            this.x = x; 
            this.y = y; 
            this.z = z;
            this.mode = m; 
            this.intended = intended;
            this.sink = null;
        }

        static WorldManipulation fill(World w, int x, int y, int z, Material mat) {
            return new WorldManipulation(w, x, y, z, Mode.FILL, mat);
        }
        static WorldManipulation copy(World w, int x, int y, int z, Consumer<Placement> sink) {
            return new WorldManipulation(w, x, y, z, Mode.COPY, sink);
        }

        void apply() {
            switch (mode) {
                case FILL -> {
                    world.getBlockAt(x, y, z).setType(intended, false);
                }
                case COPY -> {
                    // Read current, record only
                    Material current = world.getBlockAt(x, y, z).getType();
                    Placement p = new Placement(world.getName(), x, y, z, current, current);
                    //new Placement(world.getName(), x,y,z, current, current).writeToDisk();
                    sink.accept(p);//if null, write to disk could be used for slower save redudance
                }
                case PASTE -> {
                	world.getBlockAt(x, y, z).setType(intended, false);
                }
                case CUT -> {
                	Bukkit.getLogger().info("We don't do that yet.");
                }
                case SAVE -> {
                	Bukkit.getLogger().info("We don't do that yet.");
                }
            }
        }
    }

    private static final class Job 
    {
        private final Supplier<WorldManipulation> nextManipulation;
        private final Runnable onDone;
        boolean cancelled = false;
        boolean done = false;

        Job(Supplier<WorldManipulation> nextEdit, Runnable onDone) {
            this.nextManipulation = nextEdit;
            this.onDone = onDone;
        }

		WorldManipulation next() {
            return nextManipulation.get();
        }
    }

    /**
     * Streams block coordinates over an inclusive region, in tiles of size (stepX, stepY, stepZ).
     * Iteration order: X fastest, then Y, then Z; tiles advance X -> Y -> Z.
     * Yields int[]{x,y,z}.
     */
    private static final class TiledBlockIterator implements Iterator<int[]> 
    {
        private final int minX, minY, minZ, maxX, maxY, maxZ;
        private final int stepX, stepY, stepZ;

        private int tileX, tileY, tileZ;
        private int curX, curY, curZ;
        private int endX, endY, endZ;
        private boolean hasTile;

        TiledBlockIterator(Location l1, Location l2, int stepX, int stepY, int stepZ) {
            if (stepX <= 0 || stepY <= 0 || stepZ <= 0)
            {
                throw new IllegalArgumentException("step sizes must be > 0");//TODO Log and set to default values.
            }
            
            this.minX = Math.min((int)l1.getBlockX(), (int)l2.getBlockX()); 
            this.maxX = Math.max((int)l1.getBlockX(), (int)l2.getBlockX());
            
            this.minY = Math.min((int)l1.getBlockY(), (int)l2.getBlockY()); 
            this.maxY = Math.max((int)l1.getBlockY(), (int)l2.getBlockY());
            
            this.minZ = Math.min((int)l1.getBlockZ(), (int)l2.getBlockZ()); 
            this.maxZ = Math.max((int)l1.getBlockZ(), (int)l2.getBlockZ());
            
            this.stepX = stepX; this.stepY = stepY; this.stepZ = stepZ;
            this.tileX = minX; this.tileY = minY; this.tileZ = minZ;
            this.hasTile = true;
            advanceTile();
        }

        @Override public boolean hasNext() { return hasTile; }

        @Override public int[] next() {
            int[] out = new int[] { curX, curY, curZ };

            // within-tile progression: x -> y -> z
            if (curX < endX) {
                curX++;
            } else if (curY < endY) {
                curX = tileX;
                curY++;
            } else if (curZ < endZ) {
                curX = tileX; curY = tileY;
                curZ++;
            } else {
                // tile finished -> step tile
                stepTile();
                if (hasTile) advanceTile();
            }

            return out;
        }

        private void stepTile() {
            // Advance X tiles, then Y, then Z
            if (tileX + stepX <= maxX) {
                tileX += stepX;
            } else {
                tileX = minX;
                if (tileY + stepY <= maxY) {
                    tileY += stepY;
                } else {
                    tileY = minY;
                    if (tileZ + stepZ <= maxZ) {
                        tileZ += stepZ;
                    } else {
                        hasTile = false;
                    }
                }
            }
        }

        private void advanceTile() {
            if (!hasTile) return;
            endX = Math.min(tileX + stepX - 1, maxX);
            endY = Math.min(tileY + stepY - 1, maxY);
            endZ = Math.min(tileZ + stepZ - 1, maxZ);
            curX = tileX; curY = tileY; curZ = tileZ;
        }
    }
}
