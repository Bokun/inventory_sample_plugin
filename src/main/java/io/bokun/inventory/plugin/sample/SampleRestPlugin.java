package io.bokun.inventory.plugin.sample;

import java.io.*;
import java.time.*;
import java.util.*;

import javax.annotation.*;

import com.google.common.collect.*;
import com.google.gson.*;
import com.google.inject.*;
import io.bokun.inventory.plugin.api.rest.*;
import io.undertow.server.*;
import okhttp3.*;
import org.slf4j.*;

import static io.bokun.inventory.plugin.api.rest.PluginCapability.*;
import static io.undertow.util.Headers.*;
import static java.util.concurrent.TimeUnit.*;

/**
 * The actual Inventory Service API implementation.
 *
 * @author Mindaugas Žakšauskas
 */
public class SampleRestPlugin {

    private static final Logger log = LoggerFactory.getLogger(SampleRestPlugin.class);

    /**
     * Default OkHttp read timeout: how long to wait (in seconds) for the backend to respond to requests.
     */
    private static final long DEFAULT_READ_TIMEOUT = 30L;

    private final OkHttpClient client;

    @Inject
    public SampleRestPlugin() {
        this.client = new OkHttpClient.Builder()
                .readTimeout(DEFAULT_READ_TIMEOUT, SECONDS)
                .build();
    }

    // helper method to express string as required string parameter structure, required by the REST API
    private PluginConfigurationParameter asRequiredStringParameter(String name) {
        var param = new PluginConfigurationParameter();
        param.setName(name);
        param.setType(PluginParameterDataType.STRING);
        param.setRequired(true);
        return param;
    }

    // helper method to express string as required long parameter structure, required by the REST API
    private PluginConfigurationParameter asRequiredLongParameter(String name) {
        var param = new PluginConfigurationParameter();
        param.setName(name);
        param.setType(PluginParameterDataType.LONG);
        param.setRequired(true);
        return param;
    }

    /**
     * Responds to <tt>/plugin/definition</tt> by sending back simple plugin definition JSON object.
     */
    public void getDefinition(@Nonnull HttpServerExchange exchange) {
        var definition = new PluginDefinition();
        definition.setName("Sample REST plugin");
        definition.setDescription("Provides availability and accepts bookings into <YourCompany> booking system. Uses REST protocol");

        definition.getCapabilities().add(AVAILABILITY);

        // below entry should be commented out if the plugin only supports reservation & confirmation as a single step
        definition.getCapabilities().add(RESERVATIONS);

        definition.getCapabilities().add(RESERVATION_CANCELLATION);
        definition.getCapabilities().add(AMENDMENT);

        // reuse parameter names from grpc
        definition.getParameters().add(asRequiredStringParameter(Configuration.SAMPLE_API_SCHEME));    // e.g. https
        definition.getParameters().add(asRequiredStringParameter(Configuration.SAMPLE_API_HOST));      // e.g. your-api.your-company.com
        definition.getParameters().add(asRequiredLongParameter(Configuration.SAMPLE_API_PORT));        // e.g. 443
        definition.getParameters().add(asRequiredStringParameter(Configuration.SAMPLE_API_PATH));      // e.g. /api/1
        definition.getParameters().add(asRequiredStringParameter(Configuration.SAMPLE_API_USERNAME));
        definition.getParameters().add(asRequiredStringParameter(Configuration.SAMPLE_API_PASSWORD));

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new Gson().toJson(definition));
    }

    /**
     * This method should list all your products
     */
    public void searchProducts(@Nonnull HttpServerExchange exchange) {
        var request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), SearchProductRequest.class);
        var configuration = Configuration.fromRestParameters(request.getParameters());

        // At this point you might want to call your external system to do the actual search and return data back.
        // Code below just provides some mocks.

        // Do something with httpResponseBody, e.g. convert this JSON into POJO and convert that POJO into BasicProductInfo
        // Don't forget to filter them by country and city, based on request parameters.
        var basicProductInfo = new BasicProductInfo();     // you will likely want to run this in a loop, to return multiple products
        basicProductInfo.setId("123");
        basicProductInfo.setName("Mock product");
        basicProductInfo.setDescription("Mock product description");
        basicProductInfo.setPricingCategories(new ArrayList<>());
        {
            var adult = new PricingCategory();
            adult.setId("ADT");         // can be any code as long as it is unique per pricing category. This will also connect with other calls
            adult.setLabel("Adult");
            basicProductInfo.getPricingCategories().add(adult);
        }
        {
            var child = new PricingCategory();
            child.setId("CHD");
            child.setLabel("Child");
            basicProductInfo.getPricingCategories().add(child);
        }

        basicProductInfo.setCities(ImmutableList.of("London"));
        basicProductInfo.setCountries(ImmutableList.of("GB"));

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new Gson().toJson(Lists.newArrayList(basicProductInfo)));
    }

    /**
     * Return detailed information about one particular product by given ID.
     */
    public void getProductById(HttpServerExchange exchange) {
        var request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), GetProductByIdRequest.class);
        var configuration = Configuration.fromRestParameters(request.getParameters());

        // similar to searchProducts except this should return a single product with a bit more information
        var description = new ProductDescription();
        description.setId("123");
        description.setName("Mock product");
        description.setDescription("Mock product description");

        description.setPricingCategories(new ArrayList<>());
        {
            var adult = new PricingCategory();
            adult.setId("ADT");         // can be any code as long as it is unique per pricing category. This will also connect with other calls
            adult.setLabel("Adult");
            description.getPricingCategories().add(adult);
        }
        {
            var child = new PricingCategory();
            child.setId("CHD");
            child.setLabel("Child");
            description.getPricingCategories().add(child);
        }

        var rate = new Rate();
        rate.setId("standard");
        rate.setLabel("Standard");

        var rate2 = new Rate();
        rate2.setId("standard2");
        rate2.setLabel("Standard2");

        description.setRates(ImmutableList.of(rate, rate2));
        description.setBookingType(BookingType.DATE_AND_TIME);
        description.setProductCategory(ProductCategory.ACTIVITIES);
        description.setTicketSupport(
                ImmutableList.of(
                        TicketSupport.TICKET_PER_BOOKING
                )
        );
        description.setCities(ImmutableList.of("London"));
        description.setCountries(ImmutableList.of("GB"));

        var startTime = new Time();
        startTime.setHour(13);
        startTime.setMinute(0);
        description.setStartTimes(ImmutableList.of(startTime));

        {                                                                               // opening hours block
            var openingHoursMonday = new OpeningHoursWeekday();
            openingHoursMonday.setOpen24Hours(false);
            var mondayIntervalAM = new OpeningHoursTimeInterval();
            mondayIntervalAM.setOpenFrom("08:00");
            mondayIntervalAM.setOpenForHours(4);
            mondayIntervalAM.setOpenForMinutes(0);
            var mondayIntervalPM = new OpeningHoursTimeInterval();
            mondayIntervalPM.setOpenFrom("13:00");
            mondayIntervalPM.setOpenForHours(4);
            mondayIntervalPM.setOpenForMinutes(0);
            openingHoursMonday.setTimeIntervals(ImmutableList.of(mondayIntervalAM, mondayIntervalPM));
            var openingHours = new OpeningHours();
            openingHours.setMonday(openingHoursMonday);
            description.setAllYearOpeningHours(openingHours);
        }

        {                                                                               // extras block
            var extra1 = new Extra();
            extra1.setId("some-extra-id");
            extra1.setTitle("Some extra title");
            extra1.setDescription("Some extra description");
            extra1.setOptional(true);
            extra1.setMaxPerBooking(10);
            extra1.setLimitByPax(false);
            extra1.setIncreasesCapacity(false);

            var extra2 = new Extra();
            extra2.setId("some-extra-id2");
            extra2.setTitle("Some extra title2");
            extra2.setDescription("Some extra description2");
            extra2.setOptional(true);
            extra2.setMaxPerBooking(10);
            extra2.setLimitByPax(false);
            extra2.setIncreasesCapacity(false);
            description.setExtras(ImmutableList.of(extra1, extra2));
        }

        {                                                                               // questions block
            // questions asked once per booking; answers arrive in ReservationData::answers
            var hotel = new Question();
            hotel.setId("hotel");
            hotel.setType(QuestionType.TEXT);
            hotel.setLabel("Name of your hotel");

            var arrival = new Question();
            arrival.setId("arrival");
            arrival.setType(QuestionType.DATE_AND_TIME);
            arrival.setLabel("When do you arrive in London?");

            var language = new Question();
            language.setId("language");
            language.setType(QuestionType.OPTIONS);
            language.setLabel("Preferred tour language");
            language.setRequired(true);
            language.setOptions(ImmutableList.of("English", "Spanish", "German"));

            description.setQuestionsPerBooking(ImmutableList.of(hotel, arrival, language));

            // questions asked for each passenger; answers arrive in Passenger::answers
            var age = new Question();
            age.setId("age");
            age.setType(QuestionType.INT);
            age.setLabel("Age");
            age.setRequired(true);

            var dateOfBirth = new Question();
            dateOfBirth.setId("dob");
            dateOfBirth.setType(QuestionType.DATE);
            dateOfBirth.setLabel("Date of birth");

            var weight = new Question();
            weight.setId("weight");
            weight.setType(QuestionType.DOUBLE);
            weight.setLabel("Weight (kg)");

            var wheelchair = new Question();
            wheelchair.setId("wheelchair");
            wheelchair.setType(QuestionType.BOOLEAN);
            wheelchair.setLabel("Do you need wheelchair access?");

            description.setQuestionsPerPassenger(ImmutableList.of(age, dateOfBirth, weight, wheelchair));
        }

        description.setTicketType(TicketType.QR_CODE);
        description.setMeetingType(MeetingType.MEET_ON_LOCATION);
        description.setDropoffAvailable(false);

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new Gson().toJson(description));
    }

    /**
     * Get availability of a particular product over a date range. This should provide details on precise dates/times as well as capacity for
     * each date.
     */
    public void getProductAvailability(HttpServerExchange exchange) {
        log.trace("In ::getProductAvailability");

        var request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), ProductAvailabilityRequest.class);
        var configuration = Configuration.fromRestParameters(request.getParameters());
        log.trace("Seller (vendor id) asking for availability: {}", request.getSellerId());

        // At this point you might want to call your external system to do the actual search and return data back.
        // Code below just provides some mocks.

        var l = new ArrayList<ProductAvailabilityWithRatesResponse>();
        for (int i=0; i<=1; i++) {
            var response = new ProductAvailabilityWithRatesResponse();
            response.setCapacity(10);

            var rate = new RateWithPrice();
            LocalDate date;
            if (i % 2 == 0) {
                date = LocalDate.now().plusDays(1);
                rate.setRateId("standard");
            } else {
                date = LocalDate.now().plusDays(2);
                rate.setRateId("standard2");
            }

            var dateYMD = new DateYMD();
            dateYMD.setYear(date.getYear());
            dateYMD.setMonth(date.getMonthValue());
            dateYMD.setDay(date.getDayOfMonth());
            response.setDate(dateYMD);

            var time = new Time();
            time.setHour(13);
            time.setMinute(00);
            response.setTime(time);

            var pricePerPerson = new PricePerPerson();
            pricePerPerson.setPricingCategoryWithPrice(new ArrayList<>());
            {
                var adultCategoryPrice = new PricingCategoryWithPrice();
                adultCategoryPrice.setPricingCategoryId("ADT");
                var adultPrice = new Price();
                adultPrice.setAmount("100");
                adultPrice.setCurrency("EUR");
                adultCategoryPrice.setPrice(adultPrice);
                pricePerPerson.getPricingCategoryWithPrice().add(adultCategoryPrice);
            }
            {
                var childCategoryPrice = new PricingCategoryWithPrice();
                childCategoryPrice.setPricingCategoryId("CHD");
                var childPrice = new Price();
                childPrice.setAmount("10");
                childPrice.setCurrency("EUR");
                childCategoryPrice.setPrice(childPrice);
                pricePerPerson.getPricingCategoryWithPrice().add(childCategoryPrice);
            }
            rate.setPricePerPerson(pricePerPerson);
            response.setRates(ImmutableList.of(rate));
            l.add(response);
        }

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new Gson().toJson(l));
        log.trace("Out ::getProductAvailability");
    }

    /**
     * This call secures necessary resource(s), such as activity time slot which can later become a booking. The reservation should be held for some
     * limited time, and reverted back to being available if the booking is not confirmed.
     *
     * Only implement this method if {@link PluginCapability#RESERVATIONS} is among capabilities of your {@link PluginDefinition}.
     * Otherwise you are only required to implement {@link #createAndConfirmBooking(HttpServerExchange)} which does both
     * reservation and confirmation, this method can be left empty or non-overridden.
     */
    public void createReservation(HttpServerExchange exchange) {
        // body of this method can be left empty if reserve & confirm is only supported as a single step
        log.trace("In ::createReservation");

        var request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), ReservationRequest.class);
        var configuration = Configuration.fromRestParameters(request.getParameters());

        // At this point you might want to call your external system to do the actual reservation and return data back.
        // Code below just provides some mocks.

        processAnswers(request.getReservationData());

        var response = new ReservationResponse();
        var reservation = new SuccessfulReservation();
        reservation.setReservationConfirmationCode(UUID.randomUUID().toString());
        response.setSuccessfulReservation(reservation);

//        FailedReservation fr = new FailedReservation();
//        fr.setReservationError("bla bla");
//        response.setFailedReservation(fr);

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new Gson().toJson(response));
        log.trace("Out ::createReservation");
    }

    /**
     * This call cancels existing reservation -- if the booking was not yet confirmed.
     *
     * Only implement this method if {@link PluginCapability#RESERVATIONS} and {@link PluginCapability#RESERVATION_CANCELLATION} are among
     * capabilities of your {@link PluginDefinition}.
     */
    public void cancelReservation(HttpServerExchange exchange) {
        log.trace("In ::cancelReservation");

        // At this point you might want to call your external system to do the actual reservation and return data back.
        // Code below just provides some mocks.

        var response = new CancelReservationResponse();
        var greatSuccess = new SuccessfulReservationCancellation();
        response.setSuccessfulReservationCancellation(greatSuccess);

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new Gson().toJson(response));
        log.trace("Out ::cancelReservation");
    }

    /**
     * Once reserved, proceed with booking. This will be called in case if reservation has succeeded.
     *
     * Only implement this method if {@link PluginCapability#RESERVATIONS} is among capabilities of your {@link PluginDefinition}.
     * Otherwise you are only required to implement {@link #createAndConfirmBooking(HttpServerExchange)} which does both
     * reservation and confirmation, this method can be left empty or non-overridden.
     */
    public void confirmBooking(HttpServerExchange exchange) {
        // body of this method can be left empty if reserve & confirm is only supported as a single step
        log.trace("In ::confirmBooking");

        var request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), ConfirmBookingRequest.class);
        var configuration = Configuration.fromRestParameters(request.getParameters());

        // At this point you might want to call your external system to do the actual confirmation and return data back.
        // Code below just provides some mocks.

        processBookingSourceInfo(request.getReservationData().getBookingSource());
        processAnswers(request.getReservationData());
        var confirmationCode = UUID.randomUUID().toString();

        var response = new ConfirmBookingResponse();
        var successfulBooking = new SuccessfulBooking();
        successfulBooking.setBookingConfirmationCode(confirmationCode);
        var ticket = new Ticket();
        var qrTicket = new QrTicket();
        qrTicket.setTicketBarcode(confirmationCode + "_ticket");
        ticket.setQrTicket(qrTicket);
        successfulBooking.setBookingTicket(ticket);
        response.setSuccessfulBooking(successfulBooking);
        
        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new Gson().toJson(response));
        log.trace("Out ::confirmBooking");
    }

    public void amendBooking(HttpServerExchange exchange) {
        log.trace("In ::amendBooking");

        var request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), AmendBookingRequest.class);
        var configuration = Configuration.fromRestParameters(request.getParameters());

        // At this point you might want to call your external system to do the actual amendment and return data back.
        // Code below just provides some mocks.

        processBookingSourceInfo(request.getReservationData().getBookingSource());
        processAnswers(request.getReservationData());

        var response = new AmendBookingResponse();
        var successfulAmendment = new SuccessfulAmendment();
        var ticket = new Ticket();
        var qrTicket = new QrTicket();
        var ticketBarcode = request.getBookingConfirmationCode() + "_ticket_amended";
        qrTicket.setTicketBarcode(ticketBarcode);
        ticket.setQrTicket(qrTicket);
        successfulAmendment.setBookingTicket(ticket);
        successfulAmendment.setAmendmentConfirmationCode(ticketBarcode);
        response.setSuccessfulAmendment(successfulAmendment);

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new Gson().toJson(response));
        log.trace("Out ::amendBooking");
    }

    /**
     * Example code to get info about the booking initiator.
     * Here you can see which data is available in each bookingSource.getSegment() case
     * @param bookingSource bookinSource data structure that is provided in booking requests
     */
    void processBookingSourceInfo(BookingSource bookingSource) {
        log.trace("Sales segment: {}",
                bookingSource.getSegment().name());
        log.trace("Booking channel: {} '{}'", bookingSource.getBookingChannel().getId(), bookingSource.getBookingChannel().getTitle());
        switch (bookingSource.getSegment()) {
            case OTA:
                log.trace("OTA system: {}", bookingSource.getBookingChannel().getSystemType());
                break;
            case MARKETPLACE:
                log.trace(
                        "Reseller vendor: {} '{}' reg.no. {}",
                        bookingSource.getMarketplaceVendor().getId(),
                        bookingSource.getMarketplaceVendor().getTitle(),
                        bookingSource.getMarketplaceVendor().getCompanyRegistrationNumber()
                );
                break;
            case AGENT_AREA:
                log.trace(
                        "Booking agent: {} '{}' reg.no. {}",
                        bookingSource.getBookingAgent().getId(),
                        bookingSource.getBookingAgent().getTitle(),
                        bookingSource.getBookingAgent().getCompanyRegistrationNumber()
                );
                break;
            case DIRECT_OFFLINE:
                log.trace(
                        "Extranet user: {} '{}'",
                        bookingSource.getExtranetUser().getEmail(),
                        bookingSource.getExtranetUser().getFullName()
                );
                break;
        }
    }

    /**
     * Example code to read answers to the questions of the product (see ProductDescription::questionsPerBooking and ::questionsPerPassenger in
     * {@link #getProductById(HttpServerExchange)}). Question ids are the ones returned by this plugin.
     * Only the questions which the vendor mapped to Bokun questions are answered, and an optional question might be left unanswered: answers
     * which are missing are simply not sent. If there are no answers at all, the answer lists are not sent either (null).
     * @param reservationData reservation data structure that is provided in reservation and booking requests
     */
    void processAnswers(ReservationData reservationData) {
        for (var answer : nullToEmpty(reservationData.getAnswers())) {
            log.trace("Booking answer: {} = {}", answer.getQuestionId(), answerToString(answer));
        }
        for (var reservation : nullToEmpty(reservationData.getReservations())) {
            for (var passenger : nullToEmpty(reservation.getPassengers())) {
                for (var answer : nullToEmpty(passenger.getAnswers())) {
                    log.trace("Passenger ({} {}) answer: {} = {}", passenger.getContact().getFirstName(), passenger.getContact().getLastName(),
                            answer.getQuestionId(), answerToString(answer));
                }
            }
        }
    }

    private static <T> List<T> nullToEmpty(@Nullable List<T> list) {
        return list != null ? list : List.of();
    }

    /**
     * Exactly one of the *Answer fields is set, depending on the type of the question.
     */
    private static String answerToString(Answer answer) {
        if (answer.getTextAnswer() != null) {                           // TEXT
            return answer.getTextAnswer();
        }
        if (answer.getIntAnswer() != null) {                            // INT
            return String.valueOf(answer.getIntAnswer());
        }
        if (answer.getDoubleAnswer() != null) {                         // DOUBLE
            return String.valueOf(answer.getDoubleAnswer());
        }
        if (answer.getBooleanAnswer() != null) {                        // BOOLEAN
            return String.valueOf(answer.getBooleanAnswer());
        }
        if (answer.getDateAnswer() != null) {                           // DATE
            return toLocalDate(answer.getDateAnswer()).toString();
        }
        if (answer.getDateAndTimeAnswer() != null) {                    // DATE_AND_TIME
            var dateAndTime = answer.getDateAndTimeAnswer();
            return LocalDateTime.of(
                    toLocalDate(dateAndTime.getDate()),
                    LocalTime.of(dateAndTime.getTime().getHour(), dateAndTime.getTime().getMinute())
            ).toString();
        }
        if (answer.getOptionsAnswers() != null) {                       // OPTIONS
            return String.join(", ", answer.getOptionsAnswers());
        }
        return "";
    }

    private static LocalDate toLocalDate(DateYMD date) {
        return LocalDate.of(date.getYear(), date.getMonth(), date.getDay());
    }

    /**
     * Only implement this method if {@link PluginCapability#RESERVATIONS} is <b>NOT</b> among capabilities of your {@link PluginDefinition}.
     * Otherwise you are only required to implement both {@link #createReservation(HttpServerExchange)} and {@link
     * #confirmBooking(HttpServerExchange)} separately; this method should remain empty or non-overridden.
     */
    public void createAndConfirmBooking(HttpServerExchange exchange) {
        log.trace("In ::createAndConfirmBooking");          // should never happen
//        throw new UnsupportedOperationException();

        var request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), CreateConfirmBookingRequest.class);
        var configuration = Configuration.fromRestParameters(request.getParameters());

        // At this point you might want to call your external system to do the actual reserve&confirm and return data back.
        // Code below just provides some mocks.

        processBookingSourceInfo(request.getReservationData().getBookingSource());
        processAnswers(request.getReservationData());
        var confirmationCode = UUID.randomUUID().toString();

        var response = new ConfirmBookingResponse();
        var successfulBooking = new SuccessfulBooking();
        successfulBooking.setBookingConfirmationCode(confirmationCode);
        var ticket = new Ticket();
        var qrTicket = new QrTicket();
        qrTicket.setTicketBarcode(confirmationCode + "_ticket");
        ticket.setQrTicket(qrTicket);
        successfulBooking.setBookingTicket(ticket);
        response.setSuccessfulBooking(successfulBooking);

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new Gson().toJson(response));
        log.trace("Out ::createAndConfirmBooking");
    }

    /**
     * Once booked, a booking may be cancelled using booking ref number.
     * If your system does not support booking cancellation, one of the current workarounds is to create a cancellation policy (on the Bokun end)
     * which offers no refund. Then a cancellation does not have any monetary effect.
     */
    public void cancelBooking(HttpServerExchange exchange) {
        log.trace("In ::cancelBooking");

        var request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), CancelBookingRequest.class);
        var configuration = Configuration.fromRestParameters(request.getParameters());

        // At this point you might want to call your external system to do the actual booking cancellation and return data back.
        // Code below just provides some mocks.

        var response = new CancelBookingResponse();
        response.setSuccessfulCancellation(new SuccessfulCancellation());
//        FailedCancellation failedCancellation = new FailedCancellation();
//        failedCancellation.cancellationError("asdfgasdfadf");
//        response.setFailedCancellation(failedCancellation);

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new Gson().toJson(response));
        log.trace("Out ::cancelBooking");
    }
}
