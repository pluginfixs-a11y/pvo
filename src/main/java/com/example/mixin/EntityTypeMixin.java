package com.example.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityType.class)
public class EntityTypeMixin {

    @Inject(method = "clientTrackingRange", at = @At("HEAD"), cancellable = true)
    private void pvo$overrideFireworkTrackingRange(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this == EntityTypes.FIREWORK_ROCKET) {
            // Возвращаем дистанцию трекинга в чанках (32 чанка = 512 блоков)
            cir.setReturnValue(32);
        }
    }
}
