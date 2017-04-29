package org.dave.ocrsdriver;

import com.raoulvdberge.refinedstorage.api.IRSAPI;
import com.raoulvdberge.refinedstorage.api.RSAPIInject;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTask;
import com.raoulvdberge.refinedstorage.api.network.INetworkMaster;
import com.raoulvdberge.refinedstorage.api.network.INetworkNode;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import li.cil.oc.api.Network;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.DriverSidedTileEntity;
import li.cil.oc.api.prefab.ManagedEnvironment;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import static com.raoulvdberge.refinedstorage.api.util.IComparer.COMPARE_DAMAGE;
import static com.raoulvdberge.refinedstorage.api.util.IComparer.COMPARE_NBT;

public class DriverNetworkNode extends DriverSidedTileEntity {
    @RSAPIInject
    public static IRSAPI rsAPI;

    @Override
    public Class<?> getTileEntityClass() {
        return INetworkNode.class;
    }

    @Override
    public ManagedEnvironment createEnvironment(World world, BlockPos pos, EnumFacing side) {
        return new Environment((INetworkNode) world.getTileEntity(pos));
    }

    public static final class Environment extends ManagedEnvironment {
        protected final INetworkNode tileEntity;

        public Environment(INetworkNode tileEntity) {
            this.tileEntity = tileEntity;
            this.setNode(Network.newNode(this, Visibility.Network).withComponent("rs_interface").create());
        }

        @Callback
        public Object[] isConnected(final Context context, final Arguments args) {
            return new Object[] { this.tileEntity.isConnected() };
        }

        @Callback
        public Object[] getEnergyUsage(final Context context, final Arguments args) {
            return new Object[] { this.tileEntity.getNetwork().getEnergyUsage() };
        }

        @Callback
        public Object[] getCraftingTasks(final Context context, final Arguments args) {
            return new Object[] { this.tileEntity.getNetwork().getCraftingTasks() };
        }

        @Callback
        public Object[] getPatterns(final Context context, final Arguments args) {
            return new Object[] { this.tileEntity.getNetwork().getPatterns() };
        }

        @Callback
        public Object[] hasPattern(final Context context, final Arguments args) {
            ItemStack stack = args.checkItemStack(0);

            return new Object[] { this.tileEntity.getNetwork().hasPattern(stack) };
        }

        @Callback
        public Object[] getMissingItems(final Context context, final Arguments args) {
            ItemStack stack = args.checkItemStack(0);
            if(!this.tileEntity.getNetwork().hasPattern(stack)) {
                throw new IllegalArgumentException("No pattern for this item stack exists");
            }

            int count = args.optInteger(1, 1);
            ICraftingPattern pattern = this.tileEntity.getNetwork().getPattern(stack);

            ICraftingTask task = this.tileEntity.getNetwork().createCraftingTask(stack, pattern, count);
            task.calculate();

            return new Object[] { task.getMissing().getStacks() };
        }

        @Callback
        public Object[] craftItem(final Context context, final Arguments args) {
            ItemStack stack = args.checkItemStack(0);
            if(!this.tileEntity.getNetwork().hasPattern(stack)) {
                throw new IllegalArgumentException("No pattern for this item stack exists");
            }

            int count = args.optInteger(1, 1);
            ICraftingPattern pattern = this.tileEntity.getNetwork().getPattern(stack);

            ICraftingTask task = this.tileEntity.getNetwork().createCraftingTask(stack, pattern, count);
            task.calculate();

            this.tileEntity.getNetwork().addCraftingTask(task);

            return new Object[] { };
        }

        @Callback
        public Object[] cancelCrafting(final Context context, final Arguments args) {
            ItemStack stack = args.checkItemStack(0);

            INetworkMaster grid = this.tileEntity.getNetwork();

            int count = 0;
            for(ICraftingTask task : grid.getCraftingTasks()) {
                if(rsAPI.getComparer().isEqual(task.getRequested(), stack, COMPARE_NBT | COMPARE_DAMAGE)) {
                    grid.cancelCraftingTask(task);
                    count++;
                }
            }

            return new Object[] { count };
        }

        @Callback
        public Object[] extractItem(final Context context, final Arguments args) {
            ItemStack stack = args.checkItemStack(0);
            if(stack.stackSize > stack.getMaxStackSize()) {
                stack.stackSize = stack.getMaxStackSize();
            }

            int count = args.checkInteger(1);
            EnumFacing facing = EnumFacing.getFront(args.checkInteger(2));

            TileEntity targetEntity = this.tileEntity.getNodeWorld().getTileEntity(this.tileEntity.getPosition().offset(facing));
            if(targetEntity == null || !targetEntity.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite())) {
                throw new IllegalArgumentException("No inventory on the given side");
            }

            IItemHandler handler = targetEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite());
            ItemStack extractedSim = this.tileEntity.getNetwork().extractItem(stack, count, true);
            if (extractedSim == null || extractedSim.stackSize == 0) {
                throw new IllegalArgumentException("Could not extract the specified item. Does it exist?");
            }

            int transferableAmount = extractedSim.stackSize;
            ItemStack insertedSim = ItemHandlerHelper.insertItemStacked(handler, extractedSim, true);
            if (insertedSim != null && insertedSim.stackSize > 0) {
                transferableAmount -= insertedSim.stackSize;
            }

            if (transferableAmount <= 0) {
                return new Object[] { 0 };
            }

            ItemStack extracted = this.tileEntity.getNetwork().extractItem(stack, transferableAmount, false);
            ItemHandlerHelper.insertItemStacked(handler, extracted, false);

            return new Object[] { transferableAmount };
        }

        @Callback
        public Object[] getFluids(final Context context, final Arguments args) {
            return new Object[]{ this.tileEntity.getNetwork().getFluidStorageCache().getList().getStacks() };
        }

        @Callback
        public Object[] getItem(final Context context, final Arguments args) {
            ItemStack stack = args.checkItemStack(0);
            boolean compareMeta = args.optBoolean(1, false);
            boolean compareNBT = args.optBoolean(2, false);

            int flag = 0;
            if(compareMeta)flag |= IComparer.COMPARE_DAMAGE;
            if(compareNBT)flag |= IComparer.COMPARE_NBT;

            return new Object[] { this.tileEntity.getNetwork().getItemStorageCache().getList().get(stack, flag) };
        }

        @Callback
        public Object[] getItems(final Context context, final Arguments args) {
            return new Object[]{ this.tileEntity.getNetwork().getItemStorageCache().getList().getStacks() };
        }

    }
}
