package io.bokun.inventory.plugin.sample;

import java.io.*;
import java.time.*;
import java.util.*;

import javax.annotation.*;

import com.google.common.collect.*;
import com.google.inject.*;
import com.squareup.okhttp.*;
import io.bokun.inventory.common.api.grpc.*;
import io.bokun.inventory.common.api.grpc.Date;
import io.bokun.inventory.plugin.api.grpc.*;
import io.bokun.inventory.plugin.api.grpc.PluginConfigurationParameter;
import io.grpc.stub.*;
import org.slf4j.*;

import static io.bokun.inventory.common.api.grpc.PluginCapability.*;
import static io.bokun.inventory.common.api.grpc.PluginParameterDataType.*;
import static java.util.concurrent.TimeUnit.*;

/**
 * Inventory Service API implementation using gRPC transport.
 *
 * @author Mindaugas Žakšauskas
 */
public class SampleGrpcPlugin extends PluginApiGrpc.PluginApiImplBase {

    private static final Logger log = LoggerFactory.getLogger(SampleGrpcPlugin.class);

    /**
     * Default OkHttp read timeout: how long to wait (in seconds) for the backend to respond to requests.
     */
    private static final long DEFAULT_READ_TIMEOUT = 30L;

    private final OkHttpClient client;

    @Inject
    public SampleGrpcPlugin() {
        this.client = new OkHttpClient();
        client.setReadTimeout(DEFAULT_READ_TIMEOUT, SECONDS);
    }

    /**
     * Small helper/builder to provide a mandatory string parameter (expressed as gRPC object) for configuring this plugin.
     * This object will be returned as a part of plugin definition. System administrators will have to send <tt>String</tt> value which will be later
     * passed on to the plugin with every request.
     *
     * @param name name of this configuration parameter.
     * @return configuration parameter, expressed as gRPC object.
     */
    @Nonnull
    private static PluginConfigurationParameter asRequiredStringParameter(@Nonnull String name) {
        return PluginConfigurationParameter.newBuilder()
                .setName(name)
                .setType(STRING)
                .setRequired(true)
                .build();
    }

    /**
     * Same as {@link #asRequiredStringParameter(String)} but uses {@link PluginParameterDataType#LONG} type.
     */
    @Nonnull
    private static PluginConfigurationParameter asRequiredLongParameter(@Nonnull String name) {
        return PluginConfigurationParameter.newBuilder()
                .setName(name)
                .setType(LONG)
                .setRequired(true)
                .build();
    }

    /**
     * Return plugin definition. This includes plugin capabilities as well as configurable parameters.
     */
    @Override
    public void getDefinition(Empty request, StreamObserver<PluginDefinition> responseObserver) {
        log.trace("In ::getDefinition");
        responseObserver.onNext(
                PluginDefinition.newBuilder()
                        .setName("Sample gRPC plugin")
                        .setDescription("Provides availability and accepts bookings into <YourCompany> booking system. Uses gRPC protocol")
                        .addAllCapabilities(ImmutableList.of(SUPPORTS_AVAILABILITY, SUPPORTS_RESERVATIONS))
                        .addAllParameters(
                                ImmutableList.of(
                                        asRequiredStringParameter(Configuration.SAMPLE_API_SCHEME),         // e.g. https
                                        asRequiredStringParameter(Configuration.SAMPLE_API_HOST),           // e.g. your-api.your-company.com
                                        asRequiredLongParameter(Configuration.SAMPLE_API_PORT),             // e.g. 443
                                        asRequiredStringParameter(Configuration.SAMPLE_API_PATH),           // e.g. /api/1
                                        asRequiredStringParameter(Configuration.SAMPLE_API_USERNAME),
                                        asRequiredStringParameter(Configuration.SAMPLE_API_PASSWORD)
                                )
                        )
                        .build()
        );
        responseObserver.onCompleted();
        log.trace("Out ::getDefinition");
    }

    /**
     * Helper method which creates {@link HttpUrl.Builder} based on configuration (scheme/host/port/path).
     *
     * @param configuration configuration to use to create OkHttp url builder.
     * @return url builder which is ready to use.
     */
    @Nonnull
    private HttpUrl.Builder getUrlBuilder(@Nonnull Configuration configuration) {
        return new HttpUrl.Builder()
                .scheme(configuration.scheme)
                .host(configuration.host)
                .port(configuration.port)
                .encodedPath(configuration.apiPath);
    }

    /**
     * This method should list all your products
     */
    @Override
    public void searchProducts(SearchProductsRequest request, StreamObserver<BasicProductInfo> responseObserver) {
        log.trace("In ::searchProducts");
        Configuration configuration = Configuration.fromGrpcParameters(request.getParametersList());

        // Let's say we are about to call http(s)://yoururl/product to get a list of products
        HttpUrl.Builder urlBuilder = getUrlBuilder(configuration)
                        .addPathSegment("product");
        // Create HTTP get call using basic authorization, taking username/password from the same configuration
        Request getRequest = new Request.Builder()
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", Credentials.basic(configuration.username, configuration.password))
                .url(urlBuilder.build())
                .build();
        log.debug("Making HTTP GET request {}", getRequest);

        String httpResponseBody;
        try {
            client.newCall(getRequest)
                    .execute()
                    .body().string();
        } catch (IOException e) {
            log.error("I/O error while calling remote API", e);
            responseObserver.onError(e);
            return;
        }

        // Do something with httpResponseBody, e.g. convert this JSON into POJO and convert that POJO into BasicProductInfo
        // Don't forget to filter them by country and city, based on request parameters.
        BasicProductInfo basicProductInfo = BasicProductInfo.newBuilder()
                .setId("123")
                .setName("Mock product")
                .setDescription("Mock product description")
                .addPricingCategories(
                        PricingCategory.newBuilder()
                                .setId("ADT")
                                .setLabel("Adult")
                )
                .addPricingCategories(
                        PricingCategory.newBuilder()
                                .setId("CHD")
                                .setLabel("Child")
                )
                .addCities("London")
                .addCountries("GB")
                .build();
        responseObserver.onNext(basicProductInfo);      // you will likely want to run this in a loop, to return multiple products
        responseObserver.onCompleted();                 // make sure this call is never forgotten as IS will otherwise block waiting endlessly 
        log.trace("Successfully completed ::searchProducts");
    }

    /**
     * Return detailed information about one particular product by given ID.
     */
    @Override
    public void getProductById(GetProductByIdRequest request, StreamObserver<ProductDescription> responseObserver) {
        log.trace("In ::getProductById");
        Configuration configuration = Configuration.fromGrpcParameters(request.getParametersList());

        // similar to searchProducts except this should return a single product with a bit more information
        ProductDescription productDescription = ProductDescription.newBuilder()
                .setId("123")
                .setName("Mock product")
                .setDescription("Mock product description")
                .addPricingCategories(
                        PricingCategory.newBuilder()
                                .setId("ADT")
                                .setLabel("Adult")
                )
                .addPricingCategories(
                        PricingCategory.newBuilder()
                                .setId("CHD")
                                .setLabel("Child")
                )
                .addRates(
                        Rate.newBuilder()
                                .setId("standard")
                                .setLabel("Standard")
                )
                .setBookingType(BookingType.DATE_AND_TIME)
                .setProductCategory(ProductCategory.ACTIVITIES)
                .addTicketSupport(TicketSupport.TICKET_PER_BOOKING)
                .addCities("London")
                .addCountries("GB")
                .addStartTimes(
                        Time.newBuilder()
                                .setHour(8)
                                .setMinute(15)
                )
                .setAllYearOpeningHours(
                        OpeningHours.newBuilder()
                                .setMonday(
                                        OpeningHoursWeekday.newBuilder()
                                                .setOpen24Hours(false)
                                                .addTimeIntervals(
                                                        OpeningHoursTimeInterval.newBuilder()
                                                                .setOpenFrom("08:00")
                                                                .setOpenForHours(4)
                                                                .setOpenForMinutes(0)
                                                )
                                                .addTimeIntervals(
                                                        OpeningHoursTimeInterval.newBuilder()
                                                                .setOpenFrom("13:00")
                                                                .setOpenForHours(4)
                                                                .setOpenForMinutes(0)
                                                )
                                )
                )
                .addExtras(
                        Extra.newBuilder()
                                .setId("some-extra-id")
                                .setTitle("Some extra title")
                                .setDescription("Some extra description")
                                .setOptional(false)
                                .setMaxPerBooking(1)
                                .setLimitByPax(false)
                                .setIncreasesCapacity(false)
                )
                .setTicketType(TicketType.QR_CODE)
                .setMeetingType(MeetingType.MEET_ON_LOCATION)
                .setDropoffAvailable(false)
                .build();
        responseObserver.onNext(productDescription);
        responseObserver.onCompleted();
        log.trace("Successfully completed ::getProductById");
    }

    /**
     * A set of product ids provided, return their availability over given date range.
     * This will return a subset of product IDs passed on via ProductAvailabilityRequest.
     * Note: even though request contains capacity and date range, for a matching product it is enough to have availabilities for *some* dates over
     * requested period. Subsequent GetProductAvailability request will clarify precise dates and capacities.
     */
    @Override
    public void getAvailableProducts(ProductsAvailabilityRequest request, StreamObserver<ProductsAvailabilityResponse> responseObserver) {
        log.trace("In ::getAvailableProducts");
        responseObserver.onNext(
                ProductsAvailabilityResponse.newBuilder()
                        .setProductId("123")
                        .setActualCheckDone(false)
                        .build()
        );
        responseObserver.onCompleted();
        log.trace("Out ::getAvailableProducts");
    }

    /**
     * Get availability of a particular product over a date range. This request should follow GetAvailableProducts and provide more details on
     * precise dates/times for each product as well as capacity for each date. This call, however, is for a single product only (as opposed to
     * {@link #getAvailableProducts(ProductsAvailabilityRequest, StreamObserver)}) which checks many products but only does a basic shallow check.
     */
    @Override
    public void getProductAvailability(ProductAvailabilityRequest request, StreamObserver<ProductAvailabilityWithRatesResponse> responseObserver) {
        log.trace("In ::getProductAvailability");
        LocalDate tomorrow = LocalDate.now().plusDays(1L);
        responseObserver.onNext(
                ProductAvailabilityWithRatesResponse.newBuilder()
                        .setCapacity(10)
                        .setDate(
                                Date.newBuilder()
                                        .setYear(tomorrow.getYear())
                                        .setMonth(tomorrow.getMonthValue())
                                        .setDay(tomorrow.getDayOfMonth())
                        )
                        .setTime(
                                Time.newBuilder()
                                        .setHour(8)
                                        .setMinute(15)
                        )
                        .addRates(
                                RateWithPrice.newBuilder()
                                        .setRateId("standard")
                                        .setPricePerPerson(
                                                PricePerPerson.newBuilder()
                                                        .addPricingCategoryWithPrice(
                                                                PricingCategoryWithPrice.newBuilder()
                                                                        .setPricingCategoryId("ADT")
                                                                        .setPrice(
                                                                                Price.newBuilder()
                                                                                        .setAmount("100")
                                                                                        .setCurrency("EUR")
                                                                        )
                                                        )
                                                        .addPricingCategoryWithPrice(
                                                                PricingCategoryWithPrice.newBuilder()
                                                                        .setPricingCategoryId("CHD")
                                                                        .setPrice(
                                                                                Price.newBuilder()
                                                                                        .setAmount("10")
                                                                                        .setCurrency("EUR")
                                                                        )
                                                        )
                                        )
                        )
                        .build()
        );
        responseObserver.onCompleted();
        log.trace("Out ::getProductAvailability");
    }

    /**
     * This call secures necessary resource(s), such as activity time slot which can later become a booking. The reservation should be held for some
     * limited time, and reverted back to being available if the booking is not confirmed.
     *
     * Only implement this method if {@link PluginCapability#SUPPORTS_RESERVATIONS} is among capabilities of your {@link PluginDefinition}.
     * Otherwise you are only required to implement {@link #createAndConfirmBooking(CreateConfirmBookingRequest, StreamObserver)} which does both
     * reservation and confirmation, this method can be left empty or non-overridden.
     */
    @Override
    public void createReservation(ReservationRequest request, StreamObserver<ReservationResponse> responseObserver) {
        log.trace("In ::createReservation");
        responseObserver.onNext(
                ReservationResponse.newBuilder()
                        .setSuccessfulReservation(
                                SuccessfulReservation.newBuilder()
                                        .setReservationConfirmationCode(UUID.randomUUID().toString())
                        )
                        .build()
        );
        responseObserver.onCompleted();
        log.trace("Out ::createReservation");
    }

    /**
     * Once reserved, proceed with booking. This will be called in case if reservation has succeeded.
     */
    @Override
    public void confirmBooking(ConfirmBookingRequest request, StreamObserver<ConfirmBookingResponse> responseObserver) {
        log.trace("In ::confirmBooking");
        processBookingSourceInfo(request.getReservationData().getBookingSource());
        String confirmationCode = UUID.randomUUID().toString();

        List<TicketPerPricingCategory> tickets = new ArrayList<>();
        for (Reservation reservation : request.getReservationData().getReservationsList()) {
            for (Passenger passenger : reservation.getPassengersList()) {
                tickets.add(TicketPerPricingCategory.newBuilder()
                        .setPricingCategory(passenger.getPricingCategoryId())
                        .setTicket(Ticket.newBuilder().setQrTicket(QrTicket.newBuilder().setTicketBarcode("abc").build()).build()).build());

            }
        }

        TicketsPerPricingCategory ticketsPerPassenger = TicketsPerPricingCategory.newBuilder()
                .addAllTickets(tickets)
                .build();

        responseObserver.onNext(
                ConfirmBookingResponse.newBuilder()
                        .setSuccessfulBooking(
                                SuccessfulBooking.newBuilder()
                                        .setBookingConfirmationCode(confirmationCode)
                                        .setTicketsPerPassenger(ticketsPerPassenger)
                        )
                        .build()
        );



        responseObserver.onCompleted();
        log.trace("Out ::confirmBooking");
    }

    /**
     * Example code to get info about the booking initiator.
     * Here you can see which data is available in each bookingSource.getSegment() case
     * @param bookingSource bookinSource data structure that is provided in booking requests
     */
    void processBookingSourceInfo(BookingSource bookingSource) {
        log.trace("Sales segment: {}",
                bookingSource.getSegment().name());
        log.trace("Booking channel: {} '{}'",
                bookingSource.getBookingChannel().getId(),
                bookingSource.getBookingChannel().getTitle());
        switch (bookingSource.getSegment()) {
            case OTA:
                log.trace("OTA system: {}",
                        bookingSource.getBookingChannel().getSystemType());
                break;
            case MARKETPLACE:
                log.trace("Reseller vendor: {} '{}' reg.no. {}",
                        bookingSource.getMarketplaceVendor().getId(),
                        bookingSource.getMarketplaceVendor().getTitle(),
                        bookingSource.getMarketplaceVendor().getCompanyRegistrationNumber());
                break;
            case AGENT_AREA:
                log.trace("Booking agent: {} '{}' reg.no. {}",
                        bookingSource.getBookingAgent().getId(),
                        bookingSource.getBookingAgent().getTitle(),
                        bookingSource.getBookingAgent().getCompanyRegistrationNumber());
                break;
            case DIRECT_OFFLINE:
                log.trace("Extranet user: {} '{}'",
                        bookingSource.getExtranetUser().getEmail(),
                        bookingSource.getExtranetUser().getFullName());
                break;
        }
    }

    /**
     * Only implement this method if {@link PluginCapability#SUPPORTS_RESERVATIONS} is <b>NOT</b> among capabilities of your {@link PluginDefinition}.
     * Otherwise you are only required to implement both {@link #createReservation(ReservationRequest, StreamObserver)} and {@link
     * #confirmBooking(ConfirmBookingRequest, StreamObserver)} separately; this method should remain empty or non-overridden.
     */
    @Override
    public void createAndConfirmBooking(CreateConfirmBookingRequest request, StreamObserver<ConfirmBookingResponse> responseObserver) {
        log.trace("In ::createAndConfirmBooking");          // should never happen
        throw new UnsupportedOperationException();
    }

    /**
     * Once booked, a booking may be cancelled using booking ref number.
     * If your system does not support booking cancellation, one of the current workarounds is to create a cancellation policy (on the Bokun end)
     * which offers no refund. Then a cancellation does not have any monetary effect.
     */
    @Override
    public void cancelBooking(CancelBookingRequest request, StreamObserver<CancelBookingResponse> responseObserver) {
        log.trace("In ::cancelBooking");
        responseObserver.onNext(
                CancelBookingResponse.newBuilder()
                        .setSuccessfulCancellation(
                                SuccessfulCancellation.newBuilder()
                        )
                        .build()
        );
        responseObserver.onCompleted();
        log.trace("Out ::cancelBooking");
    }
}
