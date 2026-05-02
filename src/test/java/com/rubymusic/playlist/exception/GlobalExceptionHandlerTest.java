package com.rubymusic.playlist.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

import java.util.NoSuchElementException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class ThrowingController {

        @GetMapping("/throw")
        public String throwIt(@RequestParam String type) {
            return switch (type) {
                case "notFound"    -> { throw new NoSuchElementException("not found"); }
                case "badRequest"  -> { throw new IllegalArgumentException("bad arg"); }
                case "badState"    -> { throw new IllegalStateException("bad state"); }
                case "conflict"    -> { throw new DataIntegrityViolationException("dup"); }
                case "unhandled"   -> { throw new RuntimeException("unexpected"); }
                default            -> "ok";
            };
        }

        @PostMapping("/validate")
        public String validate(@Valid @RequestBody Body body) {
            return body.value;
        }

        static class Body {
            @NotBlank(message = "value must not be blank")
            public String value;
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void noSuchElementException_returns404() throws Exception {
        mockMvc.perform(get("/throw").param("type", "notFound"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("not found"));
    }

    @Test
    void illegalArgumentException_returns400() throws Exception {
        mockMvc.perform(get("/throw").param("type", "badRequest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("bad arg"));
    }

    @Test
    void illegalStateException_returns400() throws Exception {
        mockMvc.perform(get("/throw").param("type", "badState"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("bad state"));
    }

    @Test
    void dataIntegrityViolation_returns409() throws Exception {
        mockMvc.perform(get("/throw").param("type", "conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Song already in playlist"));
    }

    @Test
    void unhandledException_returns500() throws Exception {
        mockMvc.perform(get("/throw").param("type", "unhandled"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void validationError_returns422_withFieldMessages() throws Exception {
        mockMvc.perform(post("/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("value must not be blank"));
    }
}
