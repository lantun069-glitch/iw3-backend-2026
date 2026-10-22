package ar.edu.iua.iw3.ordenes.controllers.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionAlarmaResponse {
	private BigDecimal umbralTemperaturaC;
	private String destinatarios;
}
