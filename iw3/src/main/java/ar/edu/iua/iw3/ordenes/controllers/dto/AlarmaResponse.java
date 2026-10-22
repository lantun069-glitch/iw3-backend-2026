package ar.edu.iua.iw3.ordenes.controllers.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Respuesta de alarma (TP28/TP29, F6). temperaturaC/umbralAplicado son
 * los valores congelados al momento del disparo (ver Alarma.java), no
 * la configuracion global vigente ahora.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlarmaResponse {
	private Long id;
	private Integer orderNumber;
	private LocalDateTime fechaDisparo;
	private BigDecimal temperaturaC;
	private BigDecimal umbralAplicado;
	private boolean aceptada;
	private LocalDateTime fechaAceptacion;
	private String usuarioAceptacion;
	private String observacion;
}
