package com.nikodoko.javaimports.parser;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maintains the set of named language entities delared in the scope, as well as a link to the
 * immediately surrounding (parent) scope.
 *
 * <p>Also maintains a set of unresolved identifiers found in this scope.
 *
 * <p>The top level scope should have {@code parent==null}.
 */
public class Scope {
  // All the entities declared in this scope
  private Map<String, Entity> entities = new HashMap<>();
  // All the identifiers so far unresolved in this scope
  private Set<String> notYetResolved = new HashSet<>();
  // All the classes extending a parent for which that parent has not yet been found in this scope
  private Set<Entity> notYetExtended = new HashSet<>();
  // Parent scope, can be null if top scope
  private Scope parent = null;

  /**
   * The {@code Scope} constructor.
   *
   * @param parent its parent scope
   */
  public Scope(Scope parent) {
    this.parent = parent;
  }

  /** This {@code Scope}'s parent scope. */
  public Scope parent() {
    return parent;
  }

  /** The set of identifiers that have not yet been resolved in this scope */
  public Set<String> notYetResolved() {
    return notYetResolved;
  }

  /**
   * Sets this {@code Scope}'s notYetResolved set.
   *
   * @param identifiers the new set
   */
  public void notYetResolved(Set<String> identifiers) {
    notYetResolved = identifiers;
  }

  /**
   * The set of classes extending a parent for which that parent has not yet been found in this
   * scope
   */
  public Set<Entity> notYetExtended() {
    return notYetExtended;
  }

  /**
   * Returns whether the identifier is found in this scope, ignoring all parent scopes.
   *
   * @param identifier the identifier to look for
   * @return the entity found, or null
   */
  public Entity lookup(String identifier) {
    return entities.get(identifier);
  }

  /**
   * Inserts the identifier in the scope.
   *
   * @param identifier the identifier to add
   */
  public void insert(String identifier, Entity entity) {
    entities.put(identifier, entity);
  }

  /**
   * Searches for the parent class of {@code classEntity} in this {@code Scope}.
   *
   * <p>This will return {@code null} if the parent is not in the scope but has a chance to be
   * somewhere else, and an {@link Entity} with {@link Entity.Kind#BAD} if the parent class is not
   * found but cannot be found somewhere else (in other words, when it catches an identifier
   * resolution error).
   *
   * <p>{@code classEntity} is expected to be of {@link Entity.Kind#CLASS} and to have a non-null
   * extended path.
   *
   * @param classEntity the child class to try to extend
   */
  public Entity findParent(Entity classEntity) {
    checkArgument(
        classEntity.kind() == Entity.Kind.CLASS,
        "expected a class entity but got %s",
        classEntity.kind());
    checkArgument(classEntity.isChildClass(), "expected a child class entity");

    List<String> parentPath = classEntity.extendedClassPath();

    // The parentPath may look like something like this: A.B.C
    // What we are going to do:
    //  - See if the leftmost part of the path is in this scope (A in our case)
    //  - If not we might find it in another scope, so we return null
    //  - If yes we go down the path left to right, as long as we keep finding classes. If for
    //  whatever reason we do not (either because the class does not exist, or because it's not a
    //  class but something else), then we return a BAD Entity
    Entity maybeParent = lookup(parentPath.get(0));
    if (maybeParent == null) {
      return null;
    }

    Scope toScan = this;
    for (String s : parentPath) {
      maybeParent = toScan.lookup(s);
      if (maybeParent == null || maybeParent.kind() != Entity.Kind.CLASS) {
        // Whatever we are trying to extend, this is not going to work
        return new Entity(Entity.Kind.BAD);
      }

      toScan = maybeParent.scope();
    }

    // If we got here, then we found it
    return maybeParent;
  }

  /** Adds the class entity to the set of classes which parent has not yet been found */
  public void markAsNotYetExtended(Entity entity) {
    notYetExtended.add(entity);
  }

  /** Adds the identifier to the set of identifiers that have not yet been resolved */
  public void markAsNotYetResolved(String identifier) {
    notYetResolved.add(identifier);
  }

  /**
   * Debugging support.
   *
   * <p>This does not output anything about the parent scopes.
   */
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("entities", entities)
        .add("notYetResolved", notYetResolved)
        .add("notYetExtended", notYetExtended)
        .toString();
  }
}
