package com.example.multas;

import com.example.multas.controller.GenerarMultaRequest;
import com.example.multas.controller.GlobalExceptionHandler;
import com.example.multas.controller.MultaController;
import com.example.multas.domain.PagoRechazadoException;
import com.example.multas.model.*;
import com.example.multas.service.MultaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MultaController.class)
@Import(GlobalExceptionHandler.class)
class MultaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MultaService multaService;

    @Test
    @DisplayName("GET /api/multas -> 200 OK con lista de multas")
    void testListarTodas_Retorna200() throws Exception {
        Multa m1 = new Multa("EST-001", "Libro Redes", 2, new BigDecimal("1000"), EstadoMulta.PENDIENTE, LocalDate.now());
        m1.setId(1L);

        when(multaService.listarTodas()).thenReturn(List.of(m1));

        mockMvc.perform(get("/api/multas"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].estudianteId", is("EST-001")))
                .andExpect(jsonPath("$[0].concepto", is("Libro Redes")));
    }

    @Test
    @DisplayName("GET /api/multas/{id} -> 200 OK cuando la multa existe")
    void testBuscarPorId_Existe_Retorna200() throws Exception {
        Multa m = new Multa("EST-001", "Libro IA", 3, new BigDecimal("1500"), EstadoMulta.PENDIENTE, LocalDate.now());
        m.setId(10L);

        when(multaService.buscarPorId(10L)).thenReturn(m);

        mockMvc.perform(get("/api/multas/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(10)))
                .andExpect(jsonPath("$.estudianteId", is("EST-001")))
                .andExpect(jsonPath("$.monto", is(1500)));
    }

    @Test
    @DisplayName("GET /api/multas/{id} -> 404 Not Found cuando la multa no existe")
    void testBuscarPorId_NoExiste_Retorna404() throws Exception {
        when(multaService.buscarPorId(999L)).thenThrow(new MultaNotFoundException("No se encontró la multa con ID: 999"));

        mockMvc.perform(get("/api/multas/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("No se encontró la multa con ID: 999")));
    }

    @Test
    @DisplayName("GET /api/multas/estudiante/{estudianteId} -> 200 OK")
    void testListarPorEstudiante_Retorna200() throws Exception {
        Multa m = new Multa("EST-005", "Base de Datos", 1, new BigDecimal("500"), EstadoMulta.PENDIENTE, LocalDate.now());
        m.setId(5L);

        when(multaService.listarPorEstudiante("EST-005")).thenReturn(List.of(m));

        mockMvc.perform(get("/api/multas/estudiante/EST-005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].estudianteId", is("EST-005")));
    }

    @Test
    @DisplayName("POST /api/multas -> 201 Created con cuerpo de multa generada")
    void testGenerarMulta_Valido_Retorna201() throws Exception {
        GenerarMultaRequest request = new GenerarMultaRequest("EST-001", "Sistemas Operativos", 4);
        Multa m = new Multa("EST-001", "Sistemas Operativos", 4, new BigDecimal("2000"), EstadoMulta.PENDIENTE, LocalDate.now());
        m.setId(20L);

        when(multaService.generar("EST-001", "Sistemas Operativos", 4)).thenReturn(m);

        mockMvc.perform(post("/api/multas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(20)))
                .andExpect(jsonPath("$.estudianteId", is("EST-001")))
                .andExpect(jsonPath("$.diasAtraso", is(4)))
                .andExpect(jsonPath("$.monto", is(2000)))
                .andExpect(jsonPath("$.estado", is("PENDIENTE")));
    }

    @Test
    @DisplayName("POST /api/multas -> 400 Bad Request cuando los datos de validación fallan")
    void testGenerarMulta_DatosInvalidos_Retorna400() throws Exception {
        GenerarMultaRequest requestInvalido = new GenerarMultaRequest("", "", 0);

        mockMvc.perform(post("/api/multas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.fieldErrors", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/multas -> 409 Conflict cuando el estudiante alcanza el límite de 3 multas pendientes")
    void testGenerarMulta_LimitePendientes_Retorna409() throws Exception {
        GenerarMultaRequest request = new GenerarMultaRequest("EST-LIMIT", "Algoritmos", 2);

        when(multaService.generar(anyString(), anyString(), anyInt()))
                .thenThrow(new LimiteMultasPendientesException("El estudiante EST-LIMIT ha alcanzado el límite máximo de 3 multas pendientes."));

        mockMvc.perform(post("/api/multas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("límite máximo de 3 multas pendientes")));
    }

    @Test
    @DisplayName("PATCH /api/multas/{id}/pagar -> 200 OK para pago en ventanilla")
    void testPagarEnVentanilla_Exitoso_Retorna200() throws Exception {
        Multa m = new Multa(1L, "EST-001", "Libro A", 2, new BigDecimal("1000"), EstadoMulta.PAGADA, LocalDate.now().minusDays(1), LocalDate.now(), "VENTANILLA");

        when(multaService.pagarEnVentanilla(1L)).thenReturn(m);

        mockMvc.perform(patch("/api/multas/1/pagar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.estado", is("PAGADA")))
                .andExpect(jsonPath("$.metodoPago", is("VENTANILLA")));
    }

    @Test
    @DisplayName("PATCH /api/multas/{id}/pagar -> 409 Conflict si la multa ya fue pagada")
    void testPagarEnVentanilla_YaPagada_Retorna409() throws Exception {
        when(multaService.pagarEnVentanilla(1L))
                .thenThrow(new MultaYaPagadaException("La multa con ID 1 ya se encuentra pagada."));

        mockMvc.perform(patch("/api/multas/1/pagar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("ya se encuentra pagada")));
    }

    @Test
    @DisplayName("POST /api/multas/{id}/pagar-en-linea -> 200 OK para pago en línea exitoso")
    void testPagarEnLinea_Exitoso_Retorna200() throws Exception {
        Multa m = new Multa(2L, "EST-002", "Libro B", 3, new BigDecimal("1500"), EstadoMulta.PAGADA, LocalDate.now().minusDays(2), LocalDate.now(), "PAGOSUDES");

        when(multaService.pagarConPasarela(2L)).thenReturn(m);

        mockMvc.perform(post("/api/multas/2/pagar-en-linea"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(2)))
                .andExpect(jsonPath("$.estado", is("PAGADA")))
                .andExpect(jsonPath("$.metodoPago", is("PAGOSUDES")));
    }

    @Test
    @DisplayName("POST /api/multas/{id}/pagar-en-linea -> 402 Payment Required si la pasarela rechaza el pago")
    void testPagarEnLinea_Rechazado_Retorna402() throws Exception {
        when(multaService.pagarConPasarela(eq(3L)))
                .thenThrow(new PagoRechazadoException("Pago rechazado por PAGOSUDES: Fondos insuficientes en la cuenta del estudiante"));

        mockMvc.perform(post("/api/multas/3/pagar-en-linea"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status", is(402)))
                .andExpect(jsonPath("$.error", is("Payment Required")))
                .andExpect(jsonPath("$.message", containsString("Pago rechazado por PAGOSUDES")));
    }
}
