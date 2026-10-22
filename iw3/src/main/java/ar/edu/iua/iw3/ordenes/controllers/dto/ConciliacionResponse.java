package ar.edu.iua.iw3.ordenes.controllers.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Respuesta de conciliacion (TP16-TP18), devuelta tanto por el pesaje
 * final como por su consulta posterior (mismo calculo, ver
 * OrdenBusiness.calcularConciliacion). ultimaTemperaturaC/ultimaDensidad/
 * ultimoCaudalKgH son los ULTIMOS VALORES del snapshot de telemetria,
 * deliberadamente NO llamados "promedio*". promedioTemperaturaC/
 * promedioDensidad/promedioCaudalKgH (TP19, F6) son el promedio
 * aritmetico simple sobre el detalle ALMACENADO (DetalleCarga) de la
 * orden - no sobre todas las lecturas aceptadas recibidas, que puede
 * ser un conjunto mas grande si la orden tiene detalleIntervaloSegundos
 * configurado (TP14). null si la orden no tiene ninguna fila de
 * detalle (no se devuelve 0).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConciliacionResponse {
	private Integer orderNumber;
	private BigDecimal taraKg;
	private BigDecimal pesoBrutoKg;
	private BigDecimal netoKg;
	private BigDecimal productoCargadoKg;
	private BigDecimal diferenciaKg;
	private BigDecimal ultimaTemperaturaC;
	private BigDecimal ultimaDensidad;
	private BigDecimal ultimoCaudalKgH;
	private BigDecimal promedioTemperaturaC;
	private BigDecimal promedioDensidad;
	private BigDecimal promedioCaudalKgH;
}
