package ar.edu.iua.iw3.ordenes.business;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;

/**
 * Evento inmutable de dominio (TP28/TP29, F6, segunda unidad): se
 * publica cuando OrdenBusiness.registrarLectura dispara una alarma,
 * y lo consume AlarmaEmailListener despues de confirmado el commit
 * (ver esa clase). A proposito NO lleva ninguna entidad JPA (ni
 * Alarma, ni Orden, ni ConfiguracionAlarma): solo datos primitivos/
 * inmutables, copiados en el momento del disparo, para que el
 * listener asincrono no dependa de una sesion de Hibernate que ya
 * pudo haberse cerrado, ni de una relacion lazy, ni de que la
 * configuracion global siga teniendo los mismos valores cuando el
 * listener efectivamente corra.
 */
@Getter
public class AlarmaDisparadaEvent {
	private final Long idAlarma;
	private final Integer orderNumber;
	private final BigDecimal temperaturaC;
	private final BigDecimal umbralAplicado;
	private final LocalDateTime fechaDisparo;
	private final List<String> destinatarios;

	public AlarmaDisparadaEvent(Long idAlarma, Integer orderNumber, BigDecimal temperaturaC,
			BigDecimal umbralAplicado, LocalDateTime fechaDisparo, List<String> destinatarios) {
		this.idAlarma = idAlarma;
		this.orderNumber = orderNumber;
		this.temperaturaC = temperaturaC;
		this.umbralAplicado = umbralAplicado;
		this.fechaDisparo = fechaDisparo;
		// Copia defensiva e inmutable: snapshot de los destinatarios
		// configurados al momento del disparo, no una referencia viva a
		// ConfiguracionAlarma. Un cambio de configuracion posterior no
		// debe alterar a quien se le avisa de un disparo ya confirmado.
		this.destinatarios = List.copyOf(destinatarios);
	}
}
