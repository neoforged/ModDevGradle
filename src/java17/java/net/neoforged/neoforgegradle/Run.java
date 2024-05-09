package net.neoforged.neoforgegradle;

import org.gradle.api.Named;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

// TODO: the name probably needs to be sanitized for spaces and other weird characters
public abstract class Run implements Named {
    @Inject
    public Run() {
    }

    abstract Property<String> getType();

    public void client() {
        getType().set("client");
    }

    public void data() {
        getType().set("data");
    }

    public void server() {
        getType().set("server");
    }
}
