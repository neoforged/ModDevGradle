package testproject;

import apitest.ApiTest;
import net.minecraft.DetectedVersion;
import net.neoforged.fml.common.Mod;
import subproject.SubProject;

@Mod("testproject")
public class TestProject {
    public TestProject() {
        System.out.println(DetectedVersion.tryDetectVersion().getName());
        System.out.println("Top-Level: " + ((DetectedVersion) DetectedVersion.BUILT_IN).buildTime);
        System.out.println(SubProject.class.getName());

        new ApiTest(); // access something from the api source set
    }
}
