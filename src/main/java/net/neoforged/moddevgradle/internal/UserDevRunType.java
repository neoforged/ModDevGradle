package net.neoforged.moddevgradle.internal;

import java.util.List;
import java.util.Map;

public record UserDevRunType(boolean singleInstance, String main, List<String> args, List<String> jvmArgs,
        Map<String, String> env, Map<String, String> props) {}
