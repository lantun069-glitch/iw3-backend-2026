package ar.edu.iua.iw3.ordenes.business;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Resultado interno del calculo de conciliacion (TP16/TP18): no es un
 * DTO de controller (vive en la capa de negocio), para que
 * registrarPesajeFinal y consultarConciliacion compartan exactamente el
 * mismo calculo (OrdenBusiness.calcularConciliacion). ultimaTemperaturaC/
 * ultimaDensidad/ultimoCaudalKgH son los ULTIMOS VALORES del snapshot de
 * telemetria, no promedios. promedioTemperaturaC/promedioDensidad/
 * promedioCaudalKgH (TP19, F6) son el promedio aritmetico simple sobre
 * el detalle historico ALMACENADO de la orden (DetalleCarga) - no sobre
 * todas las lecturas aceptadas que llegaron, que puede ser un conjunto
 * mas grande si la orden tiene detalleIntervaloSegundos configurado
 * (TP14). null si la orden no tiene ninguna fila de detalle (no se
 * inventa un promedio en 0); en la practica esto no ocurre para una
 * orden que llego a estado 3/4 por el circuito normal (ver
 * OrdenBusiness.calcularConciliacion). DECISION DEL EQUIPO: promedio
 * no persistido, calculado en vivo cada vez; mismo criterio ya usado
 * para el resto de esta clase.
 */
@Getter
@AllArgsConstructor
public class Conciliacion {
	private final Integer orderNumber;
	private final BigDecimal taraKg;
	private final BigDecimal pesoBrutoKg;
	private final BigDecimal netoKg;
	private final BigDecimal productoCargadoKg;
	private final BigDecimal diferenciaKg;
	private final BigDecimal ultimaTemperaturaC;
	private final BigDecimal ultimaDensidad;
	private final BigDecimal ultimoCaudalKgH;
	private final BigDecimal promedioTemperaturaC;
	private final BigDecimal promedioDensidad;
	private final BigDecimal promedioCaudalKgH;
}
