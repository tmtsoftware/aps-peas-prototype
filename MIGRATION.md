# Migration record: 4 repos -> aps-peas-prototype

This consolidation is done -- real code from all four repos has been copied,
renamed, and cross-checked. This document records what was done and what
still needs your attention before this builds and runs.

## Source repos (fresh-copy, no git history preserved, per your call)
- https://github.com/tmtsoftware/aps-sequencer-prototype
- https://github.com/tmtsoftware/aps-computation-assembly-prototype
- https://github.com/tmtsoftware/aps-procedure-data-service
- https://github.com/tmtsoftware/aps-submitter-prototype

## Final naming map

| Old | New | sbt project ID | Directory |
|---|---|---|---|
| aps-sequencer-prototype (scripts) | ApsSequencerScripts | `apsSequencerScripts` | `aps-sequencer-scripts/scripts` |
| aps-sequencer-prototype (runner) | -- | `apsSequencerScriptsRunner` | `aps-sequencer-scripts/runner` |
| aps-computationprototypeassembly | PeasComputationAssembly | `peasComputationAssembly` | `peas-computation-assembly/assembly` |
| aps-computationprototypehcd | PeasComputationHcd | `peasComputationHcd` | `peas-computation-assembly/hcd` |
| aps-computationprototypedeploy | PeasComputationDeploy | `peasComputationDeploy` | `peas-computation-assembly/deploy` |
| aps-computationclient | PeasComputationClient | `peasComputationClient` | `peas-computation-assembly/client` |
| aps-procedure-data-service | PeasProcedureDataService | `peasProcedureDataService` | `peas-procedure-data-service` |
| aps-submitter-prototype backend | PeasProcedureSetupService | `peasProcedureSetupService` | `peas-procedure-setup-service` |
| aps-submitter-prototype frontend | PeasWebApplication | (npm, not sbt) | `peas-web-application` |
| *(new)* | PeasExposureService | `peasExposureService` | `peas-exposure-service` |

Note: `ApsSequencerScripts` intentionally keeps the `Aps` prefix per your
explicit confirmation -- everything else uses `Peas`.

## Third pass -- a real miss, caught by Scott

I had only copied the `apssubmitterprototype-backend/README.md` and
`apssubmitterprototype-frontend/README.md` subfolder READMEs during the
original consolidation. I never looked at or copied
**`aps-submitter-prototype/README.md`** -- the top-level repo README --
which turned out to contain the actual most important content: a complete,
ordered, end-to-end startup guide covering all nine components together
(CSW services -> auth setup -> ESW gateway -> sequencer -> setup service ->
frontend -> config load -> computation assembly -> data service -> using the
app). I also never copied the `scripts/` folder
(`setup-tmt-auth.sh`, `setup-config.sh`, `generate-testmode-sequence.py`)
that guide depends on.

Fixed by:
- Copying all three scripts into `scripts/` at the new repo root, updating
  every reference to `apssubmitterprototype-backend` -> `peas-procedure-setup-service`.
- Along the way, fixed a pre-existing path bug in `generate-testmode-sequence.py`:
  it computed its output directory as `os.path.dirname(__file__)` joined
  directly with the service directory name, which would have written into
  `scripts/peas-procedure-setup-service/...` instead of the sibling directory.
  Added the missing `'..'` path component.
- Rewriting the full startup guide into the root `README.md` as its own
  "Startup Guide" section, with every command/path updated to the new
  project structure (e.g. `sbt "apsSequencerScriptsRunner/run sequencer ..."`
  instead of `cd aps-sequencer-prototype && sbt "runner/run sequencer ..."`).

## Second pass (after initial consolidation)

- **Removed `PeasComputationHcd` entirely.** Confirmed with you it was a
  `.g8`-template remnant that never did anything. Deleted
  `peas-computation-assembly/hcd/`, removed `peasComputationHcd` from
  `build.sbt` (aggregate list, project definition, and `peasComputationDeploy`'s
  `dependsOn`), stripped the HCD component/connection out of
  `ComputationDeployContainer.conf` and `JComputationDeployContainer.conf`,
  deleted the now-orphaned `ComputationHcdStandalone.conf` /
  `JComputationHcdStandalone.conf` from `deploy/src/main/resources`, and fixed
  a boilerplate comment in `ComputationAssemblyHandlers.scala` that referenced
  it as an example.
- **Moved generic build/deploy instructions to a new root `README.md`.**
  The `aps-submitter-prototype` backend README's "Build Instructions" and
  "Prerequisites for Running App" sections (installing sbt/coursier, starting
  `csw-services` with AAS, setting `TMT_LOG_HOME`/`INTERFACE_NAME`) applied to
  the whole project, not just that one service -- moved to
  `aps-peas-prototype/README.md`. `peas-procedure-setup-service/README.md` now
  points there and keeps only what's actually specific to that service (its
  run command/port, route/impl/wiring structure).

## First pass -- what was actually renamed (verified with a repo-wide grep -- clean)

- **Package directories & declarations**: `org.tmt.apssubmitterprototype` ->
  `org.tmt.peasproceduresetupservice`; `org.tmt.apsproceduredataservice` ->
  `org.tmt.peasproceduredataservice`; `aps.computationprototypeassembly` ->
  `peas.computationassembly`; `aps.computationprototypehcd` ->
  `peas.computationhcd`; `aps.computationprototypedeploy` ->
  `peas.computationdeploy`.
- **Class/file names**: e.g. `ApsSubmitterPrototypeApp.scala` ->
  `PeasProcedureSetupServiceApp.scala`, `ComputationprototypeassemblyHandlers.scala`
  -> `ComputationAssemblyHandlers.scala`, and all their Java/test counterparts.
- **Location-service prefixes** (the runtime-critical, easy-to-miss part):
  - `APS.apssubmitterprototype` -> `APS.PeasProcedureSetupService`
    (`peas-procedure-setup-service/src/main/resources/application.conf`)
  - `CSW.apsproceduredataservice` -> `CSW.PeasProcedureDataService`
    (`peas-procedure-data-service/src/main/resources/application.conf`)
  - `APS.computationPrototypeAssembly` -> `APS.PeasComputationAssembly`,
    `APS.computationPrototypeHcd` -> `APS.PeasComputationHcd`
    (container conf, reference.confs, standalone confs, and
    `peas-computation-assembly/client/src/main/scala/ComputationClient.scala`)
- **Frontend cross-reference**: `peas-web-application/src/utils/resolveBackend.ts`
  updated to resolve `APS.PeasProcedureSetupService` -- this is exactly the kind
  of stale-string bug we flagged during planning; confirmed it existed and fixed it.
- **jOOQ generated code + codegen target package**: `org.tmt.apsproceduredataservice.db.generated`
  -> `org.tmt.peasproceduredataservice.db.generated` in both the generated
  Java sources and `jooq-codegen.xml`.
- **Ports** (unchanged, just confirmed no collision): data-service 8084,
  setup-service 8085, exposure-service 8086 (new, via `PEAS_EXPOSURE_SERVICE_PORT` env var, defaulted).
- **READMEs**: updated in each module to reference the new names, paths, and
  sbt project IDs. Also fixed two pre-existing documentation bugs unrelated to
  this migration: the setup-service README referenced a casing scheme that
  never matched its actual class names, and quoted the wrong port (8084
  instead of 8085).

## Root build.sbt

Real dependency versions pulled from the four repos' own `Libs.scala`/
`Dependencies.scala` -- see `project/Libs.scala`. Key versions: CSW 6.0.0,
ESW 1.0.2, esw-http-template-wiring v1.0.0, jOOQ 3.19.6, Postgres driver 42.7.3,
Pekko 1.1.0/1.1.3, sbt 1.10.6, Scala 3.6.4, Kotlin 2.1.10.

The computation-assembly family's shared settings (previously a global
`Common.scala` AutoPlugin in that repo) are now inlined as
`computationAssemblyFamilySettings` in the root `build.sbt`, applied
explicitly only to `peasComputationAssembly`/`peasComputationHcd`/
`peasComputationDeploy`/`peasComputationClient` -- deliberately *not* made
build-wide, so it doesn't silently change defaults (fork, resolvers,
scalacOptions) for the other four projects that never had them.

## Things I could NOT verify or resolve -- need your attention

1. **`algorithm-lib` dependency doesn't have a clear resolver.**
   `peasComputationAssembly` depends on `org.tmt.aps.peas % algorithm-lib % 1.0.0`.
   The original repo's `build.sbt` only declares a `jitpack` resolver, which
   doesn't appear to host this artifact. There may be a private resolver in
   your global `~/.sbt/1.0/repositories` or similar that isn't visible from
   the repo. Confirm this still resolves.

2. **`jooq-codegen.xml` has a plaintext DB password committed**
   (`admin`/`Zernike1`, in `peas-procedure-data-service/jooq-codegen.xml`).
   I didn't touch it, just flagging it since it's now more visible sitting in
   a consolidated repo.

3. **Cannot build-verify anything.** This sandbox has no network access to
   Maven Central, jitpack, or any TMT artifact repo -- only npm/PyPI/crates/GitHub
   raw content. Nothing here has been compiled. First thing to do once you have
   this locally: `sbt compile` from the root and see what falls out.

4. **`docs` subprojects dropped.** Both `aps-submitter-prototype` and
   `aps-procedure-data-service` had a Paradox/GitHub-Pages `docs` subproject
   (`sbt-docs` plugin). Not carried over -- unrelated to consolidation, easy
   to re-add if you want the docs site back.

5. **sbt-kotlin-plugin org** in `project/plugins.sbt` is
   `org.jetbrains.scala % sbt-kotlin-plugin % 3.1.4`, confirmed against the
   real `aps-sequencer-prototype/project/plugins.sbt` (my first placeholder
   guess in this document had the wrong org — corrected).

## Order I'd suggest for your first local pass

1. `cd` into the repo, `sbt compile` from root, see what breaks first
   (probably `algorithm-lib` resolution).
2. Once it compiles, `sbt "peasComputationDeploy/run"` etc. against a running
   `csw-services` to confirm location-service registration actually resolves
   with the new prefixes end-to-end (assembly <-> hcd <-> client).
3. `cd peas-web-application && npm install && npm start` against a running
   `peasProcedureSetupService` to confirm the frontend's location-service
   resolution against `APS.PeasProcedureSetupService` actually works.
