package io.bokun.inventory.plugin.sample;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import javax.annotation.*;

import com.google.inject.*;
import com.google.inject.name.*;
import io.grpc.*;
import io.grpc.netty.*;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.*;
import io.netty.util.ResourceLeakDetector;
import io.undertow.*;
import io.undertow.server.*;
import io.undertow.server.handlers.*;
import org.slf4j.*;

import static com.google.common.net.HttpHeaders.CONNECTION;
import static com.google.inject.Scopes.*;
import static com.google.inject.name.Names.*;
import static io.grpc.Metadata.*;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpUtil.is100ContinueExpected;
import static io.netty.handler.codec.http.HttpUtil.isKeepAlive;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
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

    /**
     * Monitoring port variable. (Optional. By default will use 8886 port)
     */
    public static final String MONITORING_PORT_ENV = ENVIRONMENT_PREFIX + "MONITORING_PORT";

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
     * TCP port to monitor service (optional to use)
     */
    @Inject(optional = true)
    @Named(MONITORING_PORT_ENV)
    private Integer monitoringPort = 8886;

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
        log.info("sample main port: {}, grpcService: {}, restService: {}", port, grpcService, restService);
    }

    /**
     * Starts the service: binds gRPC server, adds shutdown hook.
     *
     * @throws IOException if specified port can not be bound.
     */
    private void start() throws IOException {
        log.info("Start the system...");
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

        //start optional monitoring server
        try {
            Channel monitoringServerChannel = getOptionalMonitoringServer(monitoringPort).sync().channel();
            if (monitoringServerChannel != null) {
                monitoringServerChannel.closeFuture().sync();
            }
        } catch (InterruptedException e) {
            log.error("Monitoring server interrupted", e);
        }
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
                                    .post("/product/search", new BlockingHandler(server.restService::searchProducts))
                                    .post("/product/getById", new BlockingHandler(server.restService::getProductById))
                                    .post("/product/getAvailable", new BlockingHandler(server.restService::getAvailableProducts))
                                    .post("/product/getAvailability", new BlockingHandler(server.restService::getProductAvailability))
                                    .post("/booking/reserve", new BlockingHandler(server.restService::createReservation))
                                    .post("/booking/confirm", new BlockingHandler(server.restService::confirmBooking))
                                    .post("/booking/cancel", new BlockingHandler(server.restService::cancelBooking))
                    )
                    .build()
                    .start();
            log.info("Started REST service on port {}", server.port);
        }

        //Optional monitoring server
        Channel monitoringServerChannel = getOptionalMonitoringServer(server.monitoringPort).sync().channel();
        if (monitoringServerChannel != null) {
            monitoringServerChannel.closeFuture().sync();
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
            binder.bind(SampleRestPlugin.class).in(SINGLETON);
            binder.bind(Main.class).in(SINGLETON);
        }
    }

    private static ChannelFuture getOptionalMonitoringServer(Integer port) {
        if (port == null || port == 0) {
            return null;
        }

        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
        ServerBootstrap b = new ServerBootstrap();
        b.group(new NioEventLoopGroup())
                .channel(NioServerSocketChannel.class)
                .localAddress(new InetSocketAddress(port))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new HttpServerCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(1048576));
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                            @Override
                            public void channelReadComplete(ChannelHandlerContext ctx) {
                                ctx.flush();
                                ctx.close();
                            }

                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
                                log.debug("Monitoring request from {} to {}", ctx.channel().remoteAddress(), msg.uri());

                                ByteBuf buf = Unpooled.wrappedBuffer("{\"status\":\"UP\"}".getBytes());
                                DefaultFullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, buf);
                                response.headers().add(msg.headers());
                                response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
                                if (isKeepAlive(msg)) {
                                    response.headers().set(CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                                }
                                if (is100ContinueExpected(msg)) {
                                    ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
                                }

                                ctx.write(response);
                            }
                        });
                    }
                });
        ChannelFuture channelFuture = b.bind(port);
        log.debug("Monitoring server started, listening on {}", port);
        return channelFuture;
    }

}
