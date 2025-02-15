package com.nikodoko.javaimports.environment.maven;

import com.google.common.base.MoreObjects;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Finds all dependencies in a Maven project by parsing POM files. */
class MavenDependencyFinder {
  static final class Result {
    final List<MavenDependency> dependencies = new ArrayList<>();
    final List<MavenEnvironmentException> errors = new ArrayList<>();

    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("dependencies", dependencies)
          .add("errors", errors)
          .toString();
    }
  }

  private static final Path POM = Paths.get("pom.xml");
  private Result result = new Result();

  Result findAll(Path moduleRoot) {
    var loaded = MavenPomLoader.load(moduleRoot.resolve(POM));

    var pom = loaded.pom;
    var errors = new ArrayList<>(loaded.errors);
    while (pom.hasParent() && !pom.isWellDefined()) {
      // We need to normalize because the relative parent path often includes the special name ..
      var parentPath = moduleRoot.resolve(relativeParentPomPath(pom)).normalize();
      loaded = MavenPomLoader.load(parentPath);
      errors.addAll(loaded.errors);
      pom.merge(loaded.pom);
    }

    result.dependencies.addAll(pom.dependencies());
    result.errors.addAll(errors);

    return result;
  }

  private Path relativeParentPomPath(FlatPom pom) {
    var parent = pom.maybeParent().get();
    if (parent.endsWith(POM)) {
      return parent;
    }

    // Consider that we had a directory, attempt to find a pom in it
    return parent.resolve(POM);
  }
}
