package me.stolyy.heroes;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.RemoteRepository;

import java.util.List;

/**
 * Paper {@link PluginLoader} that resolves JDA (without audio/opus/tink modules) at server start
 * so the plugin jar stays lean without shading JDA.
 */
@SuppressWarnings("UnstableApiUsage")
public final class HeroesPluginLoader implements PluginLoader {
    private static final String JDA_COORDS = "net.dv8tion:JDA:6.7.0";

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        resolver.addDependency(new Dependency(
                new DefaultArtifact(JDA_COORDS),
                null,
                false,
                List.of(
                        new Exclusion("club.minnced", "opus-java", "*", "*"),
                        new Exclusion("com.google.crypto.tink", "tink", "*", "*")
                )));
        classpathBuilder.addLibrary(resolver);
    }
}
