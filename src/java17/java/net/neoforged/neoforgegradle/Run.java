package net.neoforged.neoforgegradle;

import org.gradle.api.provider.Property;

public abstract class Run {
    private final String name;

    public Run(String name) {
        this.name = name;
    }

    // TODO: this probably needs to be sanitized for spaces and other weird characters
    public String getName() {
        return name;
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
