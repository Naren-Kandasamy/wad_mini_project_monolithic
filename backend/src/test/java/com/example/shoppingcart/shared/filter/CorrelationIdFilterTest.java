package com.example.shoppingcart.shared.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    @Test
    @DisplayName("Propagates incoming X-Request-Id header to response and MDC during execution")
    void shouldPropagateIncomingCorrelationId() throws ServletException, IOException {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "custom-req-id-1234");
        MockHttpServletResponse response = new MockHttpServletResponse();

        final String[] mdcCapture = new String[1];
        FilterChain chain = (req, res) -> {
            mdcCapture[0] = MDC.get(CorrelationIdFilter.MDC_KEY);
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo("custom-req-id-1234");
        assertThat(mdcCapture[0]).isEqualTo("custom-req-id-1234");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull(); // Cleared after filter chain
    }

    @Test
    @DisplayName("Generates new UUID X-Request-Id if none provided")
    void shouldGenerateNewCorrelationIdWhenMissing() throws ServletException, IOException {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        final String[] mdcCapture = new String[1];
        FilterChain chain = (req, res) -> {
            mdcCapture[0] = MDC.get(CorrelationIdFilter.MDC_KEY);
        };

        filter.doFilterInternal(request, response, chain);

        String generatedHeader = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(generatedHeader).isNotBlank();
        assertThat(mdcCapture[0]).isEqualTo(generatedHeader);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
