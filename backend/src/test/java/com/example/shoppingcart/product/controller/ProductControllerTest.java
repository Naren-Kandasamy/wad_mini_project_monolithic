package com.example.shoppingcart.product.controller;

import com.example.shoppingcart.product.dto.ProductResponse;
import com.example.shoppingcart.product.service.ProductService;
import com.example.shoppingcart.shared.error.GlobalExceptionHandler;
import com.example.shoppingcart.shared.error.ProductNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    @DisplayName("GET /api/products returns active products list")
    void listProducts_returnsOk() throws Exception {
        Instant now = Instant.now();
        ProductResponse item = new ProductResponse("p1", "Item", "Desc", new BigDecimal("10.00"), "SKU-1", true, now, now);
        when(productService.listActiveProducts()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("p1"))
            .andExpect(jsonPath("$[0].name").value("Item"));
    }

    @Test
    @DisplayName("GET /api/products/{id} returns 404 when product missing")
    void getProduct_returnsNotFound() throws Exception {
        when(productService.getProductById("missing")).thenThrow(new ProductNotFoundException("missing"));

        mockMvc.perform(get("/api/products/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /api/products creates product and returns 201")
    void createProduct_returnsCreated() throws Exception {
        Instant now = Instant.now();
        ProductResponse res = new ProductResponse("p-new", "Mouse", "Optical mouse", new BigDecimal("25.00"), "SKU-M", true, now, now);

        when(productService.createProduct(any())).thenReturn(res);

        String json = """
            {
                "name": "Mouse",
                "description": "Optical mouse",
                "price": 25.00,
                "sku": "SKU-M",
                "active": true
            }
            """;

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("p-new"));
    }

    @Test
    @DisplayName("DELETE /api/products/{id} deletes product and returns 204")
    void deleteProduct_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/products/p1"))
            .andExpect(status().isNoContent());

        verify(productService).deleteProduct("p1");
    }

    @Test
    @DisplayName("DELETE /api/products/{id} returns 404 when product missing")
    void deleteProduct_returnsNotFoundWhenMissing() throws Exception {
        doThrow(new ProductNotFoundException("missing")).when(productService).deleteProduct("missing");

        mockMvc.perform(delete("/api/products/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }
}
