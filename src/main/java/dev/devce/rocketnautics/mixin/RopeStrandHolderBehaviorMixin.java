package dev.devce.rocketnautics.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.devce.rocketnautics.content.blocks.hose.HoseAnchorBlockEntity;
import dev.devce.rocketnautics.content.items.hose.FluidHoseItem;
import dev.devce.rocketnautics.registry.RocketItems;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hoses are rope strands between two Hose Anchors. Only the Fluid Hose may create one, and breaking a hose gives back a
 * Fluid Hose instead of the Rope Coupling that Simulated always returns.
 */
@Mixin(value = RopeStrandHolderBehavior.class, remap = false)
public abstract class RopeStrandHolderBehaviorMixin {

    /**
     * {@link RopeItemMixin} keeps the Rope Coupling off anchors, but some code creates ropes without asking
     * {@code RopeItem.isValidRopeAttachment}: CreateFireFightingAdd's Smart Rope Connector, for one, joins any free
     * rope holder to a Rope Coupling's other end. Refusing here covers every way in, while the Fluid Hose marks its own
     * calls.
     */
    @Inject(method = "createRope", at = @At("HEAD"), cancellable = true)
    private void rocketnautics$onlyFluidHoseOnAnchors(RopeStrandHolderBehavior target, boolean dropItem, CallbackInfoReturnable<Boolean> cir) {
        RopeStrandHolderBehavior self = (RopeStrandHolderBehavior) (Object) this;
        if ((self.blockEntity instanceof HoseAnchorBlockEntity || target.blockEntity instanceof HoseAnchorBlockEntity)
                && !FluidHoseItem.isConnecting()) {
            cir.setReturnValue(false);
        }
    }

    @ModifyExpressionValue(method = "destroyRope", at = @At(value = "NEW", target = "(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack rocketnautics$returnFluidHose(ItemStack ropeCoupling) {
        RopeStrandHolderBehavior self = (RopeStrandHolderBehavior) (Object) this;
        // the strand's owner is its START end; destroyRope only gets here for the owner
        if (!(self.blockEntity instanceof HoseAnchorBlockEntity)) {
            return ropeCoupling;
        }
        // New strands from an anchor always end at another anchor. One made before the Fluid Hose became the only way
        // to connect anchors can end at some other block, such as a Rope Connector: that one stays a rope. A missing
        // END block entity (already broken) counts as an anchor, since the owner is one.
        ServerRopeStrand strand = self.getOwnedStrand();
        Level level = self.blockEntity.getLevel();
        RopeAttachment end = strand == null ? null : strand.getAttachment(RopeAttachmentPoint.END);
        if (end != null && level != null) {
            BlockEntity endEntity = level.getBlockEntity(end.blockAttachment());
            if (endEntity != null && !(endEntity instanceof HoseAnchorBlockEntity)) {
                return ropeCoupling;
            }
        }
        return new ItemStack(RocketItems.FLUID_HOSE.get(), ropeCoupling.getCount());
    }
}
