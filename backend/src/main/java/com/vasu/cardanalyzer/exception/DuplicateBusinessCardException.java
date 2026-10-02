package com.vasu.cardanalyzer.exception;

import java.util.List;

public class DuplicateBusinessCardException extends RuntimeException {

    private final String existingCardId;
    private final String existingName;
    private final String existingCompany;
    private final List<String> reasons;

    public DuplicateBusinessCardException(String existingCardId,
                                          String existingName,
                                          String existingCompany,
                                          List<String> reasons) {
        super(buildMessage(existingName, existingCompany, reasons));
        this.existingCardId = existingCardId;
        this.existingName = existingName;
        this.existingCompany = existingCompany;
        this.reasons = reasons;
    }

    public String getExistingCardId() {
        return existingCardId;
    }

    public String getExistingName() {
        return existingName;
    }

    public String getExistingCompany() {
        return existingCompany;
    }

    public List<String> getReasons() {
        return reasons;
    }

    private static String buildMessage(String existingName, String existingCompany, List<String> reasons) {
        String target = (existingName != null && !existingName.isBlank())
                ? existingName.trim()
                : "an existing saved card";
        if (existingCompany != null && !existingCompany.isBlank()) {
            target = target + " at " + existingCompany.trim();
        }

        String reasonText = reasons == null || reasons.isEmpty()
                ? "matching contact details"
                : String.join(", ", reasons);

        return "Duplicate card detected for " + target + " (" + reasonText + ").";
    }
}