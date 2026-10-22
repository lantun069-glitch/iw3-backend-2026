package ar.edu.iua.iw3.ordenes.controllers;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iua.iw3.business.execption.BusinessException;
import ar.edu.iua.iw3.business.execption.NotFoundException;
import ar.edu.iua.iw3.controllers.BaseRestController;
import ar.edu.iua.iw3.controllers.Constants;
import ar.edu.iua.iw3.ordenes.business.IConfiguracionAlarmaBusiness;
import ar.edu.iua.iw3.ordenes.controllers.dto.ConfiguracionAlarmaRequest;
import ar.edu.iua.iw3.ordenes.controllers.dto.ConfiguracionAlarmaResponse;
import ar.edu.iua.iw3.ordenes.model.ConfiguracionAlarma;
import ar.edu.iua.iw3.util.IStandartResponseBusiness;
import ar.edu.iua.iw3.util.StandartResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Configuracion global de alarmas de temperatura (TP28, F6, primera
 * unidad). No es un sub-recurso de orden (una sola fila para todo el
 * sistema, ver ConfiguracionAlarma.java): por eso vive en un
 * controller propio, no dentro de OrdenRestController.
 *
 * Mismo criterio que OrdenRestController para los @ExceptionHandler
 * locales (no @ControllerAdvice global): resolver la excepcion aqui
 * evita el forward interno a /error, que no esta en la lista de rutas
 * publicas y volveria 401/400 incorrectos (ver docs/decisiones.md,
 * mismo hallazgo ya documentado para F2 y para TP27).
 */
@RestController
@RequestMapping(Constants.URL_ALARMAS)
@Tag(description = "Configuracion global de alarmas de temperatura (F6)", name = "ConfiguracionAlarma")
public class ConfiguracionAlarmaRestController extends BaseRestController {

	@Autowired
	private IConfiguracionAlarmaBusiness configuracionAlarmaBusiness;

	@Autowired
	private IStandartResponseBusiness response;

	@Operation(summary = "Consultar la configuracion global de alarmas",
			description = "Devuelve el umbral de temperatura y los destinatarios vigentes. Requiere el rol "
					+ "ROLE_SUPERVISOR (decision del equipo): es una accion de politica, no una operacion diaria "
					+ "del circuito. Requiere autenticacion JWT.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Configuracion vigente.",
					content = @Content(schema = @Schema(implementation = ConfiguracionAlarmaResponse.class), examples = @ExampleObject(value = "{\"umbralTemperaturaC\":33.00,\"destinatarios\":\"alertas@example.invalid,turno@example.invalid\"}"))),
			@ApiResponse(responseCode = "401", description = "Sin token o token invalido/alterado.", content = @Content),
			@ApiResponse(responseCode = "403", description = "Autenticado pero sin ROLE_SUPERVISOR.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"No tiene los permisos necesarios para esta operacion\",\"code\":403,\"devInfo\":\"No stack trace\"}"))),
			@ApiResponse(responseCode = "404", description = "Todavia no se configuro ninguna alarma (sin umbral por defecto).",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"No hay configuracion de alarmas definida\",\"code\":404,\"devInfo\":\"No stack trace\"}"))),
			@ApiResponse(responseCode = "500", description = "Error no clasificado al procesar la solicitud.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"Error interno al procesar la solicitud\",\"code\":500,\"devInfo\":\"No stack trace\"}")))
	})
	@GetMapping(value = "/configuracion", produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("hasAuthority('ROLE_SUPERVISOR')")
	public ResponseEntity<?> obtener() {
		try {
			ConfiguracionAlarma config = configuracionAlarmaBusiness.obtener();
			return new ResponseEntity<>(
					new ConfiguracionAlarmaResponse(config.getUmbralTemperaturaC(), config.getDestinatarios()),
					HttpStatus.OK);
		} catch (NotFoundException e) {
			return new ResponseEntity<>(response.build(HttpStatus.NOT_FOUND, null, e.getMessage()), HttpStatus.NOT_FOUND);
		} catch (BusinessException e) {
			return new ResponseEntity<>(
					response.build(HttpStatus.INTERNAL_SERVER_ERROR, null, "Error interno al procesar la solicitud"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Operation(summary = "Crear o actualizar la configuracion global de alarmas",
			description = "Upsert de la fila unica de configuracion: la crea si no existe, la actualiza si ya "
					+ "existe. umbralTemperaturaC debe ser representable con la misma precision que las lecturas "
					+ "(hasta 2 decimales); destinatarios debe ser una lista de emails separados por coma, todos "
					+ "con formato valido. Requiere el rol ROLE_SUPERVISOR. Requiere autenticacion JWT.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Configuracion guardada.",
					content = @Content(schema = @Schema(implementation = ConfiguracionAlarmaResponse.class), examples = @ExampleObject(value = "{\"umbralTemperaturaC\":33.00,\"destinatarios\":\"alertas@example.invalid,turno@example.invalid\"}"))),
			@ApiResponse(responseCode = "400", description = "umbralTemperaturaC ausente o con mas de 2 decimales/4 enteros, o destinatarios vacio/con formato invalido.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = {
							@ExampleObject(name = "Umbral ausente", value = "{\"message\":\"umbralTemperaturaC: must not be null\",\"code\":400,\"devInfo\":\"No stack trace\"}"),
							@ExampleObject(name = "Destinatarios con formato invalido", value = "{\"message\":\"destinatariosValido: destinatarios debe ser una lista de emails separados por coma, todos con formato valido\",\"code\":400,\"devInfo\":\"No stack trace\"}")
					})),
			@ApiResponse(responseCode = "401", description = "Sin token o token invalido/alterado.", content = @Content),
			@ApiResponse(responseCode = "403", description = "Autenticado pero sin ROLE_SUPERVISOR.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"No tiene los permisos necesarios para esta operacion\",\"code\":403,\"devInfo\":\"No stack trace\"}"))),
			@ApiResponse(responseCode = "500", description = "Error no clasificado al procesar la solicitud.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"Error interno al procesar la solicitud\",\"code\":500,\"devInfo\":\"No stack trace\"}")))
	})
	@PutMapping(value = "/configuracion", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("hasAuthority('ROLE_SUPERVISOR')")
	public ResponseEntity<?> guardar(
			@org.springframework.web.bind.annotation.RequestBody @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(
					content = @Content(examples = @ExampleObject(value = "{\"umbralTemperaturaC\":33.00,\"destinatarios\":\"alertas@example.invalid,turno@example.invalid\"}")))
			ConfiguracionAlarmaRequest request) {
		try {
			ConfiguracionAlarma config = configuracionAlarmaBusiness.guardar(request);
			return new ResponseEntity<>(
					new ConfiguracionAlarmaResponse(config.getUmbralTemperaturaC(), config.getDestinatarios()),
					HttpStatus.OK);
		} catch (BusinessException e) {
			return new ResponseEntity<>(
					response.build(HttpStatus.INTERNAL_SERVER_ERROR, null, "Error interno al procesar la solicitud"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<?> handleValidacion(MethodArgumentNotValidException e) {
		String detalle = e.getBindingResult().getFieldErrors().stream()
				.map(fe -> fe.getField() + ": " + fe.getDefaultMessage()).collect(Collectors.joining("; "));
		return new ResponseEntity<>(response.build(HttpStatus.BAD_REQUEST, null, detalle), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<?> handleJsonInvalido(HttpMessageNotReadableException e) {
		return new ResponseEntity<>(response.build(HttpStatus.BAD_REQUEST, null, "JSON de entrada invalido o malformado"),
				HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
	public ResponseEntity<?> handleAccesoDenegado(org.springframework.security.access.AccessDeniedException e) {
		return new ResponseEntity<>(
				response.build(HttpStatus.FORBIDDEN, null, "No tiene los permisos necesarios para esta operacion"),
				HttpStatus.FORBIDDEN);
	}
}
