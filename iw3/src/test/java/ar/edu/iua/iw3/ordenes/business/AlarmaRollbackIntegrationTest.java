package ar.edu.iua.iw3.ordenes.business;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionRequest;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionRequest.CamionDTO;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionRequest.ChoferDTO;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionRequest.ClienteDTO;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionRequest.ProductoDTO;
import ar.edu.iua.iw3.ordenes.model.Orden;
import ar.edu.iua.iw3.ordenes.model.persistence.AlarmaRepository;
import ar.edu.iua.iw3.ordenes.model.persistence.OrdenRepository;

/**
 * TP28/TP29 (F6, segunda unidad), verificacion 5: una lectura cuya
 * transaccion se revierte no debe dejar una Alarma persistida ni
 * disparar el envio de correo. Usa el mecanismo REAL de transacciones
 * (TransactionTemplate + PlatformTransactionManager, ambos ya provistos
 * por Spring, no agregados para este test) para forzar el rollback
 * DESDE AFUERA de OrdenBusiness.registrarLectura - no se agrega ningun
 * interruptor de fallo ni endpoint al codigo productivo. El
 * AlarmaEmailListener real (@TransactionalEventListener AFTER_COMMIT)
 * nunca deberia invocarse, porque Spring no dispara ese listener si la
 * transaccion termino en rollback - eso es lo que esta prueba verifica,
 * no lo que simula.
 *
 * Requiere que la aplicacion este configurada contra un receptor SMTP
 * de prueba (SMTP_HOST/SMTP_PORT apuntando a tools/smtp_test_catcher.py,
 * ver tools/README.md) cuyo archivo de mensajes recibidos se pasa por
 * la propiedad de sistema "smtp.catcher.logfile" (default
 * "../tools/smtp-received.log", correcto cuando este test se corre con
 * `./mvnw test` desde iw3/, que es el directorio de trabajo habitual -
 * ver docs/contrato-f6-correo-alarmas.md). No envia correos reales.
 *
 * Este test NO requiere que el receptor este corriendo para pasar: una
 * transaccion revertida nunca llega a intentar el envio, con o sin
 * receptor disponible (ver tools/README.md para el detalle de esta
 * relacion). Corrido en la misma sesion que los casos de envio
 * exitoso (con el receptor arriba) es lo que le da valor de evidencia
 * real al archivo compartido.
 */
@SpringBootTest
class AlarmaRollbackIntegrationTest {

	@Autowired
	private IOrdenBusiness ordenBusiness;

	@Autowired
	private OrdenRepository ordenDAO;

	@Autowired
	private AlarmaRepository alarmaDAO;

	@Autowired
	private PlatformTransactionManager txManager;

	@Test
	void lecturaRevertidaNoDejaAlarmaNiEnviaCorreo() throws Exception {
		int orderNumber = 900000 + (int) (System.currentTimeMillis() % 90000);
		String sufijo = String.valueOf(orderNumber);

		// Setup fuera de la transaccion que se va a revertir: cada una de
		// estas llamadas es su propia transaccion normal, que SI confirma.
		ordenBusiness.recibirOrden(construirRequest(orderNumber, sufijo), "test-rollback");
		ordenBusiness.registrarTara(orderNumber, new BigDecimal("12000.000"), "test-rollback");
		Orden ordenTrasRecepcion = ordenDAO.findByOrderNumber(orderNumber).orElseThrow();
		ordenBusiness.activar(orderNumber, ordenTrasRecepcion.getClaveActivacion());

		long logSizeAntes = tamanioLog();

		// La lectura con temperatura alta (dispara la alarma) corre DENTRO
		// de una transaccion controlada por este test; se fuerza su
		// reversion con status.setRollbackOnly(), sin tocar OrdenBusiness.
		TransactionTemplate tx = new TransactionTemplate(txManager);
		tx.executeWithoutResult(status -> {
			try {
				ordenBusiness.registrarLectura(orderNumber, new BigDecimal("1000.000"), new BigDecimal("0.5400"),
						new BigDecimal("50.00"), new BigDecimal("3000.000"));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			status.setRollbackOnly();
		});

		// Verificacion inmediata en BD: la reversion debe haber deshecho
		// tanto el snapshot (fuera de alcance de esta prueba puntual, pero
		// confirma que el rollback fue real) como la fila de Alarma.
		Orden ordenTrasRollback = ordenDAO.findByOrderNumber(orderNumber).orElseThrow();
		assertFalse(alarmaDAO.existsByOrdenAndAceptadaFalse(ordenTrasRollback),
				"No deberia existir ninguna alarma pendiente: la transaccion que la creaba se revirtio");
		assertTrue(ordenTrasRollback.getTemperaturaC() == null,
				"El snapshot tampoco deberia haberse actualizado: toda la transaccion se revirtio");

		// Espera con limite de tiempo (no se asume un resultado inmediato):
		// si por algun motivo el listener async llegara a dispararse de
		// todas formas, dar tiempo a que aparezca antes de concluir que no
		// paso. AFTER_COMMIT no deberia invocarse nunca para esta
		// transaccion revertida.
		long limite = System.currentTimeMillis() + 5000;
		boolean aparecioMensajeNuevo = false;
		while (System.currentTimeMillis() < limite) {
			if (tamanioLog() > logSizeAntes) {
				aparecioMensajeNuevo = true;
				break;
			}
			Thread.sleep(250);
		}
		assertFalse(aparecioMensajeNuevo,
				"No deberia haberse enviado ningun correo: la transaccion que disparaba la alarma se revirtio");
	}

	private long tamanioLog() throws IOException {
		Path path = Path.of(System.getProperty("smtp.catcher.logfile", "../tools/smtp-received.log"));
		if (!Files.exists(path)) {
			return 0L;
		}
		return Files.size(path);
	}

	private OrdenRecepcionRequest construirRequest(int orderNumber, String sufijo) {
		OrdenRecepcionRequest request = new OrdenRecepcionRequest();
		request.setOrderNumber(orderNumber);
		request.setFechaCargaPrevista(LocalDateTime.of(2026, 11, 3, 8, 30));
		request.setPresetKg(new BigDecimal("10000.500"));

		CamionDTO camion = new CamionDTO();
		camion.setExternalCode("CAM-RB-" + sufijo);
		camion.setPatente("RB" + sufijo);
		camion.setCapacidadKg(new BigDecimal("12000.000"));
		request.setCamion(camion);

		ChoferDTO chofer = new ChoferDTO();
		chofer.setExternalCode("CHO-RB-" + sufijo);
		chofer.setNombre("Test");
		chofer.setApellido("Rollback");
		request.setChofer(chofer);

		ClienteDTO cliente = new ClienteDTO();
		cliente.setExternalCode("CLI-RB-" + sufijo);
		cliente.setRazonSocial("Cliente Rollback " + sufijo);
		request.setCliente(cliente);

		ProductoDTO producto = new ProductoDTO();
		producto.setExternalCode("PRD-RB-" + sufijo);
		producto.setNombre("GLP");
		request.setProducto(producto);

		return request;
	}
}
