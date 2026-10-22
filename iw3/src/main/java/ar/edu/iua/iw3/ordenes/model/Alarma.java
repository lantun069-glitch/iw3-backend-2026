package ar.edu.iua.iw3.ordenes.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Alarma de temperatura (TP28/TP29, F6, primera unidad). Una fila por
 * disparo: temperaturaC y umbralAplicado quedan congelados con los
 * valores reales del momento del disparo, para que la explicacion de
 * una alarma vieja no cambie si la configuracion global se modifica
 * despues (DECISION DEL EQUIPO). aceptada=false ("pendiente") inhibe
 * nuevos disparos para la misma orden (ver
 * OrdenBusiness.registrarLectura); no hay mas de una alarma pendiente
 * por orden al mismo tiempo, verificado antes de crear una nueva, bajo
 * el mismo bloqueo de fila de la orden que el resto de esta unidad de
 * negocio.
 */
@Entity
@Table(name = "alarmas")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Alarma {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(nullable = false)
	private Orden orden;

	@Column(nullable = false)
	private LocalDateTime fechaDisparo;

	/** Temperatura real que disparo esta alarma. */
	@Column(nullable = false, precision = 6, scale = 2)
	private BigDecimal temperaturaC;

	/** Umbral vigente en el momento del disparo (snapshot, no referencia
	 * viva a ConfiguracionAlarma: ver Javadoc de la clase). */
	@Column(nullable = false, precision = 6, scale = 2)
	private BigDecimal umbralAplicado;

	@Column(nullable = false)
	private boolean aceptada;

	@Column
	private LocalDateTime fechaAceptacion;

	@Column(length = 100)
	private String usuarioAceptacion;

	@Column(length = 500)
	private String observacion;
}
