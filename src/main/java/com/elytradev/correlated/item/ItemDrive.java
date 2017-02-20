package com.elytradev.correlated.item;

import java.util.List;
import java.util.Locale;

import com.elytradev.correlated.Correlated;
import com.elytradev.correlated.block.BlockDriveBay;
import com.elytradev.correlated.block.BlockMemoryBay;
import com.elytradev.correlated.helper.ItemStacks;
import com.elytradev.correlated.helper.Numbers;
import com.elytradev.correlated.proxy.ClientProxy;
import com.google.common.collect.Lists;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;

public class ItemDrive extends Item {
	public enum Priority {
		HIGHEST(TextFormatting.RED),
		HIGHER(TextFormatting.DARK_RED),
		HIGH(TextFormatting.GRAY),
		DEFAULT(TextFormatting.GRAY),
		LOW(TextFormatting.GRAY),
		LOWER(TextFormatting.DARK_GREEN),
		LOWEST(TextFormatting.GREEN);
		public final String lowerName = name().toLowerCase(Locale.ROOT);
		public final TextFormatting color;
		private Priority(TextFormatting color) {
			this.color = color;
		}
	}
	public enum PartitioningMode {
		/*BLACKLIST, TODO*/ NONE, WHITELIST;
		public final String lowerName = name().toLowerCase(Locale.ROOT);
	}

	private final int[] tierSizes = {
			1024 * 8,
			4096 * 8,
			16384 * 8,
			65536 * 8,
			-1,
			131072 * 8
	};

	public ItemDrive() {
		setMaxStackSize(1);
	}

	public int getFullnessColor(ItemStack stack) {
		boolean dirty = stack.hasTagCompound() && stack.getTagCompound().getBoolean("Dirty") && itemRand.nextBoolean();
		if (dirty && itemRand.nextInt(20) == 0) {
			stack.getTagCompound().removeTag("Dirty");
		}
		int idx;
		if (stack.getItemDamage() == 4) {
			int x = (int)(((MathHelper.sin(ClientProxy.ticks / 20f)+1) / 2f)*255);
			idx = x;
			if (!dirty) {
				idx += 256;
			}
		} else {
			idx = 512;
			float usedBits = getKilobitsUsed(stack)/(float)getMaxKilobits(stack);
			int x = (int)(usedBits*254f);
			idx += x;
			if (!dirty) {
				idx += 256;
			}
		}
		return Correlated.proxy.getColor("fullness", idx);
	}

	public int getTierColor(ItemStack stack) {
		if (stack.getMetadata() == 4) return Correlated.proxy.getColor("tier", 16);
		int meta = stack.getMetadata();
		if (meta > 4) {
			meta--;
		}
		return Correlated.proxy.getColor("tier", meta);
	}

	public int getBaseColor(ItemStack stack) {
		return stack.getItemDamage() == 4 ? Correlated.proxy.getColor("other", 33) : Correlated.proxy.getColor("other", 49);
	}
	
	@Override
	public void addInformation(ItemStack stack, EntityPlayer playerIn, List<String> tooltip, boolean advanced) {
		tooltip.add(I18n.translateToLocalFormatted("tooltip.correlated.rf_usage", getRFConsumptionRate(stack)));
		if (stack.getItemDamage() == 4) {
			int i = 0;
			while (I18n.canTranslate("tooltip.correlated.void_drive." + i)) {
				tooltip.add(I18n.translateToLocalFormatted("tooltip.correlated.void_drive." + i));
				i++;
			}
		} else {
			int bytesUsed = (getKilobitsUsed(stack) / 8)*1024;
			int bytesMax = (getMaxKilobits(stack) / 8)*1024;

			int bytesPercent = (int) (((double) bytesUsed / (double) bytesMax) * 100);

			String max = Numbers.humanReadableBytes(bytesMax);
			tooltip.add(I18n.translateToLocalFormatted("tooltip.correlated.bytes_used", Numbers.humanReadableBytes(bytesUsed), max, bytesPercent));
		}
	}

	public int getRFConsumptionRate(ItemStack stack) {
		if (stack.getItemDamage() == 4) {
			return Correlated.inst.voidDriveUsage;
		}
		int dmg = stack.getItemDamage() + 1;
		if (stack.getItemDamage() == 6) {
			dmg = 8;
		}
		return ((int) Math.pow(Correlated.inst.driveRfUsagePow, dmg))/Correlated.inst.driveRfUsageDiv;
	}

	@Override
	public boolean getHasSubtypes() {
		return true;
	}

	@Override
	public String getItemStackDisplayName(ItemStack stack) {
		if (stack.getMetadata() == 4) return I18n.translateToLocal("item.correlated.drive.void.name");
		return I18n.translateToLocalFormatted("item.correlated.drive.normal.name", Numbers.humanReadableBytes((getMaxKilobits(stack)/8)*1024));
	}

	@Override
	public void getSubItems(Item itemIn, CreativeTabs tab, NonNullList<ItemStack> subItems) {
		for (int i = 0; i < tierSizes.length; i++) {
			subItems.add(new ItemStack(itemIn, 1, i));
		}
	}

	public int getMaxKilobits(ItemStack stack) {
		return tierSizes[stack.getItemDamage() % tierSizes.length];
	}

	public int getTypeAllocationKilobits(ItemStack stack, NBTTagCompound prototype) {
		return 32 + getNBTComplexity(prototype == null ? null : prototype.getTag("tag"));
	}
	
	public static int getNBTComplexity(NBTBase base) {
		if (base == null) return 0;
		int complexity = 0;
		switch (base.getId()) {
			case NBT.TAG_BYTE:
				complexity += 4;
				break;
			case NBT.TAG_SHORT:
				complexity += 8;
				break;
			case NBT.TAG_INT:
				complexity += 16;
				break;
			case NBT.TAG_LONG:
				complexity += 32;
				break;
			case NBT.TAG_BYTE_ARRAY:
				complexity += 8;
				complexity += ((NBTTagByteArray)base).getByteArray().length*4;
				break;
			case NBT.TAG_INT_ARRAY:
				complexity += 8;
				complexity += ((NBTTagIntArray)base).getIntArray().length*16;
				break;
			case NBT.TAG_STRING:
				String str = ((NBTTagString)base).getString();
				complexity += getStringComplexity(str);
				break;
			case NBT.TAG_LIST:
				NBTTagList li = ((NBTTagList)base);
				complexity += 4;
				for (int i = 0; i < li.tagCount(); i++) {
					complexity += getNBTComplexity(li.get(i));
				}
				break;
			case NBT.TAG_COMPOUND:
				NBTTagCompound compound = ((NBTTagCompound)base);
				complexity += 4;
				for (String k : compound.getKeySet()) {
					complexity += getStringComplexity(k);
					NBTBase tag = compound.getTag(k);
					if ("Count".equals(k) || "Amount".equals(k) && tag instanceof NBTPrimitive) {
						// !!
						complexity += ((NBTPrimitive)tag).getInt();
					}
					complexity += getNBTComplexity(compound.getTag(k));
				}
				break;
		}
		return complexity;
	}

	public static int getStringComplexity(String str) {
		int complexity = 8;
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if (c < 0xFF) {
				complexity += 4;
			} else {
				complexity += 8;
			}
		}
		return complexity;
	}

	public Priority getPriority(ItemStack stack) {
		return ItemStacks.getEnum(stack, "Priority", Priority.class)
				.or(Priority.DEFAULT);
	}

	public void setPriority(ItemStack stack, Priority priority) {
		ItemStacks.ensureHasTag(stack).getTagCompound().setString("Priority", priority.name());
	}

	public PartitioningMode getPartitioningMode(ItemStack stack) {
		return ItemStacks.getEnum(stack, "PartitioningMode", PartitioningMode.class)
				.or(PartitioningMode.NONE);
	}

	public void setPartitioningMode(ItemStack stack, PartitioningMode mode) {
		ItemStacks.ensureHasTag(stack).getTagCompound().setString("PartitioningMode", mode.name());
	}

	public void markDirty(ItemStack stack) {
		ItemStacks.ensureHasTag(stack).getTagCompound().setBoolean("Dirty", true);
	}

	@Override
	public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand hand) {
		ItemStack itemStackIn = playerIn.getHeldItem(hand);
		if (playerIn.isSneaking()) {
			playerIn.playSound(Correlated.drive_disassemble, 0.4f, 0.875f+(itemRand.nextFloat()/4));
			NBTTagList ingredients = ItemStacks.getCompoundList(itemStackIn, "Ingredients");
			if (!worldIn.isRemote) {
				for (int i = 0; i < ingredients.tagCount(); i++) {
					ItemStack is = new ItemStack(ingredients.getCompoundTagAt(i));
					if (is.getItem() == Correlated.misc && (is.getMetadata() == 3 || is.getMetadata() == 8)) continue;
					playerIn.entityDropItem(is, 0.5f);
				}
			}
			if (!ItemStacks.getCompoundList(itemStackIn, "Data").hasNoTags()) {
				ItemStack dataCore = new ItemStack(Correlated.misc, 1, 8);
				dataCore.setTagCompound(itemStackIn.getTagCompound().copy());
				return ActionResult.newResult(EnumActionResult.SUCCESS, dataCore);
			} else {
				return ActionResult.newResult(EnumActionResult.SUCCESS, new ItemStack(Correlated.misc, 1, 3));
			}
		} else {
			Vec3d eyes = new Vec3d(playerIn.posX, playerIn.posY + playerIn.getEyeHeight(), playerIn.posZ);
			Vec3d look = playerIn.getLookVec();
			Vec3d origin = eyes.addVector(look.xCoord * 4, look.yCoord * 4, look.zCoord * 4);
			RayTraceResult rtr = playerIn.world.rayTraceBlocks(eyes, origin, false, false, true);
			if (rtr.typeOfHit == Type.BLOCK) {
				Block b = worldIn.getBlockState(rtr.getBlockPos()).getBlock();
				if (b instanceof BlockDriveBay || b instanceof BlockMemoryBay) {
					return ActionResult.newResult(EnumActionResult.FAIL, itemStackIn);
				}
			}
			playerIn.openGui(Correlated.inst, 1, worldIn, playerIn.inventory.currentItem, 0, 0);
			return ActionResult.newResult(EnumActionResult.SUCCESS, itemStackIn);
		}
	}

	// all this code should probably be refactored into some sort of general
	// "NetworkContents" class at some point

	public int getKilobitsUsed(ItemStack stack) {
		if (!stack.hasTagCompound()) return 0;
		if (!stack.getTagCompound().hasKey("Data", NBT.TAG_LIST) || ItemStacks.getCompoundList(stack, "Data").hasNoTags()) {
			return stack.getTagCompound().getInteger("UsedBits");
		}
		NBTTagList list = ItemStacks.getCompoundList(stack, "Data");
		int used = 0;
		for (int i = 0; i < list.tagCount(); i++) {
			used += getTypeAllocationKilobits(stack, list.getCompoundTagAt(i).getCompoundTag("Prototype"));
			used += list.getCompoundTagAt(i).getInteger("Count");
		}
		return used;
	}

	public int getKilobitsFree(ItemStack stack) {
		if (getMaxKilobits(stack) == -1) return Integer.MAX_VALUE;
		return getMaxKilobits(stack) - getKilobitsUsed(stack);
	}

	protected NBTTagCompound createPrototype(ItemStack item) {
		if (item == null)
			return null;
		NBTTagCompound prototype = item.writeToNBT(new NBTTagCompound());
		prototype.removeTag("Count");
		return prototype;
	}

	protected NBTTagCompound findDataForPrototype(ItemStack drive, NBTTagCompound prototype) {
		int index = findDataIndexForPrototype(drive, prototype);
		if (index == -1) return null;
		return ItemStacks.getCompoundList(drive, "Data").getCompoundTagAt(index);
	}
	protected int findDataIndexForPrototype(ItemStack drive, NBTTagCompound prototype) {
		if (prototype == null)
			return -1;
		NBTTagList list = ItemStacks.getCompoundList(drive, "Data");
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound tag = list.getCompoundTagAt(i);
			if (tag.getCompoundTag("Prototype").equals(prototype)) {
				return i;
			}
		}
		return -1;
	}

	public int getKilobitsFreeFor(ItemStack drive, ItemStack item) {
		if (getMaxKilobits(drive) == -1) return Integer.MAX_VALUE;
		NBTTagCompound prototype = createPrototype(item);
		NBTTagCompound data = findDataForPrototype(drive, prototype);
		if (data != null) {
			return getKilobitsFree(drive);
		} else if (getPartitioningMode(drive) == PartitioningMode.NONE) {
			return Math.max(0, getKilobitsFree(drive) - getTypeAllocationKilobits(drive, prototype));
		}
		return 0;
	}

	/**
	 * Insert as many items as possible from the given stack into a drive.
	 * <p>
	 * The stackSize of the passed stack will be affected. Return value is for
	 * convenience, and will be null if all items are taken.
	 *
	 * @param drive
	 *            The drive to affect
	 * @param item
	 *            The item to add
	 * @return The item passed in
	 */
	public ItemStack addItem(ItemStack drive, ItemStack item) {
		if (getMaxKilobits(drive) == -1) {
			if (getPartitioningMode(drive) == PartitioningMode.NONE || findDataIndexForPrototype(drive, createPrototype(item)) != -1) {
				item.setCount(0);
				markDirty(drive);
			}
			return item;
		}
		int bitsFree = getKilobitsFreeFor(drive, item);
		int amountTaken = Math.min(item.getCount(), bitsFree);
		int current = getAmountStored(drive, item);
		if (amountTaken > 0) {
			setAmountStored(drive, item, current+amountTaken);
			item.setCount(item.getCount()-amountTaken);
			markDirty(drive);
		}
		return item;
	}

	/**
	 * Take as many items as possible, up to the passed limit, from a drive into
	 * a new stack.
	 * <p>
	 * The stackSize of the passed stack will <b>not</b> be affected, thanks to
	 * Mojang and their air stacks.
	 *
	 * @param drive
	 *            The drive to affect
	 * @param item
	 *            The item to remove
	 * @param amountWanted
	 *            The maximum amount to extract
	 */
	public ItemStack removeItems(ItemStack drive, ItemStack prototype, int amountWanted) {
		if (getMaxKilobits(drive) == -1) return ItemStack.EMPTY;
		ItemStack stack = prototype.copy();
		stack.setCount(0);
		int stored = getAmountStored(drive, prototype);
		int amountGiven = Math.min(amountWanted, stored);
		if (amountGiven > 0) {
			setAmountStored(drive, prototype, stored-amountGiven);
			stack.setCount(stack.getCount() + amountGiven);
			markDirty(drive);
		}
		return stack;
	}

	public int getAmountStored(ItemStack drive, ItemStack item) {
		if (getMaxKilobits(drive) == -1) return 0;
		NBTTagCompound data = findDataForPrototype(drive, createPrototype(item));
		if (data == null) return 0;
		return data.getInteger("Count");
	}

	public void setAmountStored(ItemStack drive, ItemStack item, int amount) {
		if (getMaxKilobits(drive) == -1) return;
		NBTTagCompound prototype = createPrototype(item);
		NBTTagList list = ItemStacks.getCompoundList(drive, "Data");
		int index = findDataIndexForPrototype(drive, prototype);
		if (index == -1) {
			allocateType(drive, item, item.getCount());
		} else {
			if (amount <= 0 && getPartitioningMode(drive) == PartitioningMode.NONE) {
				deallocateType(drive, item);
			} else {
				list.getCompoundTagAt(index).setInteger("Count", amount);
			}
		}
		markDirty(drive);
	}

	/**
	 * Creates a list of "prototype" (stack size one) itemstacks based on the
	 * stored data in the given drive.
	 * <p>
	 * As these are newly created, it is safe to just modify the stack size and
	 * use them without copying.
	 */
	public List<ItemStack> getPrototypes(ItemStack drive) {
		NBTTagList list = ItemStacks.getCompoundList(drive, "Data");
		List<ItemStack> rtrn = Lists.newArrayList();
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound tag = list.getCompoundTagAt(i);
			ItemStack is = new ItemStack(tag.getCompoundTag("Prototype"));
			is.setCount(1);
			rtrn.add(is);
		}
		return rtrn;
	}

	/**
	 * Creates a list of itemstacks based on the stored data in the given drive.
	 * <p>
	 * Unlike the getPrototypes method, these itemstacks will have stack sizes
	 * matching the amount of items in the drive. These can be potentially
	 * insane values that are <i>far</i> outside the item's max stack size
	 * range. Always use splitStack before passing to another method. Also
	 * unlike getPrototypes, this method will skip items that are partitioned
	 * but have none stored.
	 * <p>
	 * As these are newly created, it is safe to just modify the stack
	 * size and use them without copying.
	 */
	public List<ItemStack> getTypes(ItemStack drive) {
		if (getMaxKilobits(drive) == -1) return Lists.newArrayList();
		NBTTagList list = ItemStacks.getCompoundList(drive, "Data");
		List<ItemStack> rtrn = Lists.newArrayList();
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound tag = list.getCompoundTagAt(i);
			int count = tag.getInteger("Count");
			if (count > 0) {
				ItemStack is = new ItemStack(tag.getCompoundTag("Prototype"));
				is.setCount(count);
				rtrn.add(is);
			}
		}
		return rtrn;
	}

	/**
	 * Forcefully allocates a type for the given item. It is the
	 * responsibility of the caller to ensure there is space for
	 * the type. Does nothing if there is already an allocation
	 * for this type. Allocation will succeed even if the count
	 * is zero.
	 */
	public void allocateType(ItemStack drive, ItemStack item, int count) {
		NBTTagCompound prototype = createPrototype(item);
		int idx = findDataIndexForPrototype(drive, prototype);
		if (idx == -1) {
			NBTTagCompound data = new NBTTagCompound();
			data.setTag("Prototype", prototype);
			data.setInteger("Count", count);
			ItemStacks.getCompoundList(drive, "Data").appendTag(data);
			markDirty(drive);
		}
	}

	/**
	 * Forcefully deallocates a type for the given item. If there
	 * were any items of the given type stored in this drive,
	 * they will be deleted.
	 */
	public void deallocateType(ItemStack drive, ItemStack item) {
		NBTTagCompound prototype = createPrototype(item);
		int idx = findDataIndexForPrototype(drive, prototype);
		if (idx != -1) {
			ItemStacks.getCompoundList(drive, "Data").removeTag(idx);
			markDirty(drive);
		}
	}

}