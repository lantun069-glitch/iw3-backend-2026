package ar.edu.iua.iw3.ordenes.controllers.dto;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Entrada de PUT /api/v1/alarmas/configuracion (TP28, F6). umbralTemperaturaC:
 * misma precision que Orden.temperaturaC (DECIMAL(6,2)), @Digits valida que
 * sea representable sin truncarse en silencio. destinatarios: lista de
 * emails separados por coma; formato validado con isDestinatariosValido()
 * (sin librerias nuevas, una expresion simple es suficiente para esta
 * unidad).
 */
@Getter
@Setter
public class ConfiguracionAlarmaRequest {

	private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

	@NotNull
	@Digits(integer = 4, fraction = 2)
	private BigDecimal umbralTemperaturaC;

	@NotBlank
	@Size(max = 500)
	private String destinatarios;

	@AssertTrue(message = "destinatarios debe ser una lista de emails separados por coma, todos con formato valido")
	public boolean isDestinatariosValido() {
		if (destinatarios == null || destinatarios.isBlank()) {
			// @NotBlank ya reporta este caso con su propio mensaje; no
			// duplicar el error agregando tambien este.
			return true;
		}
		String[] partes = destinatarios.split(",");
		for (String parte : partes) {
			if (!EMAIL.matcher(parte.trim()).matches()) {
				return false;
			}
		}
		return true;
	}
}
