package net.neoforged.moddevgradle.internal;

/**
 * Used to customize the groups of tasks generated by MDG.
 *
 * @param publicTaskGroup   Use this group for tasks that are considered to be part of the user-interface of MDG.
 * @param internalTaskGroup Use this group for tasks that are considered to be an implementation detail of MDG.
 */
record Branding(String publicTaskGroup, String internalTaskGroup) {
    public static final Branding MDG = new Branding("mod development", "mod development/internal");
    public static final Branding NEODEV = new Branding("neoforge development", "neoforge development/internal");
}