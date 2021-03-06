package com.elytradev.correlated.world;

import com.elytradev.correlated.CLog;
import com.elytradev.correlated.CorrelatedWorldData;
import com.elytradev.correlated.math.Vec2f;
import com.elytradev.correlated.math.Vec2i;
import com.google.common.base.Stopwatch;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

public class LimboTeleporter extends Teleporter {
	private final WorldServer world;
	private final DungeonGrid grid;
	private final DungeonScribe scribe;
	public LimboTeleporter(WorldServer world, DungeonScribe scribe, DungeonGrid grid) {
		super(world);
		this.world = world;
		this.scribe = scribe;
		this.grid = grid;
	}

	@Override
	public boolean makePortal(Entity entityIn) {
		return false;
	}
	
	@Override
	public boolean placeInExistingPortal(Entity entityIn, float rotationYaw) {
		placeInPortal(entityIn, rotationYaw);
		return true;
	}
	
	@Override
	public void placeInPortal(Entity entityIn, float rotationYaw) {
		LimboProvider provider = ((LimboProvider)world.provider);
		EntityPlayerMP player = ((EntityPlayerMP)entityIn);
		DungeonPlayer dp = provider.popEntering(player.getGameProfile().getId());
		CorrelatedWorldData data = CorrelatedWorldData.getFor(world.getMinecraftServer().getWorld(0));
		data.getPlayerRespawnData().put(dp.getProfile().getId(), dp.getOldPlayer());
		data.markDirty();
		
		Dungeon d = grid.getBySeed(dp.getSeed());
		if (d == null) {
			Vec2i freeSpot = grid.findFreeSpot();
			
			Stopwatch sw = Stopwatch.createUnstarted();
			sw.start();
			scribe.erase(freeSpot);
			sw.stop();
			CLog.info("Freed space at {} in {}", freeSpot, sw);
			sw.reset();
			
			sw.start();
			d = new Dungeon();
			d.generateNewPlan();
			grid.set(freeSpot, d);
			sw.stop();
			CLog.info("Generated new dungeon at {} in {}", freeSpot, sw);
			sw.reset();
			
			sw.start();
			scribe.write(d);
			sw.stop();
			CLog.info("Scribed new dungeon at {} in {}", freeSpot, sw);
		} else {
			CLog.info("Reusing existing dungeon at {}, {}", d.x, d.z);
		}
		Vec2f entrance = d.findEntranceTile();
		int x = (d.x*Dungeon.NODE_SIZE)*Dungeon.DUNGEON_SIZE;
		int z = (d.z*Dungeon.NODE_SIZE)*Dungeon.DUNGEON_SIZE;
		x += (entrance.x*Dungeon.NODE_SIZE);
		z += (entrance.y*Dungeon.NODE_SIZE);
		x += (Dungeon.NODE_SIZE/2);
		z += (Dungeon.NODE_SIZE/2);
		
		int y = 51;
		BlockPos pos = new BlockPos(x, y-1, z);
		if (world.isAirBlock(pos)) {
			world.setBlockState(pos, Blocks.STONE.getDefaultState());
		}
		((EntityPlayerMP) entityIn).connection.setPlayerLocation(x+0.5, y, z+0.5, rotationYaw, entityIn.rotationPitch);
	}
	
}
