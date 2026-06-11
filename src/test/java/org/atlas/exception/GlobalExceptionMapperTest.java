package org.atlas.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Юніт-тести мапінгу винятків у {status, message, timestamp}. */
class GlobalExceptionMapperTest {

    private final GlobalExceptionMapper mapper = new GlobalExceptionMapper();

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(Response res) {
        return (Map<String, Object>) res.getEntity();
    }

    @Test
    void badRequest_mapsTo400() {
        Response res = mapper.toResponse(new BadRequestException("bad input"));
        assertEquals(400, res.getStatus());
        Map<String, Object> body = body(res);
        assertEquals(400, body.get("status"));
        assertEquals("bad input", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void notAuthorized_mapsTo401() {
        Response res = mapper.toResponse(new NotAuthorizedException("nope"));
        assertEquals(401, res.getStatus());
        assertEquals(401, body(res).get("status"));
    }

    @Test
    void notFound_mapsTo404() {
        Response res = mapper.toResponse(new NotFoundException("missing"));
        assertEquals(404, res.getStatus());
        assertEquals("missing", body(res).get("message"));
    }

    @Test
    void genericException_mapsTo500WithSafeMessage() {
        Response res = mapper.toResponse(new RuntimeException("boom details"));
        assertEquals(500, res.getStatus());
        Map<String, Object> body = body(res);
        assertEquals(500, body.get("status"));
        // внутрішні деталі не протікають клієнту
        assertEquals("Internal server error", body.get("message"));
        assertTrue(body.get("timestamp").toString().length() > 0);
    }
}
