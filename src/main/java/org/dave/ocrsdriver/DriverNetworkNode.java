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
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.Map;

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
        protected final INetworkNode node;

        public Environment(INetworkNode node) {
            this.node = node;
            this.setNode(Network.newNode(this, Visibility.Network).withComponent("rs_interface").create());
        }

        @Callback
        public Object[] isConnected(final Context context, final Arguments args) {
            if (node.getNetwork() == null) {
                return new Object[]{null, "not connected"};
            }

            return new Object[] { this.node.isConnected() };
        }

        @Callback
        public Object[] getEnergyUsage(final Context context, final Arguments args) {
            if (node.getNetwork() == null) {
                return new Object[]{null, "not connected"};
            }

            return new Object[] { this.node.getNetwork().getEnergyUsage() };
        }

        @Callback
        public Object[] getTasks(final Context context, final Arguments args) {
            if (node.getNetwork() == null) {
                return new Object[]{null, "not connected"};
            }

            return new Object[] { this.node.getNetwork().getCraftingTasks() };
        }

        @Callback
        public Object[] getPatterns(final Context context, final Arguments args) {
            if (node.getNetwork() == null) {
                return new Object[]{null, "not connected"};
            }

            return new Object[] { this.node.getNetwork().getPatterns() };
        }

        @Callback
        public Object[] hasPattern(final Context context, final Arguments args) {
            if (node.getNetwork() == null) {
                return new Object[]{null, "not connected"};
            }

            ItemStack stack = args.checkItemStack(0);

            return new Object[] { this.node.getNetwork().hasPattern(stack) };
        }

        @Callback
        public Object[] getMissingItems(final Context context, final Arguments args) {
            if (node.getNetwork() == null) {
                return new Object[]{null, "not connected"};
            }

            ItemStack stack = args.checkItemStack(0);
            if(!this.node.getNetwork().hasPattern(stack)) {
                throw new IllegalArgumentException("No pattern for this item stack exists");
            }

            int count = args.optInteger(1, 1);
            ICraftingPattern pattern = this.node.getNetwork().getPattern(stack);

            ICraftingTask task = this.node.getNetwork().createCraftingTask(stack, pattern, count);
            task.calculate();

            return new Object[] { task.getMissing().getStacks() };
        }

        @Callback
        public Object[] craftItem(final Context context, final Arguments args) {
            if (node.getNetwork() == null) {
                return new Object[]{null, "not connected"};
            }

            ItemStack stack = args.checkItemStack(0);
            if(!this.node.getNetwork().hasPattern(stack)) {
                throw new IllegalArgumentException("No pattern for this item stack exists");
            }

            int count = args.optInteger(1, 1);
            ICraftingPattern pattern = this.node.getNetwork().getPattern(stack);

            ICraftingTask task = this.node.getNetwork().createCraftingTask(stack, pattern, count);
            task.calculate();

            this.node.getNetwork().addCraftingTask(task);

            return new Object[] { };
        }

        @Callback
        public Object[] cancelCrafting(final Context context, final Arguments args) {
            if (node.getNetwork() == null) {
                return new Object[]{null, "not connected"};
            }

            ItemStack stack = args.checkItemStack(0);

            INetworkMaster grid = this.node.getNetwork();

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
        public Object[] extractFluid(final Context context, final Arguments args) {
            if (node.getNetwork() == null) {
                return new Object[]{null, "not connected"};
            }

            // First argument: the fluid stack to extract
            // There is no args.checkFluidStack(), we have to deal with this ourselves
            Map<String, Object> fluidMap = args.checkTable(0);
            if(!fluidMap.containsKey("name") || !(fluidMap.get("name") instanceof String) || ((String) fluidMap.get("name")).length() == 0) {
                throw new IllegalArgumentException("no fluid name");
            }
            String fluid = (String) fluidMap.get("name");

            // Second argument: the amount of liquid to extract, at least 1mb
            int amount = Math.max(1, args.checkInteger(1));

            // With the amount ready, we can actually try to create a fluid stack for the given fluid
            FluidStack stack = FluidRegistry.getFluidStack(fluid, amount);
            if(stack == null) {
                throw new IllegalArgumentException("invalid fluid stack, does not exist");
            }

            // Third argument: which direction to extract to
            EnumFacing facing = EnumFacing.getFront(args.optInteger(2, 0));

            // Get the tile-entity on the specified side
            TileEntity targetEntity = node.getNetwork().getNetworkWorld().getTileEntity(node.getPosition().offset(facing));
            if(targetEntity == null || !targetEntity.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, facing.getOpposite())) {
                throw new IllegalArgumentException("No fluid tank on the given side");
            }

            FluidStack extractedSim = node.getNetwork().extractFluid(stack, amount, true);
            if(extractedSim == null || extractedSim.amount <= 0) {
                return new Object[]{null, "could not extract the specified fluid"};
            }

            // Simulate inserting the fluid and see how much we were able to insert
            IFluidHandler handler = targetEntity.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, facing.getOpposite());
            int filledAmountSim = handler.fill(extractedSim, false);
            if(filledAmountSim <= 0) {
                return new Object[]{0};
            }

            // Actually do it and return how much fluid we've inserted
            FluidStack extracted = node.getNetwork().extractFluid(stack, amount, false);
            handler.fill(extracted, true);

            return new Object[] { filledAmountSim };
        }

        @Callback
        public Object[] extractItem(final Context context, final Arguments args) {
            if (node.getNetwork() == null) {
                return new Object[]{null, "not connected"};
            }

            // First argument: the itemstack to extract
            ItemStack stack = args.checkItemStack(0);

            // Second argument: the number of items to extract, at least 1 ...
            int count = Math.max(1, args.optInteger(1, 1));

            // ... and at most a full stack
            count = Math.min(count, stack.getMaxStackSize());

            EnumFacing facing = EnumFacing.getFront(args.optInteger(2, 0));

            TileEntity targetEntity = this.node.getNodeWorld().getTileEntity(this.node.getPosition().offset(facing));
            if(targetEntity == null || !targetEntity.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite())) {
                throw new IllegalArgumentException("No inventory on the given side");
            }

            IItemHandler handler = targetEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite());
            ItemStack extractedSim = this.node.getNetwork().extractItem(stack, count, true);
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

            ItemStack extracted = this.node.getNetwork().extractItem(stack, transferableAmount, false);
            ItemHandlerHelper.insertItemStacked(handler, extracted, false);

            return new Object[] { transferableAmount };
        }

        @Callback
        public Object[] getFluid(final Context context, final Arguments args) {
            if (node.getNetwork() == null) {
                return new Object[]{null, "not connected"};
            }

            // There is no args.checkFluidStack(), we have to deal with this ourselves
            Map<String, Object> fluidMap = args.checkTable(0);
            if(!fluidMap.containsKey("name") || !(fluidMap.get("name") instanceof String) || ((String) fluidMap.get("name")).length() == 0) {
                throw new IllegalArgumentException("no fluid name");
            }

            String fluid = (String) fluidMap.get("name");

            FluidStack needle = FluidRegistry.getFluidStack(fluid, 1000);
            if(needle == null) {
                throw new IllegalArgumentException("invalid fluid stack, does not exist");
            }

            return new Object[]{ node.getNetwork().getFluidStorageCache().getList().get(needle) };
        }

        @Callback
        public Object[] getFluids(final Context context, final Arguments args) {
            if (node.getNetwork() == null) {
                return new Object[]{null, "not connected"};
            }

            return new Object[]{ this.node.getNetwork().getFluidStorageCache().getList().getStacks() };
        }

        @Callback
        public Object[] getItem(final Context context, final Arguments args) {
            if (node.getNetwork() == null) {
                return new Object[]{null, "not connected"};
            }

            ItemStack stack = args.checkItemStack(0);
            boolean compareMeta = args.optBoolean(1, false);
            boolean compareNBT = args.optBoolean(2, false);
            boolean compareOreDict = args.optBoolean(3, true);

            int flag = 0;
            if(compareMeta)flag |= IComparer.COMPARE_DAMAGE;
            if(compareNBT)flag |= IComparer.COMPARE_NBT;
            if(compareOreDict)flag |= IComparer.COMPARE_OREDICT;

            return new Object[] { this.node.getNetwork().getItemStorageCache().getList().get(stack, flag) };
        }

        @Callback
        public Object[] getItems(final Context context, final Arguments args) {
            if (node.getNetwork() == null) {
                return new Object[]{null, "not connected"};
            }

            return new Object[]{ this.node.getNetwork().getItemStorageCache().getList().getStacks() };
        }

    }
}
