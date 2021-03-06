package com.elytradev.correlated.entity;

import com.elytradev.correlated.init.CConfig;
import com.elytradev.correlated.init.CItems;
import com.elytradev.correlated.init.CSoundEvents;
import com.elytradev.correlated.init.CStacks;
import com.elytradev.correlated.network.fx.DungeonTransitionMessage;
import com.elytradev.correlated.network.fx.DungeonTransitionMessage.GlitchState;
import com.elytradev.correlated.storage.NetworkType;
import com.elytradev.correlated.world.DungeonPlayer;
import com.elytradev.correlated.world.LimboProvider;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.ListenableFutureTask;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;

public class EntityThrownItem extends EntityThrowable {

	public EntityThrownItem(World worldIn, double x, double y, double z) {
		super(worldIn, x, y, z);
	}

	public EntityThrownItem(World worldIn, EntityLivingBase throwerIn) {
		super(worldIn, throwerIn);
	}

	public EntityThrownItem(World worldIn) {
		super(worldIn);
	}

	private static final DataParameter<ItemStack> STACK = EntityDataManager.createKey(EntityThrownItem.class, DataSerializers.ITEM_STACK);
	private boolean noTeleport = false;
	
	@Override
	protected void entityInit() {
		super.entityInit();
		dataManager.register(STACK, ItemStack.EMPTY);
		timeUntilPortal = 1000;
	}
	
	@Override
	public String getName() {
		return getStack().getUnlocalizedName();
	}
	
	@Override
	public ITextComponent getDisplayName() {
		return getStack().getTextComponent();
	}
	
	public ItemStack getStack() {
		return dataManager.get(STACK);
	}
	
	public void setStack(ItemStack stack) {
		dataManager.set(STACK, stack);
	}
	
	@Override
	protected void onImpact(RayTraceResult result) {
		if (!getStack().isEmpty()) {
			if (result.entityHit == getThrower() || getThrower() == null && result.entityHit instanceof EntityPlayer) return;
			if (!world.isRemote && result.entityHit != null) {
				result.entityHit.attackEntityFrom(DamageSource.causeThrownDamage(this, getThrower()), 0);
			}
			if (getStack() != null && getStack().getItem() == CItems.MISC) {
				if (getStack().getMetadata() == 6 && !world.isRemote) {
					if (getThrower() instanceof EntityPlayerMP && !noTeleport) {
						EntityPlayerMP p = (EntityPlayerMP)getThrower();
						if (p.connection.getNetworkManager().isChannelOpen() && p.world == this.world && !p.isPlayerSleeping()) {
							if (p.isRiding()) {
								p.dismountRidingEntity();
							}

							p.setPositionAndUpdate(posX, posY, posZ);
							p.fallDistance = 0;
							p.attackEntityFrom(DamageSource.FALL, 0.01f);
							
							setDead();
							
							p.mcServer.futureTaskQueue.add(ListenableFutureTask.create(() -> {
								NBTTagCompound oldEntity = p.writeToNBT(new NBTTagCompound());
								DungeonPlayer player = new DungeonPlayer(p.getGameProfile(), p.inventory.getFirstEmptyStack(), oldEntity);
								long hashCode = 1;
								long prime = 59;
								int rd = 2;
								Vec3i radius = new Vec3i(rd, rd, rd);
								BlockPos pos = result.getBlockPos();
								if (pos == null) {
									pos = new BlockPos((int)result.hitVec.x, (int)result.hitVec.y, (int)result.hitVec.z);
								}
								for (BlockPos bp : BlockPos.getAllInBoxMutable(pos.subtract(radius), pos.add(radius))) {
									IBlockState state = world.getBlockState(bp);
									state = state.getActualState(world, bp);
									String stateString = state.toString();
								
								    for (int i = 0; i < stateString.length(); i++) {
								        hashCode = (prime * hashCode) + stateString.charAt(i);
								    }
								}
								player.setSeed(hashCode);
								int dim = CConfig.limboDimId;
								if (ForgeHooks.onTravelToDimension(p, dim)) {
									new DungeonTransitionMessage(GlitchState.CORRUPTING, (float)posX, (float)posY, (float)posZ, Long.toHexString(hashCode)).sendTo(p);
									WorldServer dest = p.mcServer.getWorld(dim);
									if (dest.provider instanceof LimboProvider) {
										((LimboProvider)dest.provider).addEnteringPlayer(player);
									}
									PlayerList r = p.mcServer.getPlayerList();
									r.updatePermissionLevel(p);
									p.setDead();
								}
							}, null));
						}
						return;
					}
				} else if (getStack().getMetadata() == 8) {
					playSound(CSoundEvents.DATA_CORE_SHATTER, 1f, 0.875f+(rand.nextFloat()/4));
					if (!world.isRemote) {
						for (ItemStack is : Iterables.transform(CItems.DRIVE.getTypes(getStack()), NetworkType::getStack)) {
							int amt = is.getCount();
							while (amt > 0) {
								ItemStack stack = is.copy();
								stack.setCount(Math.min(10000, amt));
								EntityItem entityitem = new EntityItem(this.world, this.posX, this.posY, this.posZ, stack);
								entityitem.setDefaultPickupDelay();
								// You just KNOW someone is going to use MAX_VALUE as a lifespan
								entityitem.lifespan = Ints.saturatedCast(entityitem.lifespan*64L);
								if (captureDrops) {
									capturedDrops.add(entityitem);
								} else {
									world.spawnEntity(entityitem);
								}
								amt -= stack.getCount();
							}
						}
					}
					setDead();
					if (world instanceof WorldServer) {
						((WorldServer)world).spawnParticle(EnumParticleTypes.ITEM_CRACK,
								result.hitVec.x, result.hitVec.y, result.hitVec.z, 80,
								0, 0, 0, 0.15, Item.getIdFromItem(getStack().getItem()), getStack().getMetadata());
					}
					return;
				} else if (getStack().getMetadata() == 3) {
					// I like the idea of this, but it ends up being extremely overpowered.
					/*
					ItemStack nw = CStacks.dataCore();
					boolean absorbedAnyItem = false;
					for (EntityItem ei : world.getEntitiesWithinAABB(EntityItem.class, getEntityBoundingBox().expand(1, 1, 1))) {
						if (!ei.isDead) {
							InsertResult ir = CItems.DRIVE.addItem(nw, ei.getEntityItem(), false);
							absorbedAnyItem = true;
							if (ir.stack == null || ir.stack.isEmpty()) {
								ei.setDead();
							} else {
								ei.setEntityItemStack(ir.stack);
							}
						}
					}
					if (absorbedAnyItem) {
						setStack(nw);
						motionX = 0;
						motionY = 0;
						motionZ = 0;
					}
					*/
				}
			}
			if (!world.isRemote) {
				EntityItem item = new EntityItem(world, result.hitVec.x, result.hitVec.y, result.hitVec.z, getStack());
				world.spawnEntity(item);
			}
		}
		setDead();
	}
	
	@Override
	public void setPortal(BlockPos pos) {
		if (!world.isRemote && getStack() != null && getStack().getItem() == CItems.MISC) {
			if (getStack().getMetadata() == 3) {
				world.playSound(null, posX, posY, posZ, CSoundEvents.CONVERT, SoundCategory.PLAYERS, 1, rand.nextFloat()+0.75f);
				if (world instanceof WorldServer) {
					((WorldServer)world).spawnParticle(EnumParticleTypes.REDSTONE, posX, posY, posZ, 100, 0.2, 0.2, 0.2, 100);
				}
				setStack(CStacks.unstablePearl(getStack().getCount()));
				noTeleport = true;
			} else if (getStack().getMetadata() == 6 && !noTeleport) {
				playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
				if (world instanceof WorldServer) {
					((WorldServer)world).spawnParticle(EnumParticleTypes.ITEM_CRACK,
							posX, posY, posZ, 120,
							0, 0, 0, 1.0, Item.getIdFromItem(CItems.MISC), 3);
							
					((WorldServer)world).spawnParticle(EnumParticleTypes.ITEM_CRACK,
							posX, posY, posZ, 40,
							0, 0, 0, 0.25, Item.getIdFromItem(CItems.MISC), 3);
					
					((WorldServer)world).spawnParticle(EnumParticleTypes.ITEM_CRACK,
							posX, posY, posZ, 80,
							0, 0, 0, 0.05, Item.getIdFromItem(CItems.MISC), 3);
					
					((WorldServer)world).spawnParticle(EnumParticleTypes.SMOKE_LARGE,
							posX, posY, posZ, 100,
							0, 0, 0, 0.05);
					((WorldServer)world).spawnParticle(EnumParticleTypes.REDSTONE, posX, posY, posZ, 100, 0.2, 0.2, 0.2, 100);
				}
				setDead();
			}
		}
	}
	
}
