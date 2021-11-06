package com.nikodoko.javaimports.fixer.candidates;

import static com.google.common.truth.Truth.assertThat;
import static com.nikodoko.javaimports.common.CommonTestUtil.arbitraryImportEndingWith;
import static com.nikodoko.javaimports.common.CommonTestUtil.arbitrarySelector;
import static com.nikodoko.javaimports.common.CommonTestUtil.arbitrarySelectorOfSize;

import com.nikodoko.javaimports.common.CommonTestUtil;
import com.nikodoko.javaimports.common.Import;
import com.nikodoko.javaimports.common.Selector;
import java.util.Collections;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

public class BasicCandidateSelectionStrategyTest {
  static class SelectorAndImports {
    final Selector selector;
    final List<Import> imports;

    SelectorAndImports(Selector s, List<Import> i) {
      selector = s;
      imports = i;
    }
  }

  @Property
  void stdlibIsMoreRelevantThanExternal(@ForAll("endingWith") SelectorAndImports data) {
    var stdlibCandidate = new Candidate(data.imports.get(0), Candidate.Source.STDLIB);
    var externalCandidate = new Candidate(data.imports.get(1), Candidate.Source.EXTERNAL);
    var candidates =
        Candidates.forSelector(data.selector).add(stdlibCandidate, externalCandidate).build();
    var expected = BestCandidates.builder().put(data.selector, stdlibCandidate.i).build();

    var got =
        new BasicCandidateSelectionStrategy(Selector.of("a", "package")).selectBest(candidates);

    assertThat(got).isEqualTo(expected);
  }

  @Property
  void siblingsIsMoreRelevantThanStdlibOrExternal(@ForAll("endingWith") SelectorAndImports data) {
    var stdlibCandidate = new Candidate(data.imports.get(0), Candidate.Source.STDLIB);
    var externalCandidate = new Candidate(data.imports.get(1), Candidate.Source.EXTERNAL);
    var siblingCandidate = new Candidate(data.imports.get(2), Candidate.Source.SIBLING);
    var candidates =
        Candidates.forSelector(data.selector)
            .add(stdlibCandidate, externalCandidate, siblingCandidate)
            .build();
    var expected = BestCandidates.builder().put(data.selector, siblingCandidate.i).build();

    var got =
        new BasicCandidateSelectionStrategy(Selector.of("a", "package")).selectBest(candidates);

    assertThat(got).isEqualTo(expected);
  }

  @Property
  void shorterStdlibPathsAreMoreRelevantThanLongOnes(
      @ForAll("endingWith") SelectorAndImports data) {
    var shortest =
        Collections.min(data.imports, (i1, i2) -> i1.selector.size() - i2.selector.size());
    var candidates =
        Candidates.forSelector(data.selector)
            .add(
                data.imports.stream()
                    .map(i -> new Candidate(i, Candidate.Source.STDLIB))
                    .toArray(Candidate[]::new))
            .build();
    var expected = BestCandidates.builder().put(data.selector, shortest).build();

    var got =
        new BasicCandidateSelectionStrategy(Selector.of("a", "package")).selectBest(candidates);

    assertThat(got).isEqualTo(expected);
  }

  @Property
  void javaUtilIsMoreRelevantThanStdlibOfSameSize(
      @ForAll("endingWith") SelectorAndImports data, @ForAll("ofSize2") Selector prefix) {
    var stdlibImport =
        new Import(prefix.combine(data.imports.get(0).selector), data.imports.get(0).isStatic);
    var javaUtilImport =
        new Import(
            Selector.of("java", "util").combine(data.imports.get(0).selector),
            data.imports.get(0).isStatic);
    var candidates =
        Candidates.forSelector(data.selector)
            .add(
                new Candidate(stdlibImport, Candidate.Source.STDLIB),
                new Candidate(javaUtilImport, Candidate.Source.STDLIB))
            .build();
    var expected = BestCandidates.builder().put(data.selector, javaUtilImport).build();

    var got =
        new BasicCandidateSelectionStrategy(Selector.of("a", "package")).selectBest(candidates);

    assertThat(got).isEqualTo(expected);
  }

  @Property
  void closerExternalCandidatesAreMoreRelevant(
      @ForAll Import aPkg, @ForAll Import anotherPkg, @ForAll Selector aSelector) {
    var anImport = new Import(aPkg.selector.combine(aSelector), aPkg.isStatic);
    var anotherImport = new Import(anotherPkg.selector.combine(aSelector), anotherPkg.isStatic);
    var candidates =
        Candidates.forSelector(aSelector)
            .add(
                new Candidate(anImport, Candidate.Source.EXTERNAL),
                new Candidate(anImport, Candidate.Source.EXTERNAL))
            .build();
    var expected = BestCandidates.builder().put(aSelector, anImport).build();

    var got = new BasicCandidateSelectionStrategy(aPkg.selector).selectBest(candidates);

    assertThat(got).isEqualTo(expected);
  }

  @Property
  void aCandidateIsMoreRelevantThanOthersOfSameSourceIfThereIsOneInSameScopeForAnotherSelector(
      @ForAll Import pkg,
      @ForAll Import other,
      @ForAll("identifier") String a,
      @ForAll("identifier") String b,
      @ForAll Candidate.Source source,
      @ForAll Candidate.Source otherSource) {
    var aInPkg = new Import(pkg.selector.combine(Selector.of(a)), pkg.isStatic);
    var aNotInPkg = new Import(other.selector.combine(Selector.of(a)), other.isStatic);
    var bInPkg = new Import(pkg.selector.combine(Selector.of(b)), pkg.isStatic);
    var candidatesForA =
        Candidates.forSelector(Selector.of(a))
            .add(new Candidate(aInPkg, source), new Candidate(aNotInPkg, source))
            .build();
    var candidatesForB =
        Candidates.forSelector(Selector.of(b)).add(new Candidate(bInPkg, otherSource)).build();
    var candidates = Candidates.merge(candidatesForA, candidatesForB);
    var expected =
        BestCandidates.builder().put(Selector.of(a), aInPkg).put(Selector.of(b), bInPkg).build();

    var got =
        new BasicCandidateSelectionStrategy(Selector.of("a", "package")).selectBest(candidates);

    assertThat(got).isEqualTo(expected);
  }

  @Provide
  Arbitrary<SelectorAndImports> endingWith() {
    return arbitrarySelector()
        .flatMap(
            sel ->
                Combinators.combine(
                        Arbitraries.of(sel), arbitraryImportEndingWith(sel).list().ofSize(10))
                    .as(SelectorAndImports::new));
  }

  @Provide
  Arbitrary<Selector> ofSize2() {
    return arbitrarySelectorOfSize(2, 2);
  }

  @Provide
  Arbitrary<String> identifier() {
    return CommonTestUtil.arbitraryIdentifier();
  }
}
