package ar.edu.iua.iw3.ordenes.controllers.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Entrada de POST .../alarmas/{id}/aceptacion (TP29). observacion es el
 * unico campo opcional (ver docs/contrato-f6-alarmas-configuracion.md);
 * usuario y fecha/hora los fija el servidor (actor del contexto de
 * seguridad, LocalDateTime.now), nunca el body.
 */
@Getter
@Setter
public class AceptacionAlarmaRequest {
	@Size(max = 500)
	private String observacion;
}
