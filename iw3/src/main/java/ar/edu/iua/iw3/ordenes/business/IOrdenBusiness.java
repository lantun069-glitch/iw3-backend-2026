package ar.edu.iua.iw3.ordenes.business;

import ar.edu.iua.iw3.business.execption.BusinessException;
import ar.edu.iua.iw3.business.execption.FoundException;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionRequest;
import ar.edu.iua.iw3.ordenes.model.Orden;

public interface IOrdenBusiness {
	/**
	 * Sincroniza camion/chofer/cliente/producto por externalCode, crea la
	 * orden en estado 1 y registra su HistorialEstado, en una unica
	 * transaccion. actor viene del contexto de seguridad, nunca del JSON.
	 */
	public Orden recibirOrden(OrdenRecepcionRequest request, String actor) throws FoundException, BusinessException;
}
