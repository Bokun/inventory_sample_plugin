package io.bokun.inventory.plugin.sample;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import javax.annotation.*;

import com.google.inject.*;
import com.google.inject.name.*;
import io.grpc.*;
import io.grpc.netty.*;
import io.netty.handler.ssl.*;
import io.undertow.*;
import io.undertow.server.*;
import org.slf4j.*;

import static com.google.inject.Scopes.*;
import static com.google.inject.name.Names.*;
import static io.grpc.Metadata.*;
import static io.netty.handler.ssl.ClientAuth.*;
import static io.netty.handler.ssl.SslProvider.*;

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

    /**
     * This value is what real server expects as well (if you're using shared secrets); don't change it as this will render the feature useless.
     */
    private static final String SHARED_SECRET_HEADER = "sharedSecret";

    private static final Metadata.Key<String> SHARED_SECRET_METADATA_KEY = Metadata.Key.of(SHARED_SECRET_HEADER, ASCII_STRING_MARSHALLER);

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    /**
     * All environment variables will have this prefix.
     */
    private static final String ENVIRONMENT_PREFIX = "SAMPLE_";

    @SuppressWarnings("rawtypes")
    private static final ServerCall.Listener NOOP_LISTENER = new ServerCall.Listener() {};

    /**
     * gRPC server.
     */
    private Server server;

    /**
     * TCP port to use for listening for incoming Inventory Server requests.
     */
    private final int port;

    /**
     * gRPC implementation of the plugin API.
     */
    private final SampleGrpcPlugin grpcService;

    /**
     * REST implementation of the plugin API.
     */
    private final SampleRestPlugin restService;

    /**
     * Called by Gradle
     */
    @Inject
    public Main(@Named(ENVIRONMENT_PREFIX + "PLUGIN_PORT") int port,
                SampleGrpcPlugin grpcService,
                SampleRestPlugin restService) {
        this.port = port;
        this.grpcService = grpcService;
        this.restService = restService;
    }

    /**
     * Starts the service: binds gRPC server, adds shutdown hook.
     *
     * @throws IOException if specified port can not be bound.
     */
    private void start() throws IOException {
        Map<String, String> environmentVariables = System.getenv();
        ServerBuilder<?> serverBuilder;

        // configure TLS/SSL if requested
        if (environmentVariables.containsKey("USE_TLS") && Boolean.TRUE.toString().equalsIgnoreCase(environmentVariables.get("USE_TLS"))) {
            if (!environmentVariables.containsKey("CERT_FILE") || !Files.exists(Paths.get(environmentVariables.get("CERT_FILE")))) {
                throw new IllegalStateException("Certificate file is required if running with TLS/SSL");
            }
            if (!environmentVariables.containsKey("KEY_FILE") || !Files.exists(Paths.get(environmentVariables.get("KEY_FILE")))) {
                throw new IllegalStateException("Key file is required if running with TLS/SSL");
            }
            File certFile = new File(environmentVariables.get("CERT_FILE"));
            SslContextBuilder sslContextBuilder = SslContextBuilder
                    .forServer(
                            certFile,
                            new File(environmentVariables.get("KEY_FILE"))
                    );
            GrpcSslContexts.configure(sslContextBuilder);
            SslContext sslContext = sslContextBuilder
                    .sslProvider(OPENSSL)
                    .trustManager(certFile)
                    .clientAuth(OPTIONAL)
                    .build();
            serverBuilder = NettyServerBuilder.forPort(port)
                    .sslContext(sslContext);
            log.info("Using TLS/SSL");
        } else {
            serverBuilder = ServerBuilder.forPort(port);
            log.info("Not using TLS/SSL");
        }

        // configure shared secret if set via environment variable
        if (environmentVariables.containsKey("SHARED_SECRET")) {
            String sharedSecret = environmentVariables.get("SHARED_SECRET");
            serverBuilder.addService(ServerInterceptors.intercept(grpcService, getSharedSecretCheckerInterceptor(sharedSecret)));
            log.info("Using shared secret for caller authentication");
        } else {
            serverBuilder.addService(grpcService);
            log.info("Not using shared secret for caller authentication");
        }

        server = serverBuilder.build();
        server.start();

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
     * Creates and returns a new interceptor which will intercept all service calls by checking shared secret against some predefined value.
     *
     * @return interceptor which should be wrapping real service with {@link ServerInterceptors#intercept(ServerServiceDefinition,
     * ServerInterceptor...)}
     */
    @Nonnull
    @SuppressWarnings("unchecked")
    public static ServerInterceptor getSharedSecretCheckerInterceptor(@Nonnull String sharedSecret) {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                         Metadata headers,
                                                                         ServerCallHandler<ReqT, RespT> next) {
                String sharedSecretValue = headers.get(SHARED_SECRET_METADATA_KEY);
                if (sharedSecretValue == null || !sharedSecret.equals(sharedSecretValue)) {
                    log.warn("Incoming request does not have matching shared secret {}", sharedSecretValue);
                    call.close(Status.UNAUTHENTICATED.withDescription("Incoming request does not have matching shared secret"), headers);
                    return NOOP_LISTENER;
                }
                ServerCall.Listener<ReqT> listener = Contexts.interceptCall(Context.current(), call, headers, next);
                return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(listener) {};
            }
        };
    }

    /**
     * Main launches the server from the command line.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        log.info("Starting server...");
        boolean isRest = (args.length == 1) && "-rest".equals(args[0]);
        boolean isGrpc = (args.length == 1) && "-grpc".equals(args[0]);

        if (!isRest && !isGrpc) {
            System.err.println("Usage: Main [OPTION]");
            System.err.println("  -rest Runs sample RESTful service");
            System.err.println("  -grpc Runs sample gRPC service");
            System.exit(1);
        }
        Injector injector = Guice.createInjector(new GuiceInitializer());
        Main server = injector.getInstance(Main.class);

        if (isGrpc) {
            server.start();
            server.blockUntilShutdown();
            log.info("gRPC server has been stopped.");
        }
        if (isRest) {
            Undertow.builder()
                    .addHttpListener(server.port, "localhost")
                    .setHandler(
                            new RoutingHandler()
                                    .get("/plugin/definition", server.restService::getDefinition)
                    )
                    .build()
                    .start();
        }
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
            binder.bind(SampleGrpcPlugin.class).in(SINGLETON);
            binder.bind(Main.class).in(SINGLETON);
        }
    }
}
