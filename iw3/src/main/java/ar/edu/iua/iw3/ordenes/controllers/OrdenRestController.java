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

	@PostMapping(value = "", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> recibir(@Valid @RequestBody OrdenRecepcionRequest request) {
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
