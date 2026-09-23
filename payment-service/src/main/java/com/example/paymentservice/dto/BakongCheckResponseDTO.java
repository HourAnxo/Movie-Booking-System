package com.example.paymentservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The envelope every Bakong Open API response comes in. The HTTP status is
 * 200 either way; responseCode 0 means success and 1 means an error named
 * by errorCode.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BakongCheckResponseDTO(

        Integer responseCode,

        String responseMessage,

        Integer errorCode,

        BakongTransactionDTO data

) {
}
