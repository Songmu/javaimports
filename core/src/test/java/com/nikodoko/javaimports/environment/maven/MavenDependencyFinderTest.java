package com.nikodoko.javaimports.environment.maven;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.DefaultModelWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MavenDependencyFinderTest {
  Path tmp;

  @BeforeEach
  void setup() throws Exception {
    tmp = Files.createDirectory(Files.createTempDirectory("").resolve("module"));
  }

  @Test
  void testThatPomWithNoDepenciesReturnsEmptyList() throws Exception {
    MavenDependencyFinder finder = new MavenDependencyFinder();
    writeChild(basicPom());

    MavenDependencyFinder.Result got = finder.findAll(tmp);
    assertThat(got.dependencies).isEmpty();
    assertThat(got.errors).isEmpty();
  }

  @Test
  void testThatPomWithDependenciesReturnsCorrectDependencies() throws Exception {
    MavenDependencyFinder finder = new MavenDependencyFinder();
    writeChild(
        basicPom(),
        withDependencies(
            "com.google.guava", "guava", "28.1-jre", "com.google.truth", "truth", "1.0.1"));
    List<MavenDependency> expected =
        ImmutableList.of(
            new MavenDependency("com.google.guava", "guava", "28.1-jre"),
            new MavenDependency("com.google.truth", "truth", "1.0.1"));
    MavenDependencyFinder.Result got = finder.findAll(tmp);
    assertThat(got.dependencies).containsExactlyElementsIn(expected);
    assertThat(got.errors).isEmpty();
  }

  @Test
  void testDependenciesUsingParametersAreFoundEvenIfNotResolved() throws Exception {
    MavenDependencyFinder finder = new MavenDependencyFinder();
    writeChild(basicPom(), withDependencies("com.google.guava", "guava", "${guava.version}"));
    List<MavenDependency> expected =
        ImmutableList.of(new MavenDependency("com.google.guava", "guava", "${guava.version}"));

    MavenDependencyFinder.Result got = finder.findAll(tmp);
    assertThat(got.dependencies).containsExactlyElementsIn(expected);
    assertThat(got.errors).isEmpty();
  }

  @Test
  void testThatPropertiesAreResolvedInTheSameFile() throws Exception {
    var finder = new MavenDependencyFinder();
    writeChild(
        basicPom(),
        withDependencies("com.google.guava", "guava", "${guava.version}"),
        withProperty("guava.version", "28.1-jre"));
    var expected = List.of(new MavenDependency("com.google.guava", "guava", "28.1-jre"));

    var got = finder.findAll(tmp);
    assertThat(got.errors).isEmpty();
    assertThat(got.dependencies).containsExactlyElementsIn(expected);
  }

  @Test
  void testThatFinderDoesNotCrashOnInvalidPom() throws Exception {
    Files.write(Paths.get(tmp.toString(), "pom.xml"), "this is not a valid pom!".getBytes());

    MavenDependencyFinder.Result got = new MavenDependencyFinder().findAll(tmp);
    assertThat(got.dependencies).isEmpty();
    assertThat(got.errors).hasSize(1);
  }

  @Test
  void testThatExplicitParentPomIsFound() throws Exception {
    var finder = new MavenDependencyFinder();
    writeChild(
        basicPom(),
        withExplicitRelativePath("../pom.xml"),
        withDependencies("com.google.guava", "guava", "${guava.version}"));
    writeParent(basicPom(), withProperty("guava.version", "28.1-jre"));
    var expected = List.of(new MavenDependency("com.google.guava", "guava", "28.1-jre"));

    var got = finder.findAll(tmp);
    assertThat(got.errors).isEmpty();
    assertThat(got.dependencies).containsExactlyElementsIn(expected);
  }

  @Test
  void testThatParentPomIsFoundIfOnlyDirectory() throws Exception {
    var finder = new MavenDependencyFinder();
    writeChild(
        basicPom(),
        withExplicitRelativePath(".."),
        withDependencies("com.google.guava", "guava", "${guava.version}"));
    writeParent(basicPom(), withProperty("guava.version", "28.1-jre"));
    var expected = List.of(new MavenDependency("com.google.guava", "guava", "28.1-jre"));

    var got = finder.findAll(tmp);
    assertThat(got.errors).isEmpty();
    assertThat(got.dependencies).containsExactlyElementsIn(expected);
  }

  @Test
  void theThatNoParentPomIsFoundIfEmptyRelativePath() throws Exception {
    var finder = new MavenDependencyFinder();
    writeChild(
        basicPom(),
        withExplicitRelativePath(""),
        withDependencies("com.google.guava", "guava", "${guava.version}"));
    writeParent(basicPom(), withProperty("guava.version", "28.1-jre"));
    var expected = List.of(new MavenDependency("com.google.guava", "guava", "${guava.version}"));

    var got = finder.findAll(tmp);
    assertThat(got.errors).isEmpty();
    assertThat(got.dependencies).containsExactlyElementsIn(expected);
  }

  @Test
  void testThatImplicitParentPomIsFound() throws Exception {
    var finder = new MavenDependencyFinder();
    writeChild(
        basicPom(),
        withImplicitRelativePath(),
        withDependencies("com.google.guava", "guava", "${guava.version}"));
    writeParent(basicPom(), withProperty("guava.version", "28.1-jre"));
    var expected = List.of(new MavenDependency("com.google.guava", "guava", "28.1-jre"));

    var got = finder.findAll(tmp);
    assertThat(got.errors).isEmpty();
    assertThat(got.dependencies).containsExactlyElementsIn(expected);
  }

  static Consumer<Model> basicPom() {
    return m -> {
      m.setModelVersion("4.0.0");
      m.setGroupId("com.nikodoko.javaimports");
      m.setArtifactId("test-pom");
      m.setVersion("0.0");
    };
  }

  static Consumer<Model> withDependencies(String... elements) {
    List<Dependency> deps = new ArrayList<>();
    for (int i = 0; i < elements.length; i = i + 3) {
      Dependency dep = new Dependency();
      dep.setGroupId(elements[i]);
      dep.setArtifactId(elements[i + 1]);
      dep.setVersion(elements[i + 2]);
      deps.add(dep);
    }

    return m -> m.setDependencies(deps);
  }

  static Consumer<Model> withProperty(String key, String value) {
    return m -> m.addProperty(key, value);
  }

  static Consumer<Model> withExplicitRelativePath(String relativePath) {
    var parent = new Parent();
    parent.setRelativePath(relativePath);

    return m -> m.setParent(parent);
  }

  static Consumer<Model> withImplicitRelativePath() {
    return m -> m.setParent(new Parent());
  }

  void writeChild(Consumer<Model>... options) throws Exception {
    write(tmp, options);
  }

  void writeParent(Consumer<Model>... options) throws Exception {
    write(tmp.getParent(), options);
  }

  void write(Path dir, Consumer<Model>... options) throws Exception {
    Model pom = new Model();
    for (Consumer<Model> opt : options) {
      opt.accept(pom);
    }

    File target = Paths.get(dir.toString(), "pom.xml").toFile();
    new DefaultModelWriter().write(target, null, pom);
  }
}
