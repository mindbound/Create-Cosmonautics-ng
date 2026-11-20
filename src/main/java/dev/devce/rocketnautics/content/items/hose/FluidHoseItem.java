package dev.devce.rocketnautics.content.items.hose;

import com.simibubi.create.content.fluids.FluidPropagator;
import dev.devce.rocketnautics.content.blocks.hose.HoseAnchorBlock;
import dev.devce.rocketnautics.registry.RocketBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import org.jetbrains.annotations.Nullable;

public class FluidHoseItem extends Item {

    // RopeStrandHolderBehaviorMixin refuses ropes on Hose Anchors unless they are created while this is set
    private static final ThreadLocal<Boolean> CONNECTING = ThreadLocal.withInitial(() -> false);

    public FluidHoseItem(Properties properties) {
        super(properties);
    }

    /** True while a Fluid Hose is creating a hose; only then may a rope strand be tied to a Hose Anchor. */
    public static boolean isConnecting() {
        return CONNECTING.get();
    }

    // CustomData.update drops the component once it is empty, so a cleared hose stacks with fresh ones again
    private void setFirstPos(ItemStack stack, BlockPos pos, Direction face) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putLong("FirstPos", pos.asLong());
            tag.putInt("FirstFace", face.ordinal());
        });
    }

    private BlockPos getFirstPos(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        if (customData.contains("FirstPos")) {
            return BlockPos.of(customData.copyTag().getLong("FirstPos"));
        }
        return null;
    }

    private Direction getFirstFace(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        if (customData.contains("FirstFace")) {
            return Direction.values()[customData.copyTag().getInt("FirstFace")];
        }
        return null;
    }

    private void clearFirstPos(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove("FirstPos");
            tag.remove("FirstFace");
        });
    }

    private static void notify(@Nullable Player player, String key, Object... args) {
        if (player != null) {
            player.displayClientMessage(Component.translatable("rocketnautics.fluid_hose." + key, args), true);
        }
    }

    private static class ConnectionPoint {
        final BlockPos anchorPos;
        final Direction facing;
        final boolean needsPlacement;

        ConnectionPoint(BlockPos anchorPos, Direction facing, boolean needsPlacement) {
            this.anchorPos = anchorPos;
            this.facing = facing;
            this.needsPlacement = needsPlacement;
        }
    }

    private ConnectionPoint resolveConnectionPoint(Level level, BlockPos clickedPos, Direction clickedFace) {
        BlockState clickedState = level.getBlockState(clickedPos);
        if (clickedState.getBlock() instanceof HoseAnchorBlock) {
            return new ConnectionPoint(clickedPos, clickedState.getValue(HoseAnchorBlock.FACING), false);
        }

        boolean isValidPipe = FluidPropagator.hasFluidCapability(level, clickedPos, clickedFace)
                || clickedState.getBlock() instanceof com.simibubi.create.content.fluids.pipes.FluidPipeBlock;

        if (isValidPipe) {
            BlockPos anchorPos = clickedPos.relative(clickedFace);
            BlockState anchorState = level.getBlockState(anchorPos);
            // an anchor already in front of the clicked face is used as it is, not replaced
            if (anchorState.getBlock() instanceof HoseAnchorBlock) {
                return new ConnectionPoint(anchorPos, anchorState.getValue(HoseAnchorBlock.FACING), false);
            }
            if (anchorState.canBeReplaced()) {
                return new ConnectionPoint(anchorPos, clickedFace, true);
            }
        }

        return null;
    }

    /** Shift-using the hose in the air clears a half-made connection too, not only shift-clicking a block. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack heldStack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && getFirstPos(heldStack) != null) {
            clearFirstPos(heldStack);
            if (!level.isClientSide) {
                notify(player, "cleared");
            }
            return InteractionResultHolder.sidedSuccess(heldStack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        ItemStack heldStack = context.getItemInHand();
        Player player = context.getPlayer();

        if (player != null && player.isShiftKeyDown()) {
            clearFirstPos(heldStack);
            if (!level.isClientSide) {
                notify(player, "cleared");
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        ConnectionPoint point = resolveConnectionPoint(level, clickedPos, clickedFace);
        if (point == null) {
            return InteractionResult.PASS;
        }

        BlockPos firstPos = getFirstPos(heldStack);
        Direction firstFace = getFirstFace(heldStack);

        if (firstPos == null) {
            setFirstPos(heldStack, clickedPos, clickedFace);
            level.playSound(player, clickedPos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.6F, 1.2F);
            if (!level.isClientSide) {
                notify(player, "first_set");
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (firstPos.equals(clickedPos) && firstFace == clickedFace) {
            return InteractionResult.PASS;
        }

        ConnectionPoint firstPoint = resolveConnectionPoint(level, firstPos, firstFace);
        // all changes happen on the server; the client sees them when the server syncs the stack and blocks
        if (!level.isClientSide) {
            if (firstPoint == null) {
                notify(player, "first_invalid");
            } else {
                connect(level, player, heldStack, firstPoint, point);
            }
        }

        clearFirstPos(heldStack);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Connects two connection points, placing Hose Anchors where needed. Survival players pay for placed anchors from
     * their inventory, and nothing is placed or used up if the connection fails.
     */
    private void connect(Level level, @Nullable Player player, ItemStack heldStack, ConnectionPoint first, ConnectionPoint second) {
        if (first.anchorPos.equals(second.anchorPos)) {
            notify(player, "failed");
            return;
        }

        boolean consumes = player == null || !player.hasInfiniteMaterials();
        int anchorsNeeded = (first.needsPlacement ? 1 : 0) + (second.needsPlacement ? 1 : 0);
        if (consumes && anchorsNeeded > 0
                && (player == null || player.getInventory().countItem(RocketBlocks.HOSE_ANCHOR.asItem()) < anchorsNeeded)) {
            notify(player, "needs_anchors", anchorsNeeded);
            return;
        }

        BlockState replacedAtFirst = level.getBlockState(first.anchorPos);
        BlockState replacedAtSecond = level.getBlockState(second.anchorPos);
        placeAnchor(level, first);
        placeAnchor(level, second);

        // like Simulated's rope item: a rope this replaces drops its item, except for players with infinite materials
        if (attachHose(level, first.anchorPos, second.anchorPos, consumes)) {
            if (consumes) {
                if (player != null) {
                    takeAnchors(player.getInventory(), anchorsNeeded);
                }
                heldStack.shrink(1);
            }
            notify(player, "connected");
        } else {
            if (second.needsPlacement) {
                level.setBlock(second.anchorPos, replacedAtSecond, 3);
            }
            if (first.needsPlacement) {
                level.setBlock(first.anchorPos, replacedAtFirst, 3);
            }
            notify(player, "failed");
        }
    }

    private static void placeAnchor(Level level, ConnectionPoint point) {
        if (point.needsPlacement) {
            BlockState state = RocketBlocks.HOSE_ANCHOR.get().defaultBlockState().setValue(HoseAnchorBlock.FACING, point.facing);
            level.setBlock(point.anchorPos, state, 3);
        }
    }

    private static void takeAnchors(Inventory inventory, int count) {
        Item anchor = RocketBlocks.HOSE_ANCHOR.asItem();
        for (int slot = 0; slot < inventory.getContainerSize() && count > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(anchor)) {
                int taken = Math.min(count, stack.getCount());
                stack.shrink(taken);
                count -= taken;
            }
        }
        inventory.setChanged();
    }

    private boolean attachHose(Level level, BlockPos posA, BlockPos posB, boolean dropReplacedRope) {
        BlockEntity beA = level.getBlockEntity(posA);
        BlockEntity beB = level.getBlockEntity(posB);

        if (beA instanceof SmartBlockEntity sbeA && beB instanceof SmartBlockEntity sbeB) {
            RopeStrandHolderBehavior holderA = sbeA.getBehaviour(RopeStrandHolderBehavior.TYPE);
            RopeStrandHolderBehavior holderB = sbeB.getBehaviour(RopeStrandHolderBehavior.TYPE);

            if (holderA != null && holderB != null) {
                boolean created;
                CONNECTING.set(true);
                try {
                    created = holderA.createRope(holderB, dropReplacedRope);
                } finally {
                    CONNECTING.remove();
                }
                if (created) {
                    level.playSound(null, posA, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5F, 1F);
                    level.playSound(null, posB, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5F, 1F);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        dev.devce.rocketnautics.content.RocketTooltipHelper.appendTooltip(stack, this, tooltip, flag);
    }
}
