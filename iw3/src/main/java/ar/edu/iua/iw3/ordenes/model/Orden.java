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
 * F2 cubrio la recepcion (creacion en estado 1). F3 (esta unidad) agrega
 * tara/clave de activacion (transicion 1->2) y fecha de activacion. Los
 * campos de telemetria/snapshot/histórico de F3 (segunda unidad en
 * adelante) y los de F4 (pesaje final) todavia no se agregan.
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

	/** Pesaje inicial (camion vacio), registrado en la transicion 1->2. */
	@Column(precision = 15, scale = 3)
	private BigDecimal taraKg;

	@Column
	private LocalDateTime fechaTara;

	/**
	 * Numero entero de 5 digitos [10000,99999], generado al registrar la
	 * tara. No necesita ser unico entre ordenes: la activacion se valida
	 * contra la orden identificada por orderNumber, no se busca por clave.
	 * Nunca se expone en logs ni en observaciones de HistorialEstado.
	 */
	@Column
	private Integer claveActivacion;

	/**
	 * Instante en que la API acepto la clave de activacion correcta. No se
	 * equipara automaticamente al inicio fisico real de la carga: esa
	 * relacion con la primera telemetria valida se resuelve en la proxima
	 * unidad. La activacion es idempotente: este valor se fija una sola
	 * vez y no se sobreescribe en activaciones posteriores de la misma
	 * orden.
	 */
	@Column
	private LocalDateTime fechaActivacion;
}
