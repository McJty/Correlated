package com.unascribed.correlatedpotentialistics.tile;

import static net.minecraft.util.MathHelper.*;

import java.util.UUID;

import com.unascribed.correlatedpotentialistics.CoPo;
import com.unascribed.correlatedpotentialistics.CoPoWorldData.Transmitter;
import com.unascribed.correlatedpotentialistics.block.BlockWirelessEndpoint;
import com.unascribed.correlatedpotentialistics.block.BlockWirelessEndpoint.State;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public class TileEntityWirelessReceiver extends TileEntityWirelessEndpoint {
	private UUID transmitter;
	private Transmitter transmitterCache;
	private float syncedYaw;
	private float syncedPitch;
	
	public float getYaw(float partialTicks) {
		switch (getCurrentState()) {
			case ERROR:
				return (worldObj.getTotalWorldTime()+partialTicks)%360;
			case LINKED:
				if (hasWorldObj() && getWorld().isRemote) {
					return syncedYaw;
				} else {
					Vec3 dir = getDirectionToTransmitter();
					return (float)Math.toDegrees(MathHelper.atan2(dir.xCoord, dir.zCoord));
				}
			default:
				return 0;
		}
	}

	public float getPitch(float partialTicks) {
		switch (getCurrentState()) {
			case ERROR:
				return (((MathHelper.sin((worldObj.getTotalWorldTime()+partialTicks)/20f)+1)/2)*45)+30;
			case LINKED:
				if (hasWorldObj() && getWorld().isRemote) {
					return syncedPitch;
				} else {
					Vec3 dir = getDirectionToTransmitter();
					Vec3 xz = new Vec3(dir.xCoord, 0, dir.zCoord);
					double xzLength = xz.lengthVector();
					return (float)-Math.toDegrees(MathHelper.atan2(dir.yCoord, xzLength));
				}
			default:
				return 20;
		}
	}

	public Vec3 getFacing(float partialTicks) {
		switch (getCurrentState()) {
			case ERROR:
				float yr = (float)Math.toRadians(getYaw(partialTicks));
				float pr = (float)Math.toRadians(getPitch(partialTicks));
				return new Vec3(-(sin(yr)*cos(pr)), sin(pr), -(cos(yr)*cos(pr)));
			case LINKED:
				return getDirectionToTransmitter();
			default:
				return new Vec3(0, 0.265, -0.735);
		}
	}
	
	@Override
	public Packet getDescriptionPacket() {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setFloat("Yaw", getYaw(0));
		tag.setFloat("Pitch", getPitch(0));
		return new S35PacketUpdateTileEntity(getPos(), getBlockMetadata(), tag);
	}
	
	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
		super.onDataPacket(net, pkt);
		syncedYaw = pkt.getNbtCompound().getFloat("Yaw");
		syncedPitch = pkt.getNbtCompound().getFloat("Pitch");
	}
	
	public boolean hasTransmitter() {
		return getTransmitter() != null;
	}
	
	public Transmitter getTransmitter() {
		if (!hasWorldObj()) return null;
		if (transmitter == null) return null;
		if (transmitterCache != null && transmitterCache.isValid()) return transmitterCache;
		if (hasController() && !getController().isCheckingInfiniteLoop()) {
			getController().checkInfiniteLoop();
		}
		Transmitter t = CoPo.getDataFor(getWorld()).getTransmitterById(transmitter);
		if (t == null || t.position.distanceSqToCenter(getPos().getX()+0.5, getPos().getY()+0.5, getPos().getZ()+0.5) > t.range*t.range) return null;
		getWorld().markBlockForUpdate(getPos());
		transmitterCache = t;
		return t;
	}
	
	public void setTransmitter(UUID transmitter) {
		this.transmitter = transmitter;
		transmitterCache = null;
	}
	
	private Vec3 getDirectionToTransmitter() {
		if (!hasTransmitter()) return new Vec3(0, 0, 0);
		BlockPos sub = getPos().subtract(getTransmitter().position);
		return new Vec3(sub).normalize();
	}
	
	@Override
	protected State getCurrentState() {
		if (hasWorldObj() && getWorld().isRemote) {
			IBlockState state = getWorld().getBlockState(getPos());
			if (state.getBlock() == CoPo.wireless_endpoint) {
				return state.getValue(BlockWirelessEndpoint.state);
			}
		}
		return hasTransmitter() ? State.LINKED : State.ERROR;
	}
	
	@Override
	public void writeToNBT(NBTTagCompound compound) {
		super.writeToNBT(compound);
		compound.setLong("TransmitterUUIDMost", transmitter.getMostSignificantBits());
		compound.setLong("TransmitterUUIDLeast", transmitter.getLeastSignificantBits());
	}
	
	@Override
	public void readFromNBT(NBTTagCompound compound) {
		super.readFromNBT(compound);
		setTransmitter(new UUID(compound.getLong("TransmitterUUIDMost"), compound.getLong("TransmitterUUIDLeast")));
	}

	public TileEntityController getTransmitterController() {
		if (!hasTransmitter()) return null;
		Transmitter t = getTransmitter();
		TileEntity te = getWorld().getTileEntity(t.position);
		if (te != null && te instanceof TileEntityWirelessTransmitter) {
			return ((TileEntityWirelessTransmitter)te).getController();
		}
		return null;
	}

}