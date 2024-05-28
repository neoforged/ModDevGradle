package net.neoforged.moddevgradle.internal;

import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;

abstract class PrepareRun extends PrepareRunOrTest {
    @Input
    public abstract Property<String> getRunType();

    @Optional
    @Input
    public abstract Property<String> getMainClass();

    @Inject
    public PrepareRun() {
    }

    @Override
    protected UserDevRunType resolveRunType(UserDevConfig userDevConfig) {
        if (getRunType().get().equals("junit")) {
            throw new GradleException("The junit run type cannot be used for normal NeoForge runs. Available run types: " + userDevConfig.runs().keySet());
        }
        var runConfig = userDevConfig.runs().get(getRunType().get());
        if (runConfig == null) {
            throw new GradleException("Trying to prepare unknown run: " + getRunType().get() + ". Available run types: " + userDevConfig.runs().keySet());
        }
        return runConfig;
    }

    @Override
    @Nullable
    protected String resolveMainClass(UserDevRunType runConfig) {
        return getMainClass().getOrElse(runConfig.main());
    }
}
