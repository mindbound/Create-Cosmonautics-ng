package dev.devce.rocketnautics.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.devce.rocketnautics.content.physics.SpaceTransitionHandler;
import dev.egg.dimensionalsable.SubLevelWarper;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Adjusts Dimensional Sable's warp for the ships Cosmonautics moves between a planet and deep space.
 * Only active while {@link SpaceTransitionHandler#isWarping()} is true, so other mods' warps are unchanged.
 */
@Mixin(value = SubLevelWarper.class, remap = false)
public class SubLevelWarperMixin {

    private static final String WARP = "warpConnectedSubLevels(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ljava/util/List;Lnet/minecraft/server/level/ServerLevel;Lorg/joml/Vector3d;Lorg/joml/Quaterniond;)I";

    /** Carries along entities near the ship, not only those touching it (players on deck, mobs on board). */
    @ModifyArg(method = WARP, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;inflate(D)Lnet/minecraft/world/phys/AABB;"))
    private static double rocketnautics$widenEntityCapture(double margin) {
        return SpaceTransitionHandler.isWarping() ? margin + SpaceTransitionHandler.WARP_ENTITY_DETECTION_TOLERANCE : margin;
    }

    /**
     * Ships arrive at rest, as they did with the built-in warper. On the way up, their velocity has already gone into
     * the deep-space orbit ({@code SpaceTransitionHandler.initInstance}); Dimensional Sable 1.1.0 would keep it as well.
     */
    @ModifyExpressionValue(method = WARP, at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;getLinearVelocity(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"))
    private static Vector3d rocketnautics$arriveWithoutLinearVelocity(Vector3d velocity) {
        return SpaceTransitionHandler.isWarping() ? velocity.zero() : velocity;
    }

    @ModifyExpressionValue(method = WARP, at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;getAngularVelocity(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"))
    private static Vector3d rocketnautics$arriveWithoutAngularVelocity(Vector3d velocity) {
        return SpaceTransitionHandler.isWarping() ? velocity.zero() : velocity;
    }
}
