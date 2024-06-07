package testproject;

import net.minecraft.DetectedVersion;
import net.neoforged.fml.common.Mod;
import subproject.SubProject;

@Mod("testproject")
public class TestProject {
    public TestProject() {
        System.out.println(DetectedVersion.tryDetectVersion().getName());
        System.out.println(SubProject.class.getName());
    }
}
