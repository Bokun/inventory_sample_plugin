### Skeleton implementation for Bókun's Inventory Service plugin.

This project should give you a head start if you are implementing Inventory Service plugin in Java.

It includes necessary dependencies, grpc-generated API skeleton objects and gives a general impression on how we implement those plugins ourselves.
Note that this project has multiple branches: as we (loosely) follow git flow semantics, we use `develop` branch for development version and `master` branch for last stable release.
That said, the general advice is to start with latest `develop` in order to be in line with the latest API version.

We also provide an end-to-end integration test which runs a local copy of Inventory Service which in turn calls your plugin and validates some of the inputs/outputs.
