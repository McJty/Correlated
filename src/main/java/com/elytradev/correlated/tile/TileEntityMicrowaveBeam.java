package com.elytradev.correlated.tile;

import com.elytradev.correlated.Correlated;
import com.elytradev.correlated.CorrelatedWorldData;
import com.elytradev.correlated.block.BlockMicrowaveBeam;
import com.elytradev.correlated.block.BlockMicrowaveBeam.State;
import com.elytradev.correlated.wifi.Beam;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class TileEntityMicrowaveBeam extends TileEntityNetworkMember implements ITickable {

	private float syncedYaw;
	private float syncedPitch;
	
	@Override
	public int getEnergyConsumedPerTick() {
		return Correlated.inst.beamRfUsage;
	}
	
	@Override
	public void update() {
		if (!hasWorld() || getWorld().isRemote) return;
		Beam b = Correlated.getDataFor(world).getWirelessManager().getBeam(getPos());
		State newState = State.DEAD;
		if (!hasStorage() || !getStorage().isPowered()) {
			newState = State.DEAD;
		} else {
			if (b != null) {
				if (b.isObstructed()) {
					newState = State.ERROR;
				} else {
					newState = State.LINKED;
				}
			} else {
				newState = State.ERROR;
			}
		}
		IBlockState ibs = world.getBlockState(getPos());
		if (ibs.getBlock() == Correlated.microwave_beam) {
			if (ibs.getValue(BlockMicrowaveBeam.state) != newState) {
				Correlated.sendUpdatePacket(this);
				world.setBlockState(getPos(), ibs.withProperty(BlockMicrowaveBeam.state, newState));
			}
		}
		float yaw = getYaw(0);
		float pitch = getPitch(0);
		if (yaw != syncedYaw || pitch != syncedPitch) {
			syncedYaw = yaw;
			syncedPitch = pitch;
			Correlated.sendUpdatePacket(this);
		}
	}
	
	@Override
	public SPacketUpdateTileEntity getUpdatePacket() {
		return new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
	}
	
	@Override
	public NBTTagCompound getUpdateTag() {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setFloat("Yaw", getYaw(0));
		tag.setFloat("Pitch", getPitch(0));
		return tag;
	}
	
	@Override
	public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
		syncedYaw = pkt.getNbtCompound().getFloat("Yaw");
		syncedPitch = pkt.getNbtCompound().getFloat("Pitch");
	}
	
	public TileEntityController getOtherSide() {
		if (!hasWorld()) return null;
		Beam b = Correlated.getDataFor(world).getWirelessManager().getBeam(getPos());
		if (b == null) return null;
		if (b.isObstructed()) return null;
		BlockPos us = getPos();
		BlockPos other;
		if (b.getStart().equals(us)) {
			other = b.getEnd();
		} else {
			other = b.getStart();
		}
		TileEntity te = world.getTileEntity(other);
		if (te instanceof TileEntityMicrowaveBeam) {
			TileEntityMicrowaveBeam temb = (TileEntityMicrowaveBeam)te;
			if (temb.hasStorage()) {
				return temb.getStorage();
			}
		}
		return null;
	}
	
	private Vec3d getDirectionToOtherSide() {
		Beam b = Correlated.getDataFor(world).getWirelessManager().getBeam(getPos());
		if (b == null) return new Vec3d(0, 0, 0);
		BlockPos start = getPos();
		BlockPos end;
		if (b.getStart().equals(start)) {
			end = b.getEnd();
		} else {
			end = b.getStart();
		}
		BlockPos sub = start.subtract(end);
		return new Vec3d(sub).normalize();
	}
	
	public float getYaw(float partialTicks) {
		if (hasWorld() && getWorld().isRemote) {
			return syncedYaw;
		} else {
			Vec3d dir = getDirectionToOtherSide();
			if (dir.xCoord == 0 && dir.yCoord == 0 && dir.zCoord == 0) return 0;
			return (float)Math.toDegrees(MathHelper.atan2(dir.xCoord, dir.zCoord));
		}
	}

	public float getPitch(float partialTicks) {
		if (hasWorld() && getWorld().isRemote) {
			return syncedPitch;
		} else {
			Vec3d dir = getDirectionToOtherSide();
			if (dir.xCoord == 0 && dir.yCoord == 0 && dir.zCoord == 0) return 90;
			Vec3d xz = new Vec3d(dir.xCoord, 0, dir.zCoord);
			double xzLength = xz.lengthVector();
			return (float)-Math.toDegrees(MathHelper.atan2(dir.yCoord, xzLength));
		}
	}

	public Vec3d getFacing(float partialTicks) {
		return getDirectionToOtherSide();
	}
	
	public void link(BlockPos other) {
		CorrelatedWorldData data = Correlated.getDataFor(world);
		Beam cur = data.getWirelessManager().getBeam(getPos());
		if (cur != null) {
			data.getWirelessManager().remove(cur);
		}
		Beam b = new Beam(data, getPos(), other);
		data.getWirelessManager().add(b);
	}

}