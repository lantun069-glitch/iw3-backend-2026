package ar.edu.iua.iw3.ordenes.model.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ar.edu.iua.iw3.ordenes.model.Alarma;
import ar.edu.iua.iw3.ordenes.model.Orden;

@Repository
public interface AlarmaRepository extends JpaRepository<Alarma, Long> {

	/** Maximo una alarma pendiente por orden (TP29): consultado antes de
	 * crear una nueva, dentro del mismo bloqueo de fila de la orden. */
	boolean existsByOrdenAndAceptadaFalse(Orden orden);

	/** Alcance por orden (TP27-like): una alarma de otra orden no se
	 * encuentra aqui, para que aceptarAlarma devuelva 404 sin distinguir
	 * "no existe" de "existe pero es de otra orden". */
	Optional<Alarma> findByIdAndOrden(Long id, Orden orden);

	List<Alarma> findByOrdenOrderByFechaDisparoAsc(Orden orden);
}
