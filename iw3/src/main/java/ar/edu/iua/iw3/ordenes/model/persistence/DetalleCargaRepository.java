package ar.edu.iua.iw3.ordenes.model.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import ar.edu.iua.iw3.ordenes.business.EstadisticasDetalle;
import ar.edu.iua.iw3.ordenes.model.DetalleCarga;
import ar.edu.iua.iw3.ordenes.model.Orden;

@Repository
public interface DetalleCargaRepository extends JpaRepository<DetalleCarga, Long> {

	/**
	 * SUM + COUNT (no AVG, ver EstadisticasDetalle) de temperatura,
	 * densidad y caudal sobre todo el detalle historico de una orden
	 * (TP19). Si la orden no tiene ninguna fila de detalle, cantidad=0 y
	 * las tres sumas son null (semantica estandar de SUM sobre un
	 * conjunto vacio); OrdenBusiness.calcularConciliacion no divide en
	 * ese caso, para no devolver un promedio inventado.
	 */
	@Query("SELECT new ar.edu.iua.iw3.ordenes.business.EstadisticasDetalle(SUM(d.temperaturaC), SUM(d.densidad), SUM(d.caudalKgH), COUNT(d)) "
			+ "FROM DetalleCarga d WHERE d.orden = :orden")
	EstadisticasDetalle calcularEstadisticas(@Param("orden") Orden orden);
}
