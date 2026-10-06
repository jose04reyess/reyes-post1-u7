package com.example.multas;

import com.example.multas.domain.ResultadoPago;
import com.example.multas.domain.port.PasarelaPagoPort;
import com.example.multas.infrastructure.pago.PagosUdesAdapter;
import com.example.multas.infrastructure.pago.WompiAdapter;
import com.example.multas.model.EstadoMulta;
import com.example.multas.model.Multa;
import com.example.multas.repository.MultaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

class PasarelaPagoIntegrationTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
            "app.pagos.proveedor=pagosudes",
            "app.pagos.pagosudes.url=http://localhost:9001/pagosudes/transacciones"
    })
    @DisplayName("Pruebas de Integración con Adaptador Activo PagosUDES")
    class PagosUdesIntegrationTest {

        @Autowired
        private PasarelaPagoPort pasarelaPagoPort;

        @Autowired
        private RestTemplate restTemplate;

        @Autowired
        private MultaRepository multaRepository;

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        private MockRestServiceServer mockServer;

        @BeforeEach
        void setUp() {
            mockServer = MockRestServiceServer.createServer(restTemplate);
            multaRepository.deleteAll();
        }

        @Test
        @DisplayName("Punto 3: Inyección condicional activa PagosUdesAdapter según app.pagos.proveedor=pagosudes")
        void testAdaptadorActivoEsPagosUdes() {
            assertNotNull(pasarelaPagoPort);
            assertInstanceOf(PagosUdesAdapter.class, pasarelaPagoPort);
        }

        @Test
        @DisplayName("Punto 4: Procesamiento exitoso con PagosUDES devuelve ResultadoPago con referencia neutral")
        void testProcesarPagoPagosUdes_Exitoso() throws Exception {
            Multa multa = new Multa("EST-100", "Devolución tardía", 3, new BigDecimal("1500"), EstadoMulta.PENDIENTE, LocalDate.now());
            multa.setId(100L);

            PagosUdesAdapter.PagosUdesResponse udesResponse = new PagosUdesAdapter.PagosUdesResponse(
                    "TX-UDES-98765",
                    "APROBADA",
                    "Pago aprobado con éxito"
            );

            mockServer.expect(MockRestRequestMatchers.requestTo("http://localhost:9001/pagosudes/transacciones"))
                    .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                    .andRespond(MockRestResponseCreators.withSuccess(objectMapper.writeValueAsString(udesResponse), MediaType.APPLICATION_JSON));

            ResultadoPago resultado = pasarelaPagoPort.procesar(multa);

            mockServer.verify();
            assertTrue(resultado.exitoso());
            assertEquals("PAGOSUDES", resultado.proveedor());
            assertEquals("TX-UDES-98765", resultado.referenciaExterna());
        }

        @Test
        @DisplayName("Punto 4: PagosUDES rechaza el pago y flujo HTTP mapea a 402 Payment Required")
        void testFlujoCompleto_PagoRechazado_Retorna402() throws Exception {
            Multa multa = multaRepository.save(new Multa("EST-200", "Libro Física", 2, new BigDecimal("1000"), EstadoMulta.PENDIENTE, LocalDate.now()));

            PagosUdesAdapter.PagosUdesResponse udesResponse = new PagosUdesAdapter.PagosUdesResponse(
                    "TX-UDES-RECHAZO-01",
                    "RECHAZADA",
                    "Fondos insuficientes en la cuenta del estudiante"
            );

            mockServer.expect(MockRestRequestMatchers.requestTo("http://localhost:9001/pagosudes/transacciones"))
                    .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                    .andRespond(MockRestResponseCreators.withSuccess(objectMapper.writeValueAsString(udesResponse), MediaType.APPLICATION_JSON));

            mockMvc.perform(MockMvcRequestBuilders.post("/api/multas/" + multa.getId() + "/pagar-en-linea"))
                    .andExpect(MockMvcResultMatchers.status().isPaymentRequired())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.status", is(402)))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.error", is("Payment Required")))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.message", containsString("Fondos insuficientes")));

            mockServer.verify();

            // Verificar que la multa continúa PENDIENTE en base de datos
            Multa multaBd = multaRepository.findById(multa.getId()).orElseThrow();
            assertEquals(EstadoMulta.PENDIENTE, multaBd.getEstado());
            assertNull(multaBd.getFechaPago());
        }

        @Test
        @DisplayName("Error HTTP 500 en pasarela PagosUDES es traducido a ResultadoPago no exitoso y 402")
        void testErrorServidorPasarela_Retorna402() throws Exception {
            Multa multa = multaRepository.save(new Multa("EST-300", "Química", 1, new BigDecimal("500"), EstadoMulta.PENDIENTE, LocalDate.now()));

            mockServer.expect(MockRestRequestMatchers.requestTo("http://localhost:9001/pagosudes/transacciones"))
                    .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                    .andRespond(MockRestResponseCreators.withServerError());

            mockMvc.perform(MockMvcRequestBuilders.post("/api/multas/" + multa.getId() + "/pagar-en-linea"))
                    .andExpect(MockMvcResultMatchers.status().isPaymentRequired())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.status", is(402)))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.error", is("Payment Required")));

            mockServer.verify();
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
            "app.pagos.proveedor=wompi",
            "app.pagos.wompi.url=http://localhost:9002/wompi/transactions"
    })
    @DisplayName("Pruebas de Integración con Adaptador Activo Wompi")
    class WompiIntegrationTest {

        @Autowired
        private PasarelaPagoPort pasarelaPagoPort;

        @Autowired
        private RestTemplate restTemplate;

        @Autowired
        private MultaRepository multaRepository;

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        private MockRestServiceServer mockServer;

        @BeforeEach
        void setUp() {
            mockServer = MockRestServiceServer.createServer(restTemplate);
            multaRepository.deleteAll();
        }

        @Test
        @DisplayName("Punto 3: Inyección condicional activa WompiAdapter según app.pagos.proveedor=wompi")
        void testAdaptadorActivoEsWompi() {
            assertNotNull(pasarelaPagoPort);
            assertInstanceOf(WompiAdapter.class, pasarelaPagoPort);
        }

        @Test
        @DisplayName("Punto 4: Procesamiento exitoso con Wompi convierte monto a centavos y actualiza multa")
        void testFlujoCompletoWompi_Exitoso() throws Exception {
            Multa multa = multaRepository.save(new Multa("EST-400", "Matemáticas Discretas", 5, new BigDecimal("2500"), EstadoMulta.PENDIENTE, LocalDate.now()));

            WompiAdapter.WompiResponse wompiResponse = new WompiAdapter.WompiResponse(
                    "WOMPI-REF-778899",
                    "APPROVED",
                    "Aprobado por franquicia bancaria"
            );

            // Wompi recibe el monto en centavos: 2500 * 100 = 250000
            mockServer.expect(MockRestRequestMatchers.requestTo("http://localhost:9002/wompi/transactions"))
                    .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                    .andExpect(MockRestRequestMatchers.jsonPath("$.amountInCents", is(250000)))
                    .andExpect(MockRestRequestMatchers.jsonPath("$.currency", is("COP")))
                    .andRespond(MockRestResponseCreators.withSuccess(objectMapper.writeValueAsString(wompiResponse), MediaType.APPLICATION_JSON));

            mockMvc.perform(MockMvcRequestBuilders.post("/api/multas/" + multa.getId() + "/pagar-en-linea"))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.id", is(multa.getId().intValue())))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.estado", is("PAGADA")))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.metodoPago", is("WOMPI")));

            mockServer.verify();

            Multa multaBd = multaRepository.findById(multa.getId()).orElseThrow();
            assertEquals(EstadoMulta.PAGADA, multaBd.getEstado());
            assertEquals("WOMPI", multaBd.getMetodoPago());
            assertNotNull(multaBd.getFechaPago());
        }
    }
}
