package dev.elitedenizen.lightning.util.server;

import org.bukkit.Bukkit;

public final class VersionAdapter {

    public static final boolean FOLIA;

    static {
        FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");
    }

    public static String serverBrand() {
        if (FOLIA) return "Folia";
        String name = Bukkit.getName();
        return name != null && !name.isEmpty() ? name : "Paper";
    }

    private VersionAdapter() {}

    public static ParsedVersion parseVersion() {
        return ParsedVersion.of(Bukkit.getBukkitVersion());
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    public static final class ParsedVersion {
        public final int major;
        public final int minor;
        public final int patch;
        public final String raw;

        private ParsedVersion(int major, int minor, int patch, String raw) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.raw = raw;
        }

        public static ParsedVersion of(String bukkit) {
            String stripped = bukkit.split("-")[0].trim();
            String[] parts = stripped.split("\\.");
            int major = 0, minor = 0, patch = 0;
            try { if (parts.length > 0) major = Integer.parseInt(parts[0]); } catch (NumberFormatException ignored) {}
            try { if (parts.length > 1) minor = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
            try { if (parts.length > 2) patch = Integer.parseInt(parts[2]); } catch (NumberFormatException ignored) {}
            return new ParsedVersion(major, minor, patch, bukkit);
        }

        public boolean isAtLeast(int maj, int min, int pat) {
            if (major != maj) return major > maj;
            if (minor != min) return minor > min;
            return patch >= pat;
        }

        public boolean isNewNumberingScheme() {
            return major >= 26;
        }

        public String friendly() {
            return major + "." + minor + (patch > 0 ? "." + patch : "")
                    + (isNewNumberingScheme() ? " (new)" : "");
        }

        @Override
        public String toString() {
            return friendly();
        }
    }
}
