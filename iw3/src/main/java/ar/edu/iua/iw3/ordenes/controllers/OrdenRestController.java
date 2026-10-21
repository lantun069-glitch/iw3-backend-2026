package ar.edu.iua.iw3.ordenes.controllers;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iua.iw3.business.execption.BusinessException;
import ar.edu.iua.iw3.business.execption.FoundException;
import ar.edu.iua.iw3.controllers.BaseRestController;
import ar.edu.iua.iw3.controllers.Constants;
import ar.edu.iua.iw3.ordenes.business.IOrdenBusiness;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionRequest;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionResponse;
import ar.edu.iua.iw3.ordenes.model.Orden;
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
 * Unidad vertical de F2: solo la recepcion de una orden. No expone
 * CRUD/consulta para Orden ni para sus referencias (Camion/Chofer/
 * Cliente/Producto); eso no esta pedido para esta unidad.
 *
 * La validacion de entrada (400) se maneja con @ExceptionHandler LOCAL
 * a este controlador (no un @ControllerAdvice global, para no introducir
 * infraestructura generica no pedida). Motivo del cambio: dejar que
 * Spring resuelva la excepcion por su mecanismo por defecto termina en
 * un forward interno a /error, que vuelve a pasar por la cadena de
 * filtros de seguridad y devuelve 401 en vez de 400 (/error no esta en
 * la lista de rutas publicas, y no se la agrega: ver docs/decisiones.md).
 * Manejando la excepcion aqui, nunca se llega a ese forward.
 *
 * Ninguno de los catch de este controlador (400 por los dos
 * @ExceptionHandler, 409 y 500 en recibir()) le pasa la excepcion real
 * a StandartResponse: todos pasan null como "ex", para que
 * getDevInfo() devuelva "No stack trace" en vez de un stacktrace real,
 * independientemente de dev.info.enabled. El 409 usa el mensaje propio
 * de FoundException (no expone detalle de la causa). El 500 usa un
 * mensaje generico fijo, porque el mensaje de BusinessException puede
 * terminar reflejando el toString() de la causa interna segun el
 * constructor Lombok que se haya invocado (ver docs/decisiones.md).
 */
@RestController
@RequestMapping(Constants.URL_ORDERS)
@Tag(description = "Recepcion de ordenes de carga (F2)", name = "Orden")
public class OrdenRestController extends BaseRestController {

	@Autowired
	private IOrdenBusiness ordenBusiness;

	@Autowired
	private IStandartResponseBusiness response;

	@Operation(summary = "Recibir una orden de carga (estado 1)",
			description = "Crea una orden nueva en estado 1, o sincroniza camion/chofer/cliente/producto "
					+ "por externalCode si ya existian. No acepta estado, id interno, fechaRecepcion ni el actor "
					+ "del historial en el JSON de entrada: el servidor los genera. Requiere autenticacion JWT "
					+ "(Authorization: Bearer <token>), igual que el resto de la API.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Orden creada en estado 1.",
					content = @Content(schema = @Schema(implementation = OrdenRecepcionResponse.class), examples = @ExampleObject(name = "Orden creada", value = "{\"id\":1,\"orderNumber\":900001,\"estado\":1,\"fechaRecepcion\":\"2026-10-20T20:06:30.119016859\"}"))),
			@ApiResponse(responseCode = "400", description = "Entrada invalida: campo obligatorio ausente/vacio, texto que excede su longitud maxima, decimales fuera de precision, cantidad <= 0, o JSON malformado.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = {
							@ExampleObject(name = "Campo obligatorio ausente", value = "{\"message\":\"camion.patente: must not be blank\",\"code\":400,\"devInfo\":\"No stack trace\"}"),
							@ExampleObject(name = "JSON malformado", value = "{\"message\":\"JSON de entrada invalido o malformado\",\"code\":400,\"devInfo\":\"No stack trace\"}")
					})),
			@ApiResponse(responseCode = "401", description = "Sin token o token invalido/alterado. Respuesta estandar de Spring Security, sin cuerpo JSON (no pasa por StandartResponse).",
					content = @Content),
			@ApiResponse(responseCode = "409", description = "orderNumber ya existe (chequeo previo o colision concurrente detectada por la restriccion unica de la base).",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"Ya existe una orden con orderNumber=900001\",\"code\":409,\"devInfo\":\"No stack trace\"}"))),
			@ApiResponse(responseCode = "500", description = "Error no clasificado al procesar la solicitud.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"Error interno al procesar la solicitud\",\"code\":500,\"devInfo\":\"No stack trace\"}")))
	})
	@PostMapping(value = "", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> recibir(
			@org.springframework.web.bind.annotation.RequestBody @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(
					content = @Content(examples = @ExampleObject(value = "{\"orderNumber\":900001,\"fechaCargaPrevista\":\"2026-11-03T08:30:00\",\"presetKg\":10000.500,\"camion\":{\"externalCode\":\"CAM-00891\",\"patente\":\"AB123CD\",\"descripcion\":\"Camion cisterna GLP\",\"capacidadKg\":12000.000},\"chofer\":{\"externalCode\":\"CHO-00457\",\"nombre\":\"Marcos\",\"apellido\":\"Toledo\",\"documento\":\"30111222\"},\"cliente\":{\"externalCode\":\"CLI-00019\",\"razonSocial\":\"Distribuidora del Sur S.A.\",\"contacto\":\"compras@distribuidorasur.com.ar\"},\"producto\":{\"externalCode\":\"PRD-GLP01\",\"nombre\":\"GLP\",\"descripcion\":\"Gas licuado de petroleo\"}}")))
			OrdenRecepcionRequest request) {
		// El actor viene del contexto de seguridad, nunca del JSON de entrada.
		String actor = getUserLogged().getUsername();
		try {
			Orden orden = ordenBusiness.recibirOrden(request, actor);
			OrdenRecepcionResponse body = new OrdenRecepcionResponse(orden.getId(), orden.getOrderNumber(),
					orden.getEstado(), orden.getFechaRecepcion());
			return new ResponseEntity<>(body, HttpStatus.CREATED);
		} catch (FoundException e) {
			// ex=null a proposito: no se expone la excepcion real al cliente.
			return new ResponseEntity<>(response.build(HttpStatus.CONFLICT, null, e.getMessage()), HttpStatus.CONFLICT);
		} catch (BusinessException e) {
			// ex=null a proposito; mensaje generico fijo (no e.getMessage()):
			// no se expone detalle interno de la causa al cliente.
			return new ResponseEntity<>(
					response.build(HttpStatus.INTERNAL_SERVER_ERROR, null, "Error interno al procesar la solicitud"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<?> handleValidacion(MethodArgumentNotValidException e) {
		String detalle = e.getBindingResult().getFieldErrors().stream()
				.map(fe -> fe.getField() + ": " + fe.getDefaultMessage()).collect(Collectors.joining("; "));
		// ex=null a proposito: no se expone la excepcion real al cliente.
		return new ResponseEntity<>(response.build(HttpStatus.BAD_REQUEST, null, detalle), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<?> handleJsonInvalido(HttpMessageNotReadableException e) {
		return new ResponseEntity<>(response.build(HttpStatus.BAD_REQUEST, null, "JSON de entrada invalido o malformado"),
				HttpStatus.BAD_REQUEST);
	}
}
