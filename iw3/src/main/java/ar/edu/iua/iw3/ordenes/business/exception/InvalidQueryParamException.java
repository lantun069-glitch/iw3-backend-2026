package ar.edu.iua.iw3.ordenes.business.exception;

import lombok.Builder;
import lombok.NoArgsConstructor;

/**
 * Un parametro de consulta (estado, page, size, sort) tiene un valor
 * sintacticamente recibido pero fuera de lo admitido (TP31, F7): un
 * estado fuera de [1,4], page/size fuera de rango, o un campo/
 * direccion de ordenamiento que no esta en la lista explicita de
 * campos admitidos. Se mapea a 400 en el controller, mismo criterio
 * que InvalidActivationKeyException.
 */
@NoArgsConstructor
public class InvalidQueryParamException extends Exception {

	private static final long serialVersionUID = 1L;

	@Builder
	public InvalidQueryParamException(String message) {
		super(message);
	}
}
