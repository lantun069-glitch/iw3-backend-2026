package ar.edu.iua.iw3.ordenes.business;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import ar.edu.iua.iw3.util.EmailBusiness;
import lombok.extern.slf4j.Slf4j;

/**
 * TP28/TP29 (F6, segunda unidad): envia la notificacion de una alarma
 * ya disparada. @TransactionalEventListener(AFTER_COMMIT) asegura que
 * solo corre si la transaccion de OrdenBusiness.registrarLectura
 * efectivamente confirmo (una reversion posterior nunca invoca este
 * metodo, sin ninguna comprobacion adicional). @Async lo saca del hilo
 * original y lo corre fuera de cualquier bloqueo de fila: para cuando
 * esto se ejecuta, el PESSIMISTIC_WRITE de esa transaccion ya se
 * libero en el commit. Reutiliza @EnableAsync, ya habilitado en la
 * aplicacion (Scheduler.java) - no se agrega infraestructura nueva de
 * async, solo este listener.
 *
 * Captura por destinatario: una falla en uno no impide intentar los
 * demas. Sin reintento automatico - no se promete entrega exactamente
 * una vez: si el proceso cae entre el commit y el envio (o durante
 * el envio), esa notificacion se pierde sin mas aviso que el log de
 * error. Es una limitacion aceptada para esta unidad, no un
 * mecanismo de garantia de entrega.
 */
@Component
@Slf4j
public class AlarmaEmailListener {

	@Autowired
	private EmailBusiness emailBusiness;

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onAlarmaDisparada(AlarmaDisparadaEvent event) {
		String subject = "Alarma de temperatura - orden " + event.getOrderNumber();
		String text = "La orden " + event.getOrderNumber() + " registro una temperatura de "
				+ event.getTemperaturaC() + " C el " + event.getFechaDisparo()
				+ ", superando el umbral configurado de " + event.getUmbralAplicado() + " C.";
		for (String destinatario : event.getDestinatarios()) {
			try {
				emailBusiness.sendSimpleMessage(destinatario, subject, text);
			} catch (Exception e) {
				log.error("No se pudo enviar la notificacion de la alarma id={} (orderNumber={}) a {}: {}",
						event.getIdAlarma(), event.getOrderNumber(), destinatario, e.getMessage());
			}
		}
	}
}
