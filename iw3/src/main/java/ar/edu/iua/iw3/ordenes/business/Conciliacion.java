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
 * telemetria, no promedios (TP19, diferido a F6 - ver
 * docs/decisiones.md).
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
}
