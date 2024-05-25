package testproject;

import net.minecraft.DetectedVersion;
import net.neoforged.fml.common.Mod;

@Mod("testproject")
public class TestProject {
    public TestProject() {
        System.out.println(DetectedVersion.tryDetectVersion().getName());
    }
}
