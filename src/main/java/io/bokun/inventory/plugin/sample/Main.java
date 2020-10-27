package io.bokun.inventory.plugin.sample;

import com.google.inject.*;
import com.google.inject.name.Named;
import io.undertow.Undertow;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.BlockingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.name.Names.bindProperties;

/**
 * <p>The entry point for launching the plugin. Also bootstraps Gradle.</p>
 *
 * <p>The following environment variables are mandatory:<ul>
 * <li><tt>VIATOR_PLUGIN_PORT</tt> - a number to use for binding on a TCP port, e.g. 8080</li>
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

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    /**
     * All environment variables will have this prefix.
     */
    private static final String ENVIRONMENT_PREFIX = "VIATOR_";


    /**
     * TCP port to use for listening for incoming Inventory Server requests.
     */
    private final int port;


    /**
     * REST implementation of the plugin API.
     */
    private final ViatorRestPlugin restService;

    /**
     * Called by Gradle
     */
    @Inject
    public Main(@Named(ENVIRONMENT_PREFIX + "PLUGIN_PORT") int port,
                ViatorRestPlugin restService) {
        this.port = port;
        this.restService = restService;
    }


    /**
     * Main launches the server from the command line.
     */
    public static void main(String[] args) {
        log.info("Starting server...");

        Injector injector = Guice.createInjector(new GuiceInitializer());
        Main server = injector.getInstance(Main.class);


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
            binder.bind(Main.class).in(SINGLETON);
        }
    }
}
