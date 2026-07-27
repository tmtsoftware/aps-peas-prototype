# peasproceduredataservice

This project implements a prototype of the APS Procedure Data Service, an HTTP server-based application using
TMT Executive Software ([ESW](https://github.com/tmtsoftware/esw)) APIs.

This project was originated using the giter8 template: [csw-http-service-template.g8](https://github.com/tmtsoftware/csw-http-service-template.g8)

The prototype supports two endpoints: /storeProcedureComputationResults and /getProcedureResultData.
The endpoint APIs match the corresponding endpoints defined in the openAPI description provided in the APS ICD API documentation:
(https://docushare.tmt.org/docushare/dsweb/Get/Document-96223/ICD-SDB-APS-APS_CCR03.pdf)

The endpoint implementation uses a Concurrent Hashmap to store the data keys:
```
case class ComputationResultKey(
   computationName: String,
   fieldName: String,
   iterationNumber: Optional[Integer] = Optional.empty()
)
```
and values:
```
case class GenericValue(
   `type`: String,
   dim1: Int,
   dim2: Int,
   encodedStringValue: String
)
```
The implementation for the endpoints is in Java, with the code being encapsulated in a single class:
JPeasProcedureDataServiceImpl.java

## jOOQ Integration
Locally, a hashmap is used as a cache for procedure result values.  For long term store, a database is used
and jOOQ is used for database storing and retrieval.  jOOQ relies on generated classes to perform these functions.
### jOOQ code generation
jOOQ code generation with sbt requires specific changes to the sbt project: 
* Libs.scala: defining vals jooq, jooq-meta, jooq-codegen, and postgresql
* build.sbt: defining the generateJooq task (which includes the name of the database to perfom code generation on, plus database user and password to use)
* plugins.sbt: defining library dependencies using the vals from Libs.scala

To generate the jOOQ code, run: 
```
sbt generateJooq
```
Generated files are in:
src/main/java/org/tmt/peasproceduredataservice/db.generated

## Prerequisites for running service

These instructions assume postgres database is installed on target machine.
The following instructions are required to run CSW services, in particular
the database service.

### Setup database and Database service
Create a directory for the database under the account that will be running CSW services.  Set the environment variable PGDATA to point to that directory.

run `<initdb $PGDATA -E utf8>`

`<csw-services start -d>`

`<vi /tmp/pg_hba.conf*.tmp>`   and change all ‘passwords’ to ‘trust’

`<ps auwx | grep postgres>`

`<kill -HUP <postgresPid>>`

Now a psql session should be possible:

`<psql postgres -h localhost>`

and create the user/password you will be using to access the database:

`<CREATE USER <username>;>`

`<ALTER USER <username> WITH PASSWORD '<mypassword>';`>


And create the associated database:

`<CREATE DATABASE <username>;>`

Exit the psql session and restart the csw-services.



## Build Instructions

The build is based on sbt and depends on libraries generated from the
[ESW](https://github.com/tmtsoftware/esw) project.

See [here](https://www.scala-sbt.org/1.0/docs/Setup.html) for instructions on installing sbt.

## Prerequisites for Running App

We recommend using coursier for installing and running the apps. Steps for installing coursier are documented 
[here](https://tmtsoftware.github.io/csw/apps/csinstallation.html) 

The CSW AAS Service needs to be running before starting the components.
Follow below instructions to run AAS:

```
cs install csw-services
csw-services start --auth
```

**Note**: `csw-services` version should be compatible with the `ESW` version specified in [Libs.scala](project/Libs.scala). 
You can refer the ESW to CSW version compatibility table [here](https://github.com/tmtsoftware/esw/blob/master/README.md).

This will start AAS.
You can run `csw-services start --help` to get more information.

## Running the App

Before we start the app we need to set the following environment variables:
* TMT_LOG_HOME

To set environment variables, use the command `export <ENV_VAR> = <VALUE>`

By default, an interface name will be selected for you.  However, if you are having problems or have more than a single network interface, you may need to set
the environment variables `INTERFACE_NAME` and `PUBLIC_INTERFACE_NAME` explicitly.  For development, these two variables 
can be set to the primary machine 
interface name. For example, `en0`.  See the CSW documentation on [Network Topology](http://tmtsoftware.github.io/csw/deployment/network-topology.html) for more information.

To start the app, run:
`sbt "run start"`
This will start the app with default port 8084. 

If you want to start the app at custom port,
run `sbt "run start -p <port number>`

You can verify whether the application has started successfully by using the endpoint in `apptest.http` (e.g. using `curl` or a tool like [postman](https://www.postman.com/)).

NOTE: `<host>` needs to be replaced by the host address where app is running. Port also needs to be changed 
if custom one is used.

## How to Use the Project
```bash
.
├── src
│   ├── main
│   │   ├── java
│   │   └── scala
```
* The template generates implementations for both Java and Scala. Both are not required to develop the app. 
After you choose which language you want to develop in, you can delete the other. We encourage you to use Scala! 
It has good support for asynchronous programming.

* The routes can be added in [PeasProcedureDataServiceRoute](./src/main/scala/org/tmt/peasproceduredataservice/http/PeasProcedureDataServiceRoute.scala).
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

* The API implementation can be added in [PeasProcedureDataServiceImpl](./src/main/scala/org/tmt/peasproceduredataservice/core/PeasProcedureDataServiceImpl.scala).
This template provides an implementation that matches the example routes. If Java is your preferred language, then the implementation
can be added as shown in [JPeasProcedureDataServiceImpl](./src/main/java/org/tmt/peasproceduredataservice/core/JPeasProcedureDataServiceImpl.java). In this case, a Scala
is required, as shown in [JPeasProcedureDataServiceImplWrapper](./src/main/scala/org/tmt/peasproceduredataservice/http/JPeasProcedureDataServiceImplWrapper.scala)

* Core models for supporting the APIs should be added in the [models](./src/main/scala/org/tmt/peasproceduredataservice/core/models) package.
Codecs for these models should be added in [HttpCodecs](./src/main/scala/org/tmt/peasproceduredataservice/http/HttpCodecs.scala).

* [PeasProcedureDataServiceWiring](./src/main/scala/org/tmt/peasproceduredataservice/PeasProcedureDataServiceWiring.scala) is where the implementation wired up with the routes.

* [PeasProcedureDataServiceApp](./src/main/scala/org/tmt/peasproceduredataservice/PeasProcedureDataServiceApp.scala) is the main runnable application. The command line arguments 
for starting the app are defined in [PeasProcedureDataServiceAppCommand](./src/main/scala/org/tmt/peasproceduredataservice/PeasProcedureDataServiceAppCommand.scala). Any new command  
or option for command can be added like so:
```
 @CommandName("<command_name>")
  final case class <command_name>(
     @HelpMessage("<help message>")
     @ExtraName("<option>")
     option: <type>
   ) extends SampleAppCommand
```
* The newly added command/options need to handled in PeasProcedureDataServiceApp

* Any new application specific configuration can be added in [application.conf](src/main/resources/application.conf)
