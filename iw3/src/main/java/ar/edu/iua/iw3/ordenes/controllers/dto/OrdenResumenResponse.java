package ar.edu.iua.iw3.ordenes.controllers.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fila del listado de ordenes para monitoreo (TP31, F7). A proposito
 * NO incluye claveActivacion (nunca se expone, ver Orden.java) ni las
 * entidades completas de camion/chofer/cliente/producto - solo
 * patenteCamion (String), que es lo unico que la fuente pide mostrar
 * de esa relacion. El orden de los campos de este constructor importa:
 * coincide exactamente con la proyeccion JPQL
 * "SELECT NEW ...OrdenResumenResponse(...)" armada dinamicamente en
 * OrdenBusiness.listarOrdenes (el ORDER BY es dinamico y validado
 * contra una lista explicita de campos, por eso no es una @Query
 * estatica del repositorio), para evitar una consulta adicional por
 * cada camion al armar la lista.
 *
 * Los campos de telemetria pueden ser null (orden todavia sin ninguna
 * lectura aceptada): el frontend los muestra como "Sin datos", nunca
 * como 0 (ver docs/contrato-f7-monitoreo-ordenes.md).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrdenResumenResponse {
	private Integer orderNumber;
	private Integer estado;
	private String patenteCamion;
	private BigDecimal presetKg;
	private BigDecimal masaAcumuladaKg;
	private BigDecimal ultimaTemperaturaC;
	private BigDecimal ultimaDensidad;
	private BigDecimal ultimoCaudalKgH;
	private LocalDateTime fechaUltimaLectura;
}
