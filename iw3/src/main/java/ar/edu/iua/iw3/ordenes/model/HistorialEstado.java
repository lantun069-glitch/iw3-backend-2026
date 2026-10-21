package ar.edu.iua.iw3.ordenes.model;

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
 * Auditoria de transiciones de estado de una Orden.
 *
 * Requisito confirmado (TP21) -- fuente "Parte 1 - Lectura de la consigna
 * del trabajo integrador.txt" (fuente 59f2c5bf-3d78-4e71-a60a-153dbecb9c27),
 * tramo final del documento (aprox. caracter 19700-20300 de 21713):
 * "cada cambio de estado debe quedar registrado en la base de datos,
 * incluyendo fecha y hora del cambio, usuario o proceso que realizo la
 * transaccion, observaciones se aplica [sic, probable ruido de ASR por
 * "observacion si aplica"], se recomienda implementar un log de auditoria
 * de estado para la trazabilidad completa del ciclo de vida de la orden."
 * El requisito es registrar fecha/hora + usuario o proceso + observacion
 * si aplica; la recomendacion de un log dedicado no es la unica
 * estructura permitida.
 *
 * Auditar especificamente la CREACION (transicion a estado 1) es una
 * DECISION DEL EQUIPO para esta unidad, no un requisito textual aparte:
 * T1 exige auditar "cada cambio de estado", y la creacion es el primer
 * cambio de estado del ciclo de vida de la orden.
 */
@Entity
@Table(name = "historial_estados")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class HistorialEstado {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(nullable = false)
	private Orden orden;

	@Column(nullable = false)
	private Integer estadoNuevo;

	@Column(nullable = false)
	private LocalDateTime fechaHora;

	@Column(nullable = false, length = 150)
	private String usuarioOProceso;

	@Column(length = 255)
	private String observacion;
}
