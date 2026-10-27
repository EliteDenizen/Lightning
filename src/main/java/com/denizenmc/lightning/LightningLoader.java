package com.denizenmc.lightning;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

/**
 * Loads PacketEvents as an external library during plugin initialization.
 */
public final class LightningLoader implements PluginLoader {

    @Override
    public void classloader(@NotNull final PluginClasspathBuilder builder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder("codemc", "default", "https://repo.codemc.io/repository/maven-releases/").build());
        resolver.addDependency(new Dependency(new DefaultArtifact("com.github.retrooper:packetevents-spigot:2.13.0"), null));
        builder.addLibrary(resolver);
    }
}
