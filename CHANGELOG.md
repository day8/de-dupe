# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.3.0] - 2026-05-13

First release published to Clojars. The library has existed since 2015 but
was previously distributed as source only.

### Added

- Clojure (JVM) support. Sources are now `.cljc`; the library runs on both
  JVM Clojure and Node ClojureScript via reader conditionals.
- JVM substrate: a `java.util.HashMap` bucket store mirrors the CLJS-side
  `js/Map`. The dedup algorithm is identical across platforms.
- Edge-case test coverage: nil / empty collections / single-element
  collections, sets, sorted variants, deep nesting (200 levels), wide
  collections (1000 elements), quoted forms with potential `cache-N`
  symbol collisions, and a platform-parity assertion on the cache shape.
- Per-platform assertion-count floor in the test runner — silent
  test-loading failures (e.g. broken reader conditionals) now fail CI
  instead of producing a misleading green build.
- Tag-triggered Clojars deploy. Pushing a `v*` tag runs the full test
  suite then invokes `clojure -T:build deploy`.
- README badges (Clojars version, Test status, License), an elevator-pitch
  summary line, and a "When does this library help?" section with worked
  examples covering the cases where the output grows vs. shrinks
  dramatically.
- "When does this library help?" guidance addresses the longstanding
  question raised in [#2][issue-2] — small inputs grow, large
  heavily-shared inputs shrink dramatically, and the real win is
  round-trip preservation of `identical?` across the wire.

### Changed

- Build toolchain swapped from Leiningen to
  [tools.build](https://github.com/clojure/tools.build) +
  [slipset/deps-deploy](https://github.com/slipset/deps-deploy).
  `project.clj` removed; `build.clj` + the `:build` alias in `deps.edn`
  are the new entry points.
- Artifact coordinate is now `day8/de-dupe` on Clojars.
- Test runner exercises both CLJS (compile + Node) and JVM legs in a
  single `clojure -M:test` invocation.
- CI workflow modernised: actions pinned to commit SHAs per
  `day8` repository policy, concurrency cancellation on a per-branch
  basis, `setup-node` step added for the CLJS test leg, Clojure deps
  cached across runs.

### Fixed

- [#1][issue-1] — `[de-dupe "0.2.2"]` could not be resolved because no
  release was ever published to Clojars. The library now ships as
  `[day8/de-dupe "0.3.0"]`.
- [#2][issue-2] — closed by the new "When does this library help?"
  README section, which explains the small-input-grows behaviour and
  documents the real win on large duplicated structures.

### Removed

- `project.clj` — superseded by `build.clj` + the `:build` alias in
  `deps.edn`.
- `.cljs` test sources — replaced by `.cljc` test sources that exercise
  both CLJS and JVM legs.

## Earlier history

Versions 0.1.x and 0.2.x existed in the repository's `project.clj` but
were never tagged or published. The implementation has been stable since
2015; 0.3.0 is the first formally released version.

[issue-1]: https://github.com/day8/de-dupe/issues/1
[issue-2]: https://github.com/day8/de-dupe/issues/2
[Unreleased]: https://github.com/day8/de-dupe/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/day8/de-dupe/releases/tag/v0.3.0
