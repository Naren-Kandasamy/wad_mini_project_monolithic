package com.example.shoppingcart.dev;

import com.example.shoppingcart.dev.controller.DevDiagnosticsController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DevDiagnosticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class DevDiagnosticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/dev/info returns 200 with runtime diagnostics")
    void getDevInfoReturnsDiagnostics() throws Exception {
        mockMvc.perform(get("/api/dev/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("shopping-cart-monolith"))
                .andExpect(jsonPath("$.status").value("DEBUG_READY"));
    }
}
