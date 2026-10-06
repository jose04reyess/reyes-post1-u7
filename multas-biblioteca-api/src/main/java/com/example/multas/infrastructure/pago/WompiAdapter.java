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
@ConditionalOnProperty(prefix = "app.pagos", name = "proveedor", havingValue = "wompi")
public class WompiAdapter implements PasarelaPagoPort {

    private final RestTemplate restTemplate;
    private final String url;

    public WompiAdapter(RestTemplate restTemplate, @Value("${app.pagos.wompi.url}") String url) {
        this.restTemplate = restTemplate;
        this.url = url;
    }

    @Override
    public ResultadoPago procesar(Multa multa) {
        // Conversión de monto a centavos requerida por el estándar de Wompi
        long amountInCents = multa.getMonto() != null
                ? multa.getMonto().multiply(BigDecimal.valueOf(100)).longValue()
                : 0L;
        String reference = "WOMPI-MULTA-" + multa.getId() + "-" + System.currentTimeMillis();

        WompiRequest request = new WompiRequest(
                amountInCents,
                "COP",
                reference,
                multa.getEstudianteId() + "@mail.udes.edu.co"
        );

        try {
            WompiResponse response = restTemplate.postForObject(url, request, WompiResponse.class);

            if (response != null && "APPROVED".equalsIgnoreCase(response.status())) {
                return new ResultadoPago(
                        "WOMPI",
                        true,
                        response.reference() != null ? response.reference() : reference,
                        response.message() != null ? response.message() : "Transacción aprobada por Wompi"
                );
            }

            return new ResultadoPago(
                    "WOMPI",
                    false,
                    response != null ? response.reference() : reference,
                    response != null && response.message() != null ? response.message() : "Transacción no aprobada por Wompi"
            );
        } catch (RestClientException ex) {
            return new ResultadoPago(
                    "WOMPI",
                    false,
                    reference,
                    "Error de comunicación con pasarela Wompi: " + ex.getMessage()
            );
        }
    }

    public record WompiRequest(long amountInCents, String currency, String reference, String customerEmail) {}

    public record WompiResponse(String reference, String status, String message) {}
}
