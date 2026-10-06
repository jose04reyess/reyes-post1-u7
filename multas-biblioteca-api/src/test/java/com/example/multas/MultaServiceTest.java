package com.example.multas;

import com.example.multas.domain.PagoRechazadoException;
import com.example.multas.domain.ResultadoPago;
import com.example.multas.domain.port.PasarelaPagoPort;
import com.example.multas.model.*;
import com.example.multas.repository.MultaRepository;
import com.example.multas.service.MultaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultaServiceTest {

    @Mock
    private MultaRepository multaRepository;

    @Mock
    private PasarelaPagoPort pasarelaPagoPort;

    @InjectMocks
    private MultaService multaService;

    @Test
    @DisplayName("Punto 1: Cálculo de monto bajo el tope máximo (10 días x $500 = $5.000)")
    void testCalcularMonto_BajoTope() {
        BigDecimal monto = Multa.calcularMonto(10);
        assertEquals(new BigDecimal("5000"), monto);
    }

    @Test
    @DisplayName("Punto 1: Cálculo de monto sobre el tope máximo (40 días x $500 = $20.000 -> $15.000 tope)")
    void testCalcularMonto_SobreTopeMaximo() {
        BigDecimal monto = Multa.calcularMonto(40);
        assertEquals(new BigDecimal("15000"), monto);
    }

    @Test
    @DisplayName("Punto 1: Cálculo de monto con días menores o iguales a cero")
    void testCalcularMonto_DiasCeroONegativos() {
        assertEquals(BigDecimal.ZERO, Multa.calcularMonto(0));
        assertEquals(BigDecimal.ZERO, Multa.calcularMonto(-5));
    }

    @Test
    @DisplayName("Generación exitosa de multa cuando el estudiante tiene menos de 3 pendientes")
    void testGenerar_Exitoso() {
        when(multaRepository.countByEstudianteIdAndEstado("EST-001", EstadoMulta.PENDIENTE)).thenReturn(2L);
        when(multaRepository.save(any(Multa.class))).thenAnswer(invocation -> {
            Multa m = invocation.getArgument(0);
            m.setId(1L);
            return m;
        });

        Multa creada = multaService.generar("EST-001", "Libro de Cálculo I", 4);

        assertNotNull(creada);
        assertEquals(1L, creada.getId());
        assertEquals("EST-001", creada.getEstudianteId());
        assertEquals(new BigDecimal("2000"), creada.getMonto());
        assertEquals(EstadoMulta.PENDIENTE, creada.getEstado());
        verify(multaRepository, times(1)).countByEstudianteIdAndEstado("EST-001", EstadoMulta.PENDIENTE);
        verify(multaRepository, times(1)).save(any(Multa.class));
    }

    @Test
    @DisplayName("Punto 2: Bloqueo de generación al acumular 3 multas pendientes (Lanza LimiteMultasPendientesException)")
    void testGenerar_BloqueoPorLimiteTresMultasPendientes() {
        when(multaRepository.countByEstudianteIdAndEstado("EST-002", EstadoMulta.PENDIENTE)).thenReturn(3L);

        LimiteMultasPendientesException ex = assertThrows(
                LimiteMultasPendientesException.class,
                () -> multaService.generar("EST-002", "Estructuras de Datos", 5)
        );

        assertTrue(ex.getMessage().contains("límite máximo de 3 multas pendientes"));
        verify(multaRepository, never()).save(any(Multa.class));
    }

    @Test
    @DisplayName("Pago en ventanilla exitoso de una multa pendiente")
    void testPagarEnVentanilla_Exitoso() {
        Multa multa = new Multa("EST-001", "Física II", 2, new BigDecimal("1000"), EstadoMulta.PENDIENTE, LocalDate.now());
        multa.setId(10L);

        when(multaRepository.findById(10L)).thenReturn(Optional.of(multa));
        when(multaRepository.save(any(Multa.class))).thenAnswer(inv -> inv.getArgument(0));

        Multa pagada = multaService.pagarEnVentanilla(10L);

        assertEquals(EstadoMulta.PAGADA, pagada.getEstado());
        assertEquals("VENTANILLA", pagada.getMetodoPago());
        assertNotNull(pagada.getFechaPago());
        verify(multaRepository, times(1)).save(multa);
    }

    @Test
    @DisplayName("Intento de pago en ventanilla de multa ya pagada lanza MultaYaPagadaException")
    void testPagarEnVentanilla_YaPagada_LanzaExcepcion() {
        Multa multa = new Multa("EST-001", "Física II", 2, new BigDecimal("1000"), EstadoMulta.PAGADA, LocalDate.now().minusDays(2));
        multa.setId(10L);
        multa.setMetodoPago("VENTANILLA");

        when(multaRepository.findById(10L)).thenReturn(Optional.of(multa));

        assertThrows(MultaYaPagadaException.class, () -> multaService.pagarEnVentanilla(10L));
        verify(multaRepository, never()).save(any(Multa.class));
    }

    @Test
    @DisplayName("Pago en línea exitoso a través de PasarelaPagoPort")
    void testPagarConPasarela_Exitoso() {
        Multa multa = new Multa("EST-003", "Química Orgánica", 6, new BigDecimal("3000"), EstadoMulta.PENDIENTE, LocalDate.now());
        multa.setId(15L);

        ResultadoPago resultado = new ResultadoPago("PAGOSUDES", true, "TX-9988", "Aprobado");

        when(multaRepository.findById(15L)).thenReturn(Optional.of(multa));
        when(pasarelaPagoPort.procesar(multa)).thenReturn(resultado);
        when(multaRepository.save(any(Multa.class))).thenAnswer(inv -> inv.getArgument(0));

        Multa pagada = multaService.pagarConPasarela(15L);

        assertEquals(EstadoMulta.PAGADA, pagada.getEstado());
        assertEquals("PAGOSUDES", pagada.getMetodoPago());
        assertNotNull(pagada.getFechaPago());
        verify(pasarelaPagoPort, times(1)).procesar(multa);
        verify(multaRepository, times(1)).save(multa);
    }

    @Test
    @DisplayName("Pago en línea rechazado por pasarela lanza PagoRechazadoException")
    void testPagarConPasarela_Rechazado_LanzaPagoRechazadoException() {
        Multa multa = new Multa("EST-003", "Química Orgánica", 6, new BigDecimal("3000"), EstadoMulta.PENDIENTE, LocalDate.now());
        multa.setId(15L);

        ResultadoPago resultado = new ResultadoPago("WOMPI", false, "REF-1234", "Fondos insuficientes");

        when(multaRepository.findById(15L)).thenReturn(Optional.of(multa));
        when(pasarelaPagoPort.procesar(multa)).thenReturn(resultado);

        PagoRechazadoException ex = assertThrows(
                PagoRechazadoException.class,
                () -> multaService.pagarConPasarela(15L)
        );

        assertTrue(ex.getMessage().contains("Pago rechazado por WOMPI"));
        assertEquals(EstadoMulta.PENDIENTE, multa.getEstado());
        verify(multaRepository, never()).save(multa);
    }

    @Test
    @DisplayName("Búsqueda por ID no existente lanza MultaNotFoundException")
    void testBuscarPorId_NoExiste_LanzaException() {
        when(multaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(MultaNotFoundException.class, () -> multaService.buscarPorId(999L));
    }

    @Test
    @DisplayName("Listado de todas las multas y por estudiante")
    void testListados() {
        Multa m1 = new Multa("EST-001", "Libro 1", 1, new BigDecimal("500"), EstadoMulta.PENDIENTE, LocalDate.now());
        when(multaRepository.findAll()).thenReturn(List.of(m1));
        when(multaRepository.findByEstudianteId("EST-001")).thenReturn(List.of(m1));

        assertEquals(1, multaService.listarTodas().size());
        assertEquals(1, multaService.listarPorEstudiante("EST-001").size());
    }
}
