package io.bokun.inventory.plugin.sample;

import io.bokun.inventory.plugin.api.rest.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MockHelper {


    public static RateWithPrice generateRate(String str) {
        RateWithPrice rate = new RateWithPrice();
        rate.setRateId(str);

        PricePerPerson pricePerPerson = new PricePerPerson();
        pricePerPerson.setPricingCategoryWithPrice(new ArrayList<>());
        {
            PricingCategoryWithPrice adultCategoryPrice = new PricingCategoryWithPrice();
            adultCategoryPrice.setPricingCategoryId("ADT");
            Price adultPrice = new Price();
            adultPrice.setAmount("100");
            adultPrice.setCurrency("EUR");
            adultCategoryPrice.setPrice(adultPrice);
            pricePerPerson.getPricingCategoryWithPrice().add(adultCategoryPrice);
        }
        {
            PricingCategoryWithPrice childCategoryPrice = new PricingCategoryWithPrice();
            childCategoryPrice.setPricingCategoryId("CHD");
            Price childPrice = new Price();
            childPrice.setAmount("10");
            childPrice.setCurrency("EUR");
            childCategoryPrice.setPrice(childPrice);
            pricePerPerson.getPricingCategoryWithPrice().add(childCategoryPrice);
        }
        rate.setPricePerPerson(pricePerPerson);
        return  rate;
    }


    public static RateWithPrice generateRate1() {
        RateWithPrice rate = new RateWithPrice();
        rate.setRateId("r1");

        PricePerPerson pricePerPerson = new PricePerPerson();
        pricePerPerson.setPricingCategoryWithPrice(new ArrayList<>());
        {
            PricingCategoryWithPrice adultCategoryPrice = new PricingCategoryWithPrice();
            adultCategoryPrice.setPricingCategoryId("ADT");
            Price adultPrice = new Price();
            adultPrice.setAmount("100");
            adultPrice.setCurrency("EUR");
            adultCategoryPrice.setPrice(adultPrice);
            pricePerPerson.getPricingCategoryWithPrice().add(adultCategoryPrice);
        }
        {
            PricingCategoryWithPrice childCategoryPrice = new PricingCategoryWithPrice();
            childCategoryPrice.setPricingCategoryId("CHD");
            Price childPrice = new Price();
            childPrice.setAmount("10");
            childPrice.setCurrency("EUR");
            childCategoryPrice.setPrice(childPrice);
            pricePerPerson.getPricingCategoryWithPrice().add(childCategoryPrice);
        }
        rate.setPricePerPerson(pricePerPerson);
        return  rate;
    }

    public static RateWithPrice generateRate2() {
        RateWithPrice rate = new RateWithPrice();
        rate.setRateId("r2");

        PricePerPerson pricePerPerson = new PricePerPerson();
        pricePerPerson.setPricingCategoryWithPrice(new ArrayList<>());
        {
            PricingCategoryWithPrice adultCategoryPrice = new PricingCategoryWithPrice();
            adultCategoryPrice.setPricingCategoryId("ADT");
            Price adultPrice = new Price();
            adultPrice.setAmount("100");
            adultPrice.setCurrency("EUR");
            adultCategoryPrice.setPrice(adultPrice);
            pricePerPerson.getPricingCategoryWithPrice().add(adultCategoryPrice);
        }
        {
            PricingCategoryWithPrice childCategoryPrice = new PricingCategoryWithPrice();
            childCategoryPrice.setPricingCategoryId("CHD");
            Price childPrice = new Price();
            childPrice.setAmount("10");
            childPrice.setCurrency("EUR");
            childCategoryPrice.setPrice(childPrice);
            pricePerPerson.getPricingCategoryWithPrice().add(childCategoryPrice);
        }
        rate.setPricePerPerson(pricePerPerson);
        return  rate;
    }

    public static RateWithPrice generateRate3() {
        RateWithPrice rate = new RateWithPrice();
        rate.setRateId("r3");

        PricePerPerson pricePerPerson = new PricePerPerson();
        pricePerPerson.setPricingCategoryWithPrice(new ArrayList<>());
        {
            PricingCategoryWithPrice adultCategoryPrice = new PricingCategoryWithPrice();
            adultCategoryPrice.setPricingCategoryId("ADT");
            Price adultPrice = new Price();
            adultPrice.setAmount("100");
            adultPrice.setCurrency("EUR");
            adultCategoryPrice.setPrice(adultPrice);
            pricePerPerson.getPricingCategoryWithPrice().add(adultCategoryPrice);
        }
        {
            PricingCategoryWithPrice childCategoryPrice = new PricingCategoryWithPrice();
            childCategoryPrice.setPricingCategoryId("CHD");
            Price childPrice = new Price();
            childPrice.setAmount("10");
            childPrice.setCurrency("EUR");
            childCategoryPrice.setPrice(childPrice);
            pricePerPerson.getPricingCategoryWithPrice().add(childCategoryPrice);
        }
        rate.setPricePerPerson(pricePerPerson);
        return  rate;
    }

    public static RateWithPrice generateRateStandard() {
        RateWithPrice rate = new RateWithPrice();
        rate.setRateId("standard");

        PricePerPerson pricePerPerson = new PricePerPerson();
        pricePerPerson.setPricingCategoryWithPrice(new ArrayList<>());
        {
            PricingCategoryWithPrice adultCategoryPrice = new PricingCategoryWithPrice();
            adultCategoryPrice.setPricingCategoryId("ADT");
            Price adultPrice = new Price();
            adultPrice.setAmount("100");
            adultPrice.setCurrency("EUR");
            adultCategoryPrice.setPrice(adultPrice);
            pricePerPerson.getPricingCategoryWithPrice().add(adultCategoryPrice);
        }
        {
            PricingCategoryWithPrice childCategoryPrice = new PricingCategoryWithPrice();
            childCategoryPrice.setPricingCategoryId("CHD");
            Price childPrice = new Price();
            childPrice.setAmount("10");
            childPrice.setCurrency("EUR");
            childCategoryPrice.setPrice(childPrice);
            pricePerPerson.getPricingCategoryWithPrice().add(childCategoryPrice);
        }
        rate.setPricePerPerson(pricePerPerson);
        return rate;

    }

    public static ProductAvailabilityWithRatesResponse generateAvailability(int hour, int minute, List<RateWithPrice> rates) {
        ProductAvailabilityWithRatesResponse response = new ProductAvailabilityWithRatesResponse();
        response.setCapacity(10);
        LocalDate tomorrow = LocalDate.now().plusDays(1L);
        DateYMD tomorrowDate = new DateYMD();
        tomorrowDate.setYear(tomorrow.getYear());
        tomorrowDate.setMonth(tomorrow.getMonthValue());
        tomorrowDate.setDay(tomorrow.getDayOfMonth());
        response.setDate(tomorrowDate);
        Time tomorrowTime = new Time();
        tomorrowTime.setHour(hour);
        tomorrowTime.setMinute(minute);
        response.setTime(tomorrowTime);
        response.setRates(rates);
        return response;
    }
}
