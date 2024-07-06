package testproject.mixins;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import testproject.FunExtensions;

@Mixin(ItemStack.class)
public class AddFunExtensionsMixin implements FunExtensions {
}
