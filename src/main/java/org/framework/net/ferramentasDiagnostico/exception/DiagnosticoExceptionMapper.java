package org.framework.net.ferramentasDiagnostico.exception;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.framework.net.telemetria.TelemetriaLogger;
import org.framework.net.telemetria.exception.TelemetriaExceptionSupport;

@Provider
public class DiagnosticoExceptionMapper implements ExceptionMapper<DiagnosticoException> {

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Context
    ContainerRequestContext requestContext;

    @Override
    public Response toResponse(DiagnosticoException exception) {
        TelemetriaExceptionSupport.registrar(
                telemetriaLogger, "diagnostico", "domain_exception", requestContext, exception);
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(exception.getMessage())
                .type(MediaType.TEXT_PLAIN)
                .build();
    }
}
