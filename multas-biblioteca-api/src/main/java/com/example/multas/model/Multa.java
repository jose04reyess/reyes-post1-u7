package com.example.multas.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "multas")
public class Multa {

    public static final BigDecimal VALOR_POR_DIA = new BigDecimal("500");
    public static final BigDecimal TOPE_MAXIMO = new BigDecimal("15000");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El identificador del estudiante es obligatorio")
    @Column(name = "estudiante_id", nullable = false)
    private String estudianteId;

    @NotBlank(message = "El concepto de la multa es obligatorio")
    @Column(name = "concepto", nullable = false)
    private String concepto;

    @Min(value = 1, message = "Los días de atraso deben ser al menos 1")
    @Column(name = "dias_atraso", nullable = false)
    private int diasAtraso;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoMulta estado;

    @Column(name = "fecha_generacion", nullable = false)
    private LocalDate fechaGeneracion;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Column(name = "metodo_pago")
    private String metodoPago;

    public Multa() {
    }

    public Multa(String estudianteId, String concepto, int diasAtraso, BigDecimal monto, EstadoMulta estado, LocalDate fechaGeneracion) {
        this.estudianteId = estudianteId;
        this.concepto = concepto;
        this.diasAtraso = diasAtraso;
        this.monto = monto;
        this.estado = estado;
        this.fechaGeneracion = fechaGeneracion;
    }

    public Multa(Long id, String estudianteId, String concepto, int diasAtraso, BigDecimal monto, EstadoMulta estado, LocalDate fechaGeneracion, LocalDate fechaPago, String metodoPago) {
        this.id = id;
        this.estudianteId = estudianteId;
        this.concepto = concepto;
        this.diasAtraso = diasAtraso;
        this.monto = monto;
        this.estado = estado;
        this.fechaGeneracion = fechaGeneracion;
        this.fechaPago = fechaPago;
        this.metodoPago = metodoPago;
    }

    /**
     * Regla de negocio de dominio puro para calcular el monto de la multa.
     * Cobra $500 por cada día de atraso con un tope máximo fijado en $15.000.
     *
     * @param diasAtraso número de días de atraso en la devolución
     * @return monto calculado aplicando el tope máximo
     */
    public static BigDecimal calcularMonto(int diasAtraso) {
        if (diasAtraso <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal montoCalculado = VALOR_POR_DIA.multiply(BigDecimal.valueOf(diasAtraso));
        return montoCalculado.compareTo(TOPE_MAXIMO) > 0 ? TOPE_MAXIMO : montoCalculado;
    }

    /**
     * Marca la multa como pagada y actualiza la fecha y el método de pago.
     *
     * @param metodoPago identificador del método o pasarela de pago empleada
     * @throws MultaYaPagadaException si la multa ya se encontraba pagada previamente
     */
    public void marcarComoPagada(String metodoPago) {
        if (this.estado == EstadoMulta.PAGADA) {
            throw new MultaYaPagadaException("La multa con ID " + this.id + " ya se encuentra pagada.");
        }
        this.estado = EstadoMulta.PAGADA;
        this.fechaPago = LocalDate.now();
        this.metodoPago = metodoPago;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEstudianteId() {
        return estudianteId;
    }

    public void setEstudianteId(String estudianteId) {
        this.estudianteId = estudianteId;
    }

    public String getConcepto() {
        return concepto;
    }

    public void setConcepto(String concepto) {
        this.concepto = concepto;
    }

    public int getDiasAtraso() {
        return diasAtraso;
    }

    public void setDiasAtraso(int diasAtraso) {
        this.diasAtraso = diasAtraso;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public EstadoMulta getEstado() {
        return estado;
    }

    public void setEstado(EstadoMulta estado) {
        this.estado = estado;
    }

    public LocalDate getFechaGeneracion() {
        return fechaGeneracion;
    }

    public void setFechaGeneracion(LocalDate fechaGeneracion) {
        this.fechaGeneracion = fechaGeneracion;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public void setFechaPago(LocalDate fechaPago) {
        this.fechaPago = fechaPago;
    }

    public String getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(String metodoPago) {
        this.metodoPago = metodoPago;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Multa multa = (Multa) o;
        return Objects.equals(id, multa.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Multa{" +
                "id=" + id +
                ", estudianteId='" + estudianteId + '\'' +
                ", concepto='" + concepto + '\'' +
                ", diasAtraso=" + diasAtraso +
                ", monto=" + monto +
                ", estado=" + estado +
                ", fechaGeneracion=" + fechaGeneracion +
                ", fechaPago=" + fechaPago +
                ", metodoPago='" + metodoPago + '\'' +
                '}';
    }
}
