# aps-peas-prototype

Consolidated sbt/npm project for the TMT APS (Alignment Procedure System)
prototype suite. Combines what were previously four separate repositories
into one build, with component naming aligned to the ICD.

## Components

| ICD name | sbt project ID | Directory | What it is |
|---|---|---|---|
| ApsSequencerScripts | `apsSequencerScripts` / `apsSequencerScriptsRunner` | `aps-sequencer-scripts/` | Kotlin ESW sequencer scripts + Scala runner |
| PeasComputationAssembly | `peasComputationAssembly` | `peas-computation-assembly/assembly` | CSW assembly wrapping the APS Algorithm Library |
| PeasComputationDeploy | `peasComputationDeploy` | `peas-computation-assembly/deploy` | Container/host-config apps for deploying the assembly |
| (test client, no ICD name) | `peasComputationClient` | `peas-computation-assembly/client` | Standalone CLI client for sending test commands to the assembly |
| PeasProcedureDataService | `peasProcedureDataService` | `peas-procedure-data-service/` | jOOQ/Postgres-backed procedure data storage service |
| PeasProcedureSetupService | `peasProcedureSetupService` | `peas-procedure-setup-service/` | HTTP backend for building/submitting sequences |
| PeasWebApplication | *(npm, not sbt)* | `peas-web-application/` | React/TypeScript frontend |
| PeasExposureService | `peasExposureService` | `peas-exposure-service/` | New placeholder service (health-check stub only so far) |

Note: there used to be a `PeasComputationHcd` submodule -- it was a
`.g8`-template remnant that never did anything, and has been removed.

`peas-web-application` is a decoupled npm project (sibling to the sbt build,
not wired into it) -- build and run it independently with `npm`.

The [`scripts/`](scripts/) directory holds the auth/config setup scripts
referenced in the Startup Guide below (`setup-tmt-auth.sh`, `setup-config.sh`,
`generate-testmode-sequence.py`), carried over from the original
`aps-submitter-prototype` repo root with paths updated for the new structure.

## Build Instructions

The sbt portion of this project depends on libraries generated from the
[CSW](https://github.com/tmtsoftware/csw) and [ESW](https://github.com/tmtsoftware/esw)
projects. See [here](https://www.scala-sbt.org/1.0/docs/Setup.html) for
instructions on installing sbt.

Run `sbt compile` from this root directory to build everything, or scope to
a single project, e.g. `sbt peasProcedureSetupService/compile`.

For `peas-web-application`, see its own README -- it's `npm install` / `npm start`,
independent of the sbt build.

## CSW/ESW Prerequisites for Running Components

We recommend using coursier for installing and running the apps. Steps for
installing coursier are documented
[here](https://tmtsoftware.github.io/csw/apps/csinstallation.html).

The CSW services (Location, Config, Event, Alarm, Database, and AAS) need to
be running before starting any component in this project:

```
cs install csw-services
csw-services start --auth
```

**Note**: `csw-services` version should be compatible with the `csw.version` /
`esw.version` properties in [project/build.properties](project/build.properties)
and the dependency versions in [project/Libs.scala](project/Libs.scala).
You can refer to the ESW-to-CSW version compatibility table
[here](https://github.com/tmtsoftware/esw/blob/master/README.md).

You can run `csw-services --help` / `csw-services start --help` for more information.

## Startup Guide (full end-to-end sequence)

This is the complete order of operations to get every component running
together locally -- carried over from the original `aps-submitter-prototype`
top-level README (which was almost lost in the consolidation -- see
[MIGRATION.md](MIGRATION.md)), with all paths and commands updated to the
new consolidated structure. Run all commands from this repo's root
(`aps-peas-prototype/`) unless otherwise noted.

> **Important:** Keycloak and the Config Service reset on every `csw-services` restart.
> Steps 2 and 7 must be repeated each time.

Before starting any app, set the following environment variable:
* `TMT_LOG_HOME`

To set environment variables, use `export <ENV_VAR>=<VALUE>`.

By default, a network interface will be auto-selected. If you have problems,
or more than one network interface, set `INTERFACE_NAME` and
`PUBLIC_INTERFACE_NAME` explicitly -- for development, these can both be set
to your primary machine's interface name (e.g. `en0`). See the CSW docs on
[Network Topology](http://tmtsoftware.github.io/csw/deployment/network-topology.html)
for more information.

### 1. Start CSW Services

```bash
csw-services start --location --auth --config --event --database
```

### 2. Run Auth Setup Script

Must be run after every `csw-services` restart and before starting the ESW Gateway.

```bash
./scripts/setup-tmt-auth.sh
```

Expected output:
```
==> Getting admin token...
    OK
==> Creating tmt-backend-app client...
    OK
==> Getting tmt-frontend-app client UUID...
    OK (UUID: ...)
==> Adding tmt-backend-app audience mapper to tmt-frontend-app...
    OK
==> Getting esw-user1 user ID...
    OK (UUID: ...)
==> Getting aps-user role ID...
    OK (UUID: ...)
==> Assigning aps-user role to esw-user1...
    OK

Auth setup complete. You can now start the ESW Gateway.
```

### 3. Start ESW Gateway

```bash
cat > /tmp/command-role-mapping.conf << 'EOF'
APS.primary.startSequence: [aps-user]
EOF

esw-gateway-server start -p 8090 -l -c /tmp/command-role-mapping.conf
```

### 4. Start APS Sequencer

```bash
sbt "apsSequencerScriptsRunner/run sequencer -s APS -n primary -m APS_software_only_mode"
```

### 5. Start Procedure Setup Service

```bash
sbt "peasProcedureSetupService/run start --port 8085"
```

### 6. Start Web Application

```bash
cd peas-web-application
npm start
```

### 7. Load Sequence Data into Config Service

Must be run after every `csw-services` restart (Config Service resets too).

```bash
./scripts/setup-config.sh
```

### 8. Start the Computation Assembly

```bash
sbt "peasComputationDeploy/runMain peas.computationdeploy.ComputationDeployContainerCmdApp --local ./peas-computation-assembly/deploy/src/main/resources/JComputationAssemblyStandalone.conf"
```

### 9. Start the Procedure Data Service

```bash
DB_READ_USERNAME=admin DB_READ_PASSWORD=Zernike1 DB_WRITE_USERNAME=admin DB_WRITE_PASSWORD=Zernike1 sbt "peasProcedureDataService/run start -p 8084"
```

**Note:** these credentials are carried over verbatim from the original
README and match what's committed in `peas-procedure-data-service/jooq-codegen.xml`
(also flagged in [MIGRATION.md](MIGRATION.md)). Worth rotating at some point,
consolidation or not.

### 10. Use the App

1. Open `http://localhost:3000`
2. Log in with `esw-user1` / `esw-user1`
3. Enter config path: `/aps/sequences/testmode.json`
4. Click **Load Template**
5. Click **Submit Sequence**

Expected response:
```json
{
  "_type": "Completed",
  "runId": "...",
  "result": { "paramSet": [] }
}
```

### Startup Guide Notes

- The Keycloak admin UI at `http://localhost:8081` only shows the `master` realm.
  The TMT realm must be managed via the API -- the `setup-tmt-auth.sh` script handles this.
- Predefined TMT realm users (password = username):
  `esw-user1`, `config-admin1`, `config-user1`, `iris-user1`, `tcs-user1`, `wfos-user1`
- The Config Service resets on `csw-services` restart -- sequence files must be re-uploaded each time.
- Why `tmt-backend-app` must be created manually: the ESW Gateway's `application.conf` references
  this client for token validation, but the embedded Keycloak from `csw-services` does not include
  it by default. This appears to be a gap in the development tooling.

## Other component-specific details

For anything beyond the startup sequence above (route/impl structure, API
details, etc.), see each component's own README:
* [peas-procedure-setup-service/README.md](peas-procedure-setup-service/README.md)
* [peas-procedure-data-service/README.md](peas-procedure-data-service/README.md)
* [peas-computation-assembly/README.md](peas-computation-assembly/README.md)
* [peas-exposure-service](peas-exposure-service/) (port 8086, via `PEAS_EXPOSURE_SERVICE_PORT`)
* [peas-web-application/README.md](peas-web-application/README.md)

## Migration notes

See [MIGRATION.md](MIGRATION.md) for the record of what was renamed/moved
during consolidation from the four original repos, and what's still
unverified (dependency resolution, build verification, a plaintext credential
that predates this migration).
