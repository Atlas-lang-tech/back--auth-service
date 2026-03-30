package org.atlas.exception;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.Instant;
import java.util.Map;
import org.jboss.logging.Logger;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

	private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

	@Override
	public Response toResponse(Exception exception) {
		if (exception instanceof BadRequestException e) {
			return error(400, e.getMessage());
		}
		if (exception instanceof NotAuthorizedException e) {
			return error(401, e.getMessage());
		}
		if (exception instanceof NotFoundException e) {
			return error(404, e.getMessage());
		}

		LOG.errorf("Unhandled exception: %s", exception.getMessage());
		return error(500, "Internal server error");
	}

	private Response error(int status, String message) {
		return Response.status(status)
			.entity(
				Map.of(
					"status",
					status,
					"message",
					message,
					"timestamp",
					Instant.now().toString()
				)
			)
			.build();
	}
}
