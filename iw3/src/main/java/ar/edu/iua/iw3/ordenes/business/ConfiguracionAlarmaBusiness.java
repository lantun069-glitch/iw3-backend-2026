package ar.edu.iua.iw3.ordenes.business;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iua.iw3.business.execption.BusinessException;
import ar.edu.iua.iw3.business.execption.NotFoundException;
import ar.edu.iua.iw3.ordenes.controllers.dto.ConfiguracionAlarmaRequest;
import ar.edu.iua.iw3.ordenes.model.ConfiguracionAlarma;
import ar.edu.iua.iw3.ordenes.model.persistence.ConfiguracionAlarmaRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ConfiguracionAlarmaBusiness implements IConfiguracionAlarmaBusiness {

	@Autowired
	private ConfiguracionAlarmaRepository configuracionAlarmaDAO;

	@Override
	public ConfiguracionAlarma obtener() throws NotFoundException, BusinessException {
		try {
			return configuracionAlarmaDAO.findById(ConfiguracionAlarma.ID_FIJO)
					.orElseThrow(() -> NotFoundException.builder()
							.message("No hay configuracion de alarmas definida").build());
		} catch (NotFoundException e) {
			throw e;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ConfiguracionAlarma guardar(ConfiguracionAlarmaRequest request) throws BusinessException {
		try {
			ConfiguracionAlarma config = configuracionAlarmaDAO.findById(ConfiguracionAlarma.ID_FIJO)
					.orElseGet(() -> {
						ConfiguracionAlarma nueva = new ConfiguracionAlarma();
						nueva.setId(ConfiguracionAlarma.ID_FIJO);
						return nueva;
					});
			config.setUmbralTemperaturaC(request.getUmbralTemperaturaC());
			config.setDestinatarios(request.getDestinatarios());
			return configuracionAlarmaDAO.save(config);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}
	}
}
