package dev.devce.rocketnautics.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Dimensional Sable's {@code /sable dim_teleport} has no permission check, so any player could move ships between
 * dimensions. Restricts it to operators (permission level 2), as Cosmonautics' built-in copy of Dimensional Sable did.
 */
@Mixin(targets = "dev.egg.dimensionalsable.Commands$DimensionCommand", remap = false)
public class DimensionalSableCommandMixin {

    // ordinal 0 is the shared "sable" literal; ordinal 1 is "dim_teleport".
    @ModifyExpressionValue(method = "register", at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/Commands;literal(Ljava/lang/String;)Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;", ordinal = 1))
    private static LiteralArgumentBuilder<CommandSourceStack> rocketnautics$requireOperator(LiteralArgumentBuilder<CommandSourceStack> dimTeleport) {
        return dimTeleport.requires(source -> source.hasPermission(2));
    }
}
