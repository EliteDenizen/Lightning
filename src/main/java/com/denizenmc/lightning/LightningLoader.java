package com.denizenmc.lightning;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Locale;

/**
 * Provides PacketEvents to Lightning.
 * An installed PacketEvents plugin is always preferred, because it can be updated independently
 * when Minecraft changes its protocol. The bundled library is only downloaded when it is missing.
 */
public final class LightningLoader implements PluginLoader {

    static final String BUNDLED_VERSION = "2.14.0";

    @Override
    public void classloader(@NotNull final PluginClasspathBuilder builder) {
        if (installed(builder.getContext().getDataDirectory().toFile().getParentFile())) return;

        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder("codemc", "default", "https://repo.codemc.io/repository/maven-releases/").build());
        resolver.addDependency(new Dependency(new DefaultArtifact("com.github.retrooper:packetevents-spigot:" + BUNDLED_VERSION), null));
        builder.addLibrary(resolver);
    }

    // Plugin metadata is not available this early, so the plugins folder is checked for a PacketEvents jar.
    private static boolean installed(final File plugins) {
        File[] jars = plugins == null ? null : plugins.listFiles((folder, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".jar") && lower.contains("packetevents");
        });
        return jars != null && jars.length > 0;
    }
}