package io.github.controlleraudit.sample;

import io.github.controlleraudit.annotation.Audit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** @Audit on the main class = audit every controller in the app. */
@SpringBootApplication
@Audit
public class SampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }
}
