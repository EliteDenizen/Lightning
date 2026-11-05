package com.denizenmc.lightning;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Provides PacketEvents to Lightning.
 * An installed PacketEvents plugin is always preferred, because it can be updated independently
 * when Minecraft changes its protocol. The bundled library is only downloaded when it is missing.
 */
public final class LightningLoader implements PluginLoader {

    static final String BUNDLED_VERSION = "2.14.0";
    private static final Pattern NAME = Pattern.compile("(?im)^name:[ \\t]*[\"']?packetevents[\"']?[ \\t]*$");

    @Override
    public void classloader(@NotNull final PluginClasspathBuilder builder) {
        if (installed(builder.getContext().getDataDirectory().toFile().getParentFile())) return;

        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder("codemc", "default", "https://repo.codemc.io/repository/maven-releases/").build());
        resolver.addDependency(new Dependency(new DefaultArtifact("com.github.retrooper:packetevents-spigot:" + BUNDLED_VERSION), null));
        builder.addLibrary(resolver);
    }

    // Plugin metadata is not available this early, so the plugins folder is checked for a PacketEvents jar.
    // The jar's own descriptor is read, because a renamed jar would otherwise go unnoticed and two copies
    // of PacketEvents would end up injected into every connection.
    private static boolean installed(final File plugins) {
        File[] jars = plugins == null ? null : plugins.listFiles((folder, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (jars == null) return false;
        for (File jar : jars) {
            if (named(jar)) return true;
        }
        return false;
    }

    private static boolean named(final File jar) {
        try (ZipFile zip = new ZipFile(jar)) {
            for (String descriptor : new String[]{"paper-plugin.yml", "plugin.yml"}) {
                ZipEntry entry = zip.getEntry(descriptor);
                if (entry == null || entry.getSize() > 65_536) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    String text = new String(in.readNBytes(65_536), StandardCharsets.UTF_8);
                    if (NAME.matcher(text).find()) return true;
                }
            }
            return false;
        } catch (IOException | RuntimeException e) {
            // An unreadable jar is judged by its file name, as before.
            return jar.getName().toLowerCase(Locale.ROOT).contains("packetevents");
        }
    }
}