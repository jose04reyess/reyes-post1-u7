package com.example.multas.infrastructure.pago;

import com.example.multas.domain.ResultadoPago;
import com.example.multas.domain.port.PasarelaPagoPort;
import com.example.multas.model.Multa;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Component
@ConditionalOnProperty(prefix = "app.pagos", name = "proveedor", havingValue = "pagosudes", matchIfMissing = true)
public class PagosUdesAdapter implements PasarelaPagoPort {

    private final RestTemplate restTemplate;
    private final String url;

    public PagosUdesAdapter(RestTemplate restTemplate, @Value("${app.pagos.pagosudes.url}") String url) {
        this.restTemplate = restTemplate;
        this.url = url;
    }

    @Override
    public ResultadoPago procesar(Multa multa) {
        PagosUdesRequest request = new PagosUdesRequest(
                multa.getId(),
                multa.getEstudianteId(),
                multa.getMonto(),
                multa.getConcepto()
        );

        try {
            PagosUdesResponse response = restTemplate.postForObject(url, request, PagosUdesResponse.class);

            if (response != null && isAprobada(response.estadoTransaccion())) {
                return new ResultadoPago(
                        "PAGOSUDES",
                        true,
                        response.idTransaccion(),
                        response.mensaje() != null ? response.mensaje() : "Transacción aprobada por PagosUDES"
                );
            }

            return new ResultadoPago(
                    "PAGOSUDES",
                    false,
                    response != null ? response.idTransaccion() : null,
                    response != null && response.mensaje() != null ? response.mensaje() : "Transacción no aprobada por PagosUDES"
            );
        } catch (RestClientException ex) {
            return new ResultadoPago(
                    "PAGOSUDES",
                    false,
                    null,
                    "Error de comunicación con pasarela PagosUDES: " + ex.getMessage()
            );
        }
    }

    private boolean isAprobada(String estado) {
        if (estado == null) return false;
        String e = estado.trim().toUpperCase();
        return e.equals("APROBADA") || e.equals("APROBADO") || e.equals("EXITOSA") || e.equals("EXITOSO");
    }

    public record PagosUdesRequest(Long idMulta, String estudianteId, BigDecimal monto, String concepto) {}

    public record PagosUdesResponse(String idTransaccion, String estadoTransaccion, String mensaje) {}
}
