package ar.edu.iua.iw3.ordenes.business;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Resultado interno de la agregacion sobre DetalleCarga para una orden
 * (TP19). Suma + cantidad, no AVG: el AVG de JPQL/Hibernate puede
 * devolver Double para columnas BigDecimal (perdida de precision);
 * SUM sobre una columna BigDecimal preserva BigDecimal (especificacion
 * JPA), y la division final se hace en OrdenBusiness con escala y
 * RoundingMode.HALF_UP explicitos por campo, redondeando solo el
 * resultado final (ver OrdenBusiness.calcularConciliacion).
 */
@Getter
@AllArgsConstructor
public class EstadisticasDetalle {
	private final BigDecimal sumaTemperaturaC;
	private final BigDecimal sumaDensidad;
	private final BigDecimal sumaCaudalKgH;
	private final Long cantidad;
}
