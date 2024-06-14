package jijtest;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoreModTest {
    @Test
    void testPresenceOfCoreMod() throws Exception {
        var field = assertDoesNotThrow(() -> ItemStack.class.getField("CORE_MOD_MARKER"));
        assertTrue(field.getBoolean(null));
    }
}
