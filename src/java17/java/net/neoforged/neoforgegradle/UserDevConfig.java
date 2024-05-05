package net.neoforged.neoforgegradle;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record UserDevConfig(String mcp, String sources, String universal, List<String> libraries, List<String> modules,
                            Map<String, UserDevRunType> runs) implements Serializable {
}

