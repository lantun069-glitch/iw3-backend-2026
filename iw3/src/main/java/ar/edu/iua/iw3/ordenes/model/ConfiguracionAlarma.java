package ar.edu.iua.iw3.ordenes.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuracion global de alarmas de temperatura (TP28, F6, primera
 * unidad). Fila unica: ID_FIJO es el unico id valido, nunca generado
 * automaticamente. Sin fila, no hay configuracion - DECISION DEL
 * EQUIPO: no se inventa un umbral por defecto (ver
 * OrdenBusiness.registrarLectura, que no evalua ninguna alarma si esta
 * entidad no existe en la base). Por orden (DECISION DEL EQUIPO, no es
 * por orden como detalleIntervaloSegundos de TP14): una sola fila para
 * todo el sistema.
 */
@Entity
@Table(name = "configuracion_alarma")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ConfiguracionAlarma {

	public static final Integer ID_FIJO = 1;

	@Id
	private Integer id;

	/** Misma precision que Orden.temperaturaC (DECIMAL(6,2)). */
	@Column(nullable = false, precision = 6, scale = 2)
	private BigDecimal umbralTemperaturaC;

	/**
	 * Lista de direcciones de correo separadas por coma. Validada en
	 * ConfiguracionAlarmaRequest (formato), no aqui: esta entidad no
	 * repite esa validacion.
	 */
	@Column(nullable = false, length = 500)
	private String destinatarios;
}
