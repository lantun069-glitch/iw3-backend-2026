package ar.edu.iua.iw3.ordenes.business;

import ar.edu.iua.iw3.business.execption.BusinessException;
import ar.edu.iua.iw3.business.execption.NotFoundException;
import ar.edu.iua.iw3.ordenes.controllers.dto.ConfiguracionAlarmaRequest;
import ar.edu.iua.iw3.ordenes.model.ConfiguracionAlarma;

public interface IConfiguracionAlarmaBusiness {

	/**
	 * Configuracion global vigente (TP28). NotFoundException si nunca se
	 * configuro (sin umbral por defecto inventado): se mapea a 404 en el
	 * controller.
	 */
	public ConfiguracionAlarma obtener() throws NotFoundException, BusinessException;

	/**
	 * Crea la fila unica (ID_FIJO) si no existe, o actualiza sus valores
	 * si ya existe (upsert). La validacion de formato ya se hizo en
	 * ConfiguracionAlarmaRequest antes de llegar aqui.
	 */
	public ConfiguracionAlarma guardar(ConfiguracionAlarmaRequest request) throws BusinessException;
}
