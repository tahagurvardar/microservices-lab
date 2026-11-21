package com.example.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class PaymentControllerTest {
    @Test
    void badRateReturns400WithoutWriting() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var controller = new PaymentController(jdbc);
        var response = controller.pay(0, 101, "order-1");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void validKeyCreatesOnePayment() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(startsWith("INSERT INTO lab_payment"), eq("order-1"), anyString())).thenReturn(1);
        var controller = new PaymentController(jdbc);
        var response = controller.pay(0, 0, "order-1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(false, ((Map<?, ?>) response.getBody()).get("reused"));
        verify(jdbc).update(startsWith("UPDATE lab_payment"), eq("order-1"));
    }

    @Test
    void repeatedKeyReturnsExistingPayment() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(startsWith("INSERT INTO lab_payment"), eq("order-1"), anyString())).thenReturn(0);
        when(jdbc.queryForMap(anyString(), eq("order-1")))
                .thenReturn(Map.of("payment_id", "payment-1", "status", "COMPLETED"));
        var response = new PaymentController(jdbc).pay(0, 0, "order-1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, ((Map<?, ?>) response.getBody()).get("reused"));
        assertEquals("payment-1", ((Map<?, ?>) response.getBody()).get("paymentId"));
        verify(jdbc, never()).update(startsWith("UPDATE lab_payment"), anyString());
    }
}
