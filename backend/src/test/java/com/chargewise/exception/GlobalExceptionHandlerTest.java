package com.chargewise.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chargewise.service.demo.DatasetValidationException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The D8 error body for every failure class, without starting Spring. */
class GlobalExceptionHandlerTest {

    record TimedRequest(Instant requestedStart) {
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/dataset")
        void dataset() {
            throw new DatasetValidationException("stations.json: record S-03 has negative gridCapacityKw -1.00");
        }

        @GetMapping("/missing")
        void missing() {
            throw ApiException.notFound("Vehicle");
        }

        @GetMapping("/overlap")
        void overlap() {
            throw new DataIntegrityViolationException("could not execute statement",
                    new RuntimeException("violates exclusion constraint \"bookings_no_vehicle_overlap\""));
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @GetMapping("/number")
        int number(@RequestParam int value) {
            return value;
        }

        @PostMapping("/timed")
        Instant timed(@RequestBody TimedRequest request) {
            return request.requestedStart();
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void invalidDatasetGives400NamingTheRecord() throws Exception {
        mvc.perform(get("/dataset"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("stations.json: record S-03 has negative gridCapacityKw -1.00"))
                .andExpect(jsonPath("$.path").value("/dataset"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void apiExceptionKeepsItsStatusAndCode() throws Exception {
        mvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Vehicle not found"));
    }

    @Test
    void exclusionViolationGives409WithReadableMessage() throws Exception {
        mvc.perform(get("/overlap"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("The vehicle already has an active booking overlapping this window"));
    }

    @Test
    void unexpectedExceptionNeverLeaksDetails() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    void badParametersNameTheField() throws Exception {
        mvc.perform(get("/number").param("value", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("value"));
        mvc.perform(get("/number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("value"));
    }

    @Test
    void zoneLessTimestampNamesTheField() throws Exception {
        mvc.perform(post("/timed").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedStart\":\"2026-11-05T10:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("requestedStart"));
    }

    @Test
    void wrongMethodAndUnknownRouteUseTheSameBody() throws Exception {
        mvc.perform(post("/missing"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
