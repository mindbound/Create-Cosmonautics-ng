package dev.devce.rocketnautics.mixin;

import dev.devce.rocketnautics.content.blocks.hose.HoseAnchorBlockEntity;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hose Anchors connect only with a Fluid Hose. The Rope Coupling checks both of its clicks with
 * {@code isValidRopeAttachment}, and so does its client-side preview (other mods' rope items, such as Create Deep Seas'
 * Steel Cable, use it too). Rejecting anchors here makes them behave like any block that can't hold a rope.
 * {@link RopeStrandHolderBehaviorMixin} enforces the same rule for code that creates ropes without this check.
 */
@Mixin(value = RopeItem.class, remap = false)
public abstract class RopeItemMixin {

    @Inject(method = "isValidRopeAttachment", at = @At("HEAD"), cancellable = true)
    private static void rocketnautics$rejectHoseAnchors(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (level.getBlockEntity(pos) instanceof HoseAnchorBlockEntity) {
            cir.setReturnValue(false);
        }
    }
}
