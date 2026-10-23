package ar.edu.iua.iw3.ordenes.controllers.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fila del listado de ordenes para monitoreo (TP31, F7). A proposito
 * NO incluye claveActivacion (nunca se expone, ver Orden.java) ni las
 * entidades completas de camion/chofer/cliente/producto - solo
 * patenteCamion (String), que es lo unico que la fuente pide mostrar
 * de esa relacion. El orden de los parametros del constructor importa:
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
 *
 * TP32/TP33 (F7, segunda unidad de tiempos): fechaInicioCarga y
 * fechaUltimaLectura se exponen como String ISO-8601 CON offset
 * explicito (ej. "2026-10-23T10:15:30-03:00"), nunca como un
 * LocalDateTime desnudo - el cliente no debe interpretarlas con la
 * zona del navegador. El offset usado es el de ZONA_RECEPCION, la
 * misma zona con la que OrdenBusiness genera estas fechas (debe
 * coincidir con OrdenBusiness.ZONA_RECEPCION; ver docs/decisiones.md
 * y docs/contrato-f7-tiempos-carga.md). No se modifica el tipo de
 * columna en Orden.java: la conversion es solo de presentacion.
 */
@Getter
@Setter
@NoArgsConstructor
public class OrdenResumenResponse {

	/** Debe coincidir con OrdenBusiness.ZONA_RECEPCION (unica zona del
	 * backend para fechas de Orden). Duplicada aca a proposito: esta
	 * clase es un DTO sin dependencia de la capa de negocio. */
	private static final ZoneId ZONA_RECEPCION = ZoneId.of("America/Argentina/Cordoba");

	private Integer orderNumber;
	private Integer estado;
	private String patenteCamion;
	private BigDecimal presetKg;
	private BigDecimal masaAcumuladaKg;
	private BigDecimal ultimaTemperaturaC;
	private BigDecimal ultimaDensidad;
	private BigDecimal ultimoCaudalKgH;
	private String fechaUltimaLectura;
	private String fechaInicioCarga;

	/**
	 * TP32 (F7): minutos restantes para alcanzar el preset, calculados
	 * por OrdenBusiness.calcularEtaMinutos sobre el ultimo snapshot de
	 * la orden. null = "no estimable" (sin masa/caudal o caudal <= 0).
	 * DECISION DEL EQUIPO: solo se completa para ordenes en estado 2
	 * (OrdenBusiness.listarOrdenes); en 1/3/4 queda null a proposito,
	 * para no mostrar una estimacion sin sentido fuera de la carga
	 * activa - ver docs/contrato-f7-tiempos-carga.md.
	 */
	private BigDecimal etaMinutos;

	/**
	 * Constructor usado por la proyeccion JPQL de OrdenBusiness.listarOrdenes.
	 * fechaUltimaLectura/fechaInicioCarga llegan como LocalDateTime (mismo
	 * tipo que la columna de Orden, requerido por "SELECT NEW ...(...)")
	 * y se convierten aca a String con offset explicito. etaMinutos no es
	 * parte de esta proyeccion: se completa despues, fila por fila, en
	 * OrdenBusiness (no se puede expresar su formula condicional dentro
	 * de un "SELECT NEW" JPQL).
	 */
	public OrdenResumenResponse(Integer orderNumber, Integer estado, String patenteCamion, BigDecimal presetKg,
			BigDecimal masaAcumuladaKg, BigDecimal ultimaTemperaturaC, BigDecimal ultimaDensidad,
			BigDecimal ultimoCaudalKgH, LocalDateTime fechaUltimaLectura, LocalDateTime fechaInicioCarga) {
		this.orderNumber = orderNumber;
		this.estado = estado;
		this.patenteCamion = patenteCamion;
		this.presetKg = presetKg;
		this.masaAcumuladaKg = masaAcumuladaKg;
		this.ultimaTemperaturaC = ultimaTemperaturaC;
		this.ultimaDensidad = ultimaDensidad;
		this.ultimoCaudalKgH = ultimoCaudalKgH;
		this.fechaUltimaLectura = conOffset(fechaUltimaLectura);
		this.fechaInicioCarga = conOffset(fechaInicioCarga);
	}

	private static String conOffset(LocalDateTime valor) {
		return valor == null ? null : valor.atZone(ZONA_RECEPCION).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}
}
