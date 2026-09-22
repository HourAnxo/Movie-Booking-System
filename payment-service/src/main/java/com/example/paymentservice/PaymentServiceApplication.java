package com.example.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// Scheduling drives BakongPaymentSweeper: a customer who pays and then
// closes the tab must still get a confirmed booking, and an unpaid QR must
// still release its seat when nobody is polling.
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

}
