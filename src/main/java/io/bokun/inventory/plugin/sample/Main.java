package io.bokun.inventory.plugin.sample;

import java.io.*;
import java.util.*;
import java.util.stream.*;

import com.google.inject.*;
import com.google.inject.name.*;
import io.grpc.*;
import org.slf4j.*;

import static com.google.inject.Scopes.*;
import static com.google.inject.name.Names.*;

/**
 * <p>The entry point for launching the plugin. Also bootstraps Gradle.</p>
 *
 * <p>The following environment variables are mandatory:<ul>
 *     <li><tt>SAMPLE_PLUGIN_PORT</tt> - a number to use for binding on a TCP port, e.g. 8080</li>
 * </ul>
 * </p>
 *
 * @author Mindaugas Žakšauskas
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    /**
     * All environment variables will have this prefix.
     */
    private static final String ENVIRONMENT_PREFIX = "SAMPLE_";

    /**
     * gRPC server.
     */
    private Server server;

    /**
     * TCP port to use for listening for incoming Inventory Server requests.
     */
    private final int port;

    /**
     * The actual implementation of the plugin API.
     */
    private final SamplePlugin service;

    /**
     * Called by Gradle
     */
    @Inject
    public Main(@Named(ENVIRONMENT_PREFIX + "PLUGIN_PORT") int port, SamplePlugin service) {
        this.port = port;
        this.service = service;
    }

    /**
     * Starts the service: binds gRPC server, adds shutdown hook.
     *
     * @throws IOException if specified port can not be bound.
     */
    private void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(service)
                .build()
                .start();
        log.info("Server started, listening on port {}", port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            log.error("Shutting down gRPC server since JVM is shutting down");
            Main.this.stop();
            log.error("Server shut down");
        }));
    }

    /**
     * Stops the gRPC server.
     */
    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main launches the server from the command line.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        log.info("Starting server...");
        Injector injector = Guice.createInjector(new GuiceInitializer());

        Main server = injector.getInstance(Main.class);
        server.start();

        server.blockUntilShutdown();
        log.info("The server has been stopped.");
    }

    /**
     * Initializes environment variables as Guice injectables, configures all necessary objects to initialize etc.
     */
    private static class GuiceInitializer extends AbstractModule {

        @Override
        protected void configure() {
            Map<String, String> guiceSpecificVars = System.getenv().entrySet().stream()
                    .filter(entry -> entry.getKey().toUpperCase().startsWith(ENVIRONMENT_PREFIX))
                    .collect(Collectors.toMap(entry -> entry.getKey().toUpperCase(), Map.Entry::getValue));
            Binder binder = binder();
            bindProperties(binder, guiceSpecificVars);
            binder.bind(SamplePlugin.class).in(SINGLETON);
            binder.bind(Main.class).in(SINGLETON);
        }
    }
}
