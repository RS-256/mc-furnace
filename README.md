# mc-furnace

Pipeline that builds **mc-terra**: a private Git repository holding decompiled
Minecraft (Java Edition) sources and data files, one version per commit, so any
two versions can be compared with plain `git diff`.

> **Legal note:** Mojang's official mappings are licensed for reference
> purposes only. The generated `mc-terra` repository contains decompiled
> proprietary code and **must stay private**. Never redistribute its contents.
> This pipeline repository contains no Mojang assets and may be public.

## Requirements

- Java 21+ (JDK; the data generator reuses the current JVM when its major
  version suffices, see `config/jres.properties` for exact-version pinning)
- `git` on PATH
- Network access to piston-meta / Mojang download servers

Minecraft 1.14.4 and later are supported. Earlier versions do not provide the
Mojang mappings required by this pipeline and are outside its scope.

## Build & run

```bash
./gradlew installDist                                    # build the CLI
build/install/furnace/bin/furnace status                 # ingested / pending overview
build/install/furnace/bin/furnace add 1.21.10            # generate + commit one version
build/install/furnace/bin/furnace update                 # ingest everything new
build/install/furnace/bin/furnace backfill --from 1.14.4 # bulk-generate history
build/install/furnace/bin/furnace regen 1.21.10          # reproducibility check
```

Run the CLI from the repository root: configuration is read from `config/`,
and `cache/` (verified downloads) and `work/` (per-version intermediates) are
created in the current directory. The output repository location is set by
`terra.repo` in `config/furnace.properties` (default `../mc-terra`, created
and initialized automatically).

Interrupting a batch with Ctrl+C stops at a safe boundary; the second Ctrl+C
forces an immediate exit. Both are safe: a version only counts as ingested
once its commit exists, so **re-running the same command resumes** where the
batch stopped.

## Generation model

For each version, furnace downloads and SHA1-verifies the client, server, and
Mojang mapping files; unpacks bundled servers when necessary; remaps and merges
the client and server; decompiles the merged jar with Vineflower; extracts
`data/` and text-only `assets/`; runs the vanilla data generator for `reports/`;
and writes `version.json` before committing the result.

The generated tree contains `src/`, `data/`, `assets/`, `reports/`, and a
`version.json` file recording the Minecraft and toolchain metadata. Temporary
files are isolated under `work/<version-id>/`. A commit is the transaction
boundary: incomplete work is discarded on the next run, while completed
versions are detected from repository history and skipped.

Branch history is append-only and ordered by `releaseTime`; inserting an older
version requires rebuilding the history. Commit author and committer dates are
set to that version's `releaseTime`.

## Branch layout of mc-terra

| Branch      | Content                                                                 |
|-------------|-------------------------------------------------------------------------|
| `snapshots` | every version (snapshots, pre/rc, releases) in releaseTime order        |
| `releases`  | full releases only                                                      |
| `af/<id>`   | april fools versions, one dead-end commit on top of their base snapshot |

Tags carry the version id (`1.21.1`, `24w14a`; `/` replaced by `-`). Releases
are tagged on `releases`, snapshots on `snapshots`.

## Recommended git settings for browsing mc-terra

```bash
git config diff.renames true
git config diff.algorithm histogram
```

(Both are preconfigured when furnace initializes the repository.)

Example queries:

```bash
git diff 1.20.6 1.21 -- src/net/minecraft/world/level/levelgen/
git log --follow -p -- src/net/minecraft/util/RandomSource.java
git diff 1.21 1.21.1 -- data/minecraft/worldgen/
git diff --stat 1.20.4 1.20.5
git show af/24w14potato        # what the april fools build changed
```

## Configuration files (`config/`)

| File                       | Purpose                                                                                               |
|----------------------------|-------------------------------------------------------------------------------------------------------|
| `furnace.properties`       | shared defaults: paths, manifest URL, heap, scope start                                               |
| `furnace.local.properties` | **gitignored** personal overrides for any key above                                                   |
| `decompiler.properties`    | explicit Vineflower options (determinism)                                                             |
| `april_fools.yaml`         | april fools ids + their `af/` branch base versions                                                    |
| `excludes.txt`             | tree exclusion globs (binaries, non-en_us langs)                                                      |
| `overrides.yaml`           | manual releaseTime sort corrections                                                                   |
| `jres.properties`          | **gitignored** Java runtimes per required major (data generator); copy from `jres.properties.example` |

## Determinism policy

Tool versions are pinned via the Gradle version catalog and dependency lock
file, Vineflower options are fully explicit, and output uses LF / UTF-8 /
sorted file iteration. `furnace regen <id>` re-generates a version and diffs
it against the existing commit to verify bit-identical output. When upgrading
the decompiler, regenerate the whole history onto a fresh orphan branch —
mixing decompiler versions mid-history is forbidden.
