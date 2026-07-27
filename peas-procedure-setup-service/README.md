# PeasProcedureSetupService

(was the aps-submitter-prototype backend)

This project implements an HTTP server-based application using
TMT Executive Software ([ESW](https://github.com/tmtsoftware/esw)) APIs.

For general build instructions, CSW/ESW prerequisites, and environment
variable setup (`TMT_LOG_HOME`, `INTERFACE_NAME`, etc.), see the
[project root README](../README.md) -- those apply to every component in
this repo, not just this one.

## Running This Service

To start the app, run from the repo root:
```
sbt "peasProcedureSetupService/run start --port 8085"
```
This will start the app on port 8085 (per `application.conf`).

You can verify whether the application has started successfully by using the endpoint in `apptest.http` (e.g. using `curl` or a tool like [postman](https://www.postman.com/)).

NOTE: `<host>` needs to be replaced by the host address where app is running. Port also needs to be changed
if custom one is used.

## How to Use the Project
```bash
.
├── src
│   ├── main
│   │   ├── java
│   │   └── scala
```
* The template generates implementations for both Java and Scala. Both are not required to develop the app.
After you choose which language you want to develop in, you can delete the other. We encourage you to use Scala!
It has good support for asynchronous programming.

* The routes can be added in [PeasProcedureSetupServiceRoute](./src/main/scala/org/tmt/peasproceduresetupservice/http/PeasProcedureSetupServiceRoute.scala).
Some example routes have been provided.

* For adding a new authorization policy to your routes, the policy must be added to `securityDirectives` while defining the route.
For example, if you want to add policy such that only `esw-admin` should be able to access some route, then it could be done as shown
in below snippet. More information about authorization policies can be found in the  [AAS documentation](https://tmtsoftware.github.io/csw/services/aas/csw-aas-http.html#authorization-policies).
```
   path("endpoint") {
        securityDirectives.sPost(RealmRolePolicy("Esw-admin")) {
            // process request
        }
   }
```

* The API implementation can be added in [PeasProcedureSetupServiceImpl](./src/main/scala/org/tmt/peasproceduresetupservice/impl/PeasProcedureSetupServiceImpl.scala).
This template provides an implementation that matches the example routes. If Java is your preferred language, then the implementation
can be added as shown in JPeasProcedureSetupServiceImpl (Java variant, if present). In this case, a Scala wrapper
is required, as shown in [JPeasProcedureSetupServiceImplWrapperTest](./src/test/scala/org/tmt/peasproceduresetupservice/http/JPeasProcedureSetupServiceImplWrapperTest.scala)

* Core models for supporting the APIs should be added in the [models](./src/main/scala/org/tmt/peasproceduresetupservice/core/models) package.
Codecs for these models should be added in [HttpCodecs](./src/main/scala/org/tmt/peasproceduresetupservice/http/HttpCodecs.scala).

* [PeasProcedureSetupServiceWiring](./src/main/scala/org/tmt/peasproceduresetupservice/impl/PeasProcedureSetupServiceWiring.scala) is where the implementation wired up with the routes.

* [PeasProcedureSetupServiceApp](./src/main/scala/org/tmt/peasproceduresetupservice/PeasProcedureSetupServiceApp.scala) is the main runnable application. The command line arguments
for starting the app are defined in [PeasProcedureSetupServiceAppCommand](./src/main/scala/org/tmt/peasproceduresetupservice/PeasProcedureSetupServiceAppCommand.scala). Any new command
or option for command can be added like so:
```
 @CommandName("<command_name>")
  final case class <command_name>(
     @HelpMessage("<help message>")
     @ExtraName("<option>")
     option: <type>
   ) extends SampleAppCommand
```
* The newly added command/options need to handled in PeasProcedureSetupServiceApp

* Any new application specific configuration can be added in [application.conf](src/main/resources/application.conf)

## Cross-service note

The React frontend (`peas-web-application`, was the submitter frontend) resolves
this service via CSW location service using the prefix `APS.PeasProcedureSetupService`
(see `peas-web-application/src/utils/resolveBackend.ts`). If you ever change this
service's `application.conf` `prefix` value, that frontend file needs to change too.
