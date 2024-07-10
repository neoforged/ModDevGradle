package subproject.extensions;

import net.minecraft.core.BlockPos;

public interface BlockPosExtension {
    default BlockPos setYToZero() {
        var thiz = (BlockPos)this;
        return new BlockPos(thiz.getX(), 0, thiz.getZ());
    }
}
