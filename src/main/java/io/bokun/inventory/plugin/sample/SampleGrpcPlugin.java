package io.bokun.inventory.plugin.sample;

import java.time.*;
import java.util.*;

import javax.annotation.*;

import com.google.common.collect.*;
import com.google.inject.*;
import io.bokun.inventory.common.api.grpc.Date;
import io.bokun.inventory.common.api.grpc.*;
import io.bokun.inventory.plugin.api.grpc.PluginConfigurationParameter;
import io.bokun.inventory.plugin.api.grpc.*;
import io.grpc.stub.*;
import okhttp3.*;
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
        this.client = new OkHttpClient.Builder()
                .readTimeout(DEFAULT_READ_TIMEOUT, SECONDS)
                .build();
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
                        .addAllCapabilities(ImmutableList.of(SUPPORTS_AVAILABILITY, SUPPORTS_RESERVATIONS, SUPPORTS_AMENDMENT, SUPPORTS_RESERVATION_CANCELLATION))
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
        var configuration = Configuration.fromGrpcParameters(request.getParametersList());

        // At this point you might want to call your external system to do the actual search and return data back.
        // Code below just provides some mocks.

        var basicProductInfo = BasicProductInfo.newBuilder()
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
        var configuration = Configuration.fromGrpcParameters(request.getParametersList());

        // At this point you might want to call your external system to do the actual get and return data back.
        // Code below just provides some mocks.

        // similar to searchProducts except this should return a single product with a bit more information
        var productDescription = ProductDescription.newBuilder()
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
                // questions asked once per booking; answers arrive in ReservationData::answers
                .addQuestionsPerBooking(
                        Question.newBuilder()
                                .setId("hotel")
                                .setType(Question.QuestionType.TEXT)
                                .setLabel("Name of your hotel")
                )
                .addQuestionsPerBooking(
                        Question.newBuilder()
                                .setId("arrival")
                                .setType(Question.QuestionType.DATE_AND_TIME)
                                .setLabel("When do you arrive in London?")
                )
                .addQuestionsPerBooking(
                        Question.newBuilder()
                                .setId("language")
                                .setType(Question.QuestionType.OPTIONS)
                                .setLabel("Preferred tour language")
                                .setRequired(true)
                                .addOptions("English")
                                .addOptions("Spanish")
                                .addOptions("German")
                )
                // questions asked for each passenger; answers arrive in Passenger::answers
                .addQuestionsPerPassenger(
                        Question.newBuilder()
                                .setId("age")
                                .setType(Question.QuestionType.INT)
                                .setLabel("Age")
                                .setRequired(true)
                )
                .addQuestionsPerPassenger(
                        Question.newBuilder()
                                .setId("dob")
                                .setType(Question.QuestionType.DATE)
                                .setLabel("Date of birth")
                )
                .addQuestionsPerPassenger(
                        Question.newBuilder()
                                .setId("weight")
                                .setType(Question.QuestionType.DOUBLE)
                                .setLabel("Weight (kg)")
                )
                .addQuestionsPerPassenger(
                        Question.newBuilder()
                                .setId("wheelchair")
                                .setType(Question.QuestionType.BOOLEAN)
                                .setLabel("Do you need wheelchair access?")
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
     * Get availability of a particular product over a date range. This should provide details on precise dates/times as well as capacity for
     * each date.
     */
    @Override
    public void getProductAvailability(ProductAvailabilityRequest request, StreamObserver<ProductAvailabilityWithRatesResponse> responseObserver) {
        log.trace("In ::getProductAvailability");
        log.trace("Seller (vendor id) asking for availability: {}", request.getSellerId());

        // At this point you might want to call your external system to do the actual get and return data back.
        // Code below just provides some mocks.

        var tomorrow = LocalDate.now().plusDays(1L);
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

        // At this point you might want to call your external system to do the actual reservation and return data back.
        // Code below just provides some mocks.

        processAnswers(request.getReservationData());
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
     *
     * Only implement this method if {@link PluginCapability#SUPPORTS_RESERVATIONS} is among capabilities of your {@link PluginDefinition}.
     * Otherwise you are only required to implement {@link #createAndConfirmBooking(CreateConfirmBookingRequest, StreamObserver)} which does both
     * reservation and confirmation, this method can be left empty or non-overridden.
     */
    @Override
    public void confirmBooking(ConfirmBookingRequest request, StreamObserver<ConfirmBookingResponse> responseObserver) {
        log.trace("In ::confirmBooking");

        // At this point you might want to call your external system to do the actual confirmation and return data back.
        // Code below just provides some mocks.

        processBookingSourceInfo(request.getReservationData().getBookingSource());
        processAnswers(request.getReservationData());
        var confirmationCode = UUID.randomUUID().toString();
        responseObserver.onNext(
                ConfirmBookingResponse.newBuilder()
                        .setSuccessfulBooking(
                                SuccessfulBooking.newBuilder()
                                        .setBookingConfirmationCode(confirmationCode)
                                        .setBookingTicket(
                                                Ticket.newBuilder()
                                                        .setQrTicket(
                                                                QrTicket.newBuilder()
                                                                        .setTicketBarcode(confirmationCode + "_ticket")
                                                        )
                                        )
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
     * Example code to read answers to the questions of the product (see ProductDescription::questionsPerBooking and ::questionsPerPassenger in
     * {@link #getProductById(GetProductByIdRequest, StreamObserver)}). Question ids are the ones returned by this plugin.
     * Only the questions which the vendor mapped to Bokun questions are answered, and an optional question might be left unanswered: answers
     * which are missing are simply not sent.
     * @param reservationData reservation data structure that is provided in reservation and booking requests
     */
    void processAnswers(ReservationData reservationData) {
        for (var answer : reservationData.getAnswersList()) {
            log.trace("Booking answer: {} = {}", answer.getQuestionId(), answerToString(answer));
        }
        for (var reservation : reservationData.getReservationsList()) {
            for (var passenger : reservation.getPassengersList()) {
                for (var answer : passenger.getAnswersList()) {
                    log.trace("Passenger ({} {}) answer: {} = {}", passenger.getContact().getFirstName(), passenger.getContact().getLastName(),
                            answer.getQuestionId(), answerToString(answer));
                }
            }
        }
    }

    /**
     * Exactly one of the *Answer fields is set, depending on the type of the question.
     */
    private static String answerToString(Answer answer) {
        return switch (answer.getAnswerCase()) {
            case TEXTANSWER -> answer.getTextAnswer();                                      // TEXT
            case INTANSWER -> String.valueOf(answer.getIntAnswer());                        // INT
            case DOUBLEANSWER -> String.valueOf(answer.getDoubleAnswer());                  // DOUBLE
            case BOOLEANANSWER -> String.valueOf(answer.getBooleanAnswer());                // BOOLEAN
            case DATEANSWER -> toLocalDate(answer.getDateAnswer()).toString();              // DATE
            case DATEANDTIMEANSWER -> LocalDateTime.of(                                     // DATE_AND_TIME
                    toLocalDate(answer.getDateAndTimeAnswer().getDate()),
                    LocalTime.of(answer.getDateAndTimeAnswer().getTime().getHour(), answer.getDateAndTimeAnswer().getTime().getMinute())
            ).toString();
            case OPTIONSANSWERS -> String.join(", ", answer.getOptionsAnswers().getValuesList());     // OPTIONS
            case ANSWER_NOT_SET -> "";
        };
    }

    private static LocalDate toLocalDate(Date date) {
        return LocalDate.of(date.getYear(), date.getMonth(), date.getDay());
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

        // At this point you might want to call your external system to do the actual cancellation and return data back.
        // Code below just provides some mocks.

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

    @Override
    public void cancelReservation(CancelReservationRequest request, StreamObserver<CancelReservationResponse> responseObserver) {
        log.trace("In ::cancelReservation");

        // At this point you might want to call your external system to do the actual cancellation and return data back.
        // Code below just provides some mocks.

        responseObserver.onNext(
                CancelReservationResponse.newBuilder()
                        .setSuccessfulReservationCancellation(
                                SuccessfulReservationCancellation.newBuilder()
                        )
                        .build()
        );
        responseObserver.onCompleted();
        log.trace("Out ::cancelReservation");
    }

    @Override
    public void amendBooking(AmendBookingRequest request, StreamObserver<AmendBookingResponse> responseObserver) {
        log.trace("In ::amendBooking");

        // At this point you might want to call your external system to do the actual amendment and return data back.
        // Code below just provides some mocks.

        processBookingSourceInfo(request.getReservationData().getBookingSource());
        processAnswers(request.getReservationData());
        var confirmationCode = UUID.randomUUID().toString();
        responseObserver.onNext(
                AmendBookingResponse.newBuilder()
                        .setSuccessfulAmendment(
                                SuccessfulAmendment.newBuilder()
                                        .setAmendmentConfirmationCode(confirmationCode)
                                        .setBookingTicket(
                                                Ticket.newBuilder()
                                                        .setQrTicket(
                                                                QrTicket.newBuilder()
                                                                        .setTicketBarcode(confirmationCode + "ticket_amended")
                                                        )
                                        )
                        )
                        .build()
        );
        responseObserver.onCompleted();
        log.trace("Out ::amendBooking");
    }
}
