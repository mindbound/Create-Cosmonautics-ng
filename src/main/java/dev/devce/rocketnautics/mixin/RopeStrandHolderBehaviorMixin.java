package dev.devce.rocketnautics.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.devce.rocketnautics.content.blocks.hose.HoseAnchorBlockEntity;
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

/**
 * Simulated always returns a Rope Coupling when a rope strand is removed. A hose between two Hose Anchors returns a
 * Fluid Hose instead, so breaking a hose gives back the item that made it.
 */
@Mixin(value = RopeStrandHolderBehavior.class, remap = false)
public abstract class RopeStrandHolderBehaviorMixin {

    @ModifyExpressionValue(method = "destroyRope", at = @At(value = "NEW", target = "(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack rocketnautics$returnFluidHose(ItemStack ropeCoupling) {
        RopeStrandHolderBehavior self = (RopeStrandHolderBehavior) (Object) this;
        // the strand's owner is its START end; destroyRope only gets here for the owner
        if (!(self.blockEntity instanceof HoseAnchorBlockEntity)) {
            return ropeCoupling;
        }
        // A Rope Coupling can also tie an anchor to a non-hose rope holder (e.g. a Rope Connector): that stays a rope.
        // A missing END block entity (already broken) counts as an anchor, since the owner is one.
        ServerRopeStrand strand = self.getOwnedStrand();
        Level level = self.blockEntity.getLevel();
        RopeAttachment end = strand == null ? null : strand.getAttachment(RopeAttachmentPoint.END);
        if (end != null && level != null) {
            BlockEntity endEntity = level.getBlockEntity(end.blockAttachment());
            if (endEntity instanceof SmartBlockEntity smart && !(endEntity instanceof HoseAnchorBlockEntity)
                    && smart.getBehaviour(RopeStrandHolderBehavior.TYPE) != null) {
                return ropeCoupling;
            }
        }
        return new ItemStack(RocketItems.FLUID_HOSE.get(), ropeCoupling.getCount());
    }
}
