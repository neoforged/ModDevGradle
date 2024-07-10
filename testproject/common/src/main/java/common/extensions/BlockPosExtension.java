package common.extensions;

import net.minecraft.core.BlockPos;

public interface BlockPosExtension {
    default BlockPos setXToZero() {
        var thiz = (BlockPos) this;
        return new BlockPos(0, thiz.getY(), thiz.getZ());
    }
}
