package com.iikka.olkkuclient.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccess {
    @Accessor("clickCount")
    int olkku$clicks();
}
