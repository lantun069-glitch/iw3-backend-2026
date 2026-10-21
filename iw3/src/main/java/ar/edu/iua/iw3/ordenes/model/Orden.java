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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Unidad vertical de F2: solo cubre la recepcion (creacion en estado 1).
 * Los campos de F3/F4 (clave de activacion, snapshot, pesajes, etc.) no
 * se agregan todavia.
 *
 * uk_orden_order_number tiene un nombre explicito (no autogenerado) para
 * poder distinguir, en OrdenBusiness, esta restriccion de integridad de
 * cualquier otra ante una colision concurrente de orderNumber.
 */
@Entity
@Table(name = "ordenes", uniqueConstraints = @UniqueConstraint(name = "uk_orden_order_number", columnNames = "order_number"))
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Orden {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_number", nullable = false)
	private Integer orderNumber;

	@ManyToOne(optional = false)
	@JoinColumn(nullable = false)
	private Camion camion;

	@ManyToOne(optional = false)
	@JoinColumn(nullable = false)
	private Chofer chofer;

	@ManyToOne(optional = false)
	@JoinColumn(nullable = false)
	private Cliente cliente;

	@ManyToOne(optional = false)
	@JoinColumn(nullable = false)
	private Producto producto;

	/**
	 * Se asume recibida en hora local America/Argentina/Cordoba, sin
	 * offset explicito (ISO-8601 local, ej. 2026-11-03T08:30:00). No se
	 * convierte ninguna zona: ver docs/decisiones.md.
	 */
	@Column(nullable = false)
	private LocalDateTime fechaCargaPrevista;

	@Column(nullable = false, precision = 15, scale = 3)
	private BigDecimal presetKg;

	@Column(nullable = false)
	private Integer estado;

	@Column(nullable = false)
	private LocalDateTime fechaRecepcion;
}
