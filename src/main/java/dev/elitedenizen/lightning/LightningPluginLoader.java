package dev.elitedenizen.lightning;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

public final class LightningPluginLoader implements PluginLoader {

    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();

        resolver.addRepository(new RemoteRepository.Builder(
                "codemc-releases", "default", "https://repo.codemc.io/repository/maven-releases/").build());

        resolver.addDependency(new Dependency(
                new DefaultArtifact("com.github.retrooper:packetevents-spigot:2.11.1"), null));

        classpathBuilder.addLibrary(resolver);
    }
}
