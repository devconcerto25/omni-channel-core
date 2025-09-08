package com.concerto.omnichannel.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Random;

@Service
public class IdGenerationService {

    /**
     * Generate Reference ID: Random Alpha Numeric – 27, Julian Date (YDDD) – 4, Time (HHMM) – 4
     */
    public String generateRefId() {
        String randomAlphaNumeric = generateRandomAlphaNumeric(27);
        String julianDate = getJulianDate();
        String time = getCurrentTime();
        return randomAlphaNumeric + julianDate + time;
    }

    /**
     * Generate Message ID: Same format as Reference ID
     */
    public String generateMsgId() {
        return generateRefId(); // Same format
    }

    /**
     * Generate Transaction Reference ID: Customer BBPOU ID (4) + Julian Date (4) + Random (12)
     */
    public String generateTxnRefId() {
        String ouId = "OU01"; // Get from configuration
        String julianDate = getJulianDate();
        String random = generateRandomAlphaNumeric(12);
        return ouId + julianDate + random;
    }

    private String generateRandomAlphaNumeric(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder result = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            result.append(chars.charAt(random.nextInt(chars.length())));
        }
        return result.toString();
    }

    private String getJulianDate() {
        LocalDate now = LocalDate.now();
        int year = now.getYear() % 10; // Last digit of year
        int dayOfYear = now.getDayOfYear();
        return String.format("%d%03d", year, dayOfYear);
    }

    private String getCurrentTime() {
        LocalTime now = LocalTime.now();
        return String.format("%02d%02d", now.getHour(), now.getMinute());
    }
}
