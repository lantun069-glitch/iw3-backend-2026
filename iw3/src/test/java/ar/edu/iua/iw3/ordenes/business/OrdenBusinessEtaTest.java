package ar.edu.iua.iw3.ordenes.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * TP32 (F7, segunda unidad de tiempos): prueba aislada de
 * OrdenBusiness.calcularEtaMinutos, sin Spring ni base de datos (es un
 * metodo estatico puro). No es un test de integracion a proposito: los
 * casos de caudal &lt;= 0 no son alcanzables a traves del flujo normal
 * de OrdenBusiness.registrarLectura, que descarta esas lecturas antes
 * de persistir el snapshot - por eso se ejercitan aca contra la
 * formula directamente, no contra una orden real guardada.
 */
class OrdenBusinessEtaTest {

	@Test
	void ejemploDeControlDeLaFuente_120Minutos() {
		// preset 10000 kg, masa 4000 kg, caudal 3000 kg/h -> 120 min
		// (caso numerico de control de CLAUDE.md Sec.6 / docs/requisitos.md TP32).
		BigDecimal eta = OrdenBusiness.calcularEtaMinutos(
				new BigDecimal("10000"), new BigDecimal("4000"), new BigDecimal("3000"));
		assertEquals(new BigDecimal("120.00"), eta);
	}

	@Test
	void redondeoHaciaArriba_resultadoNoExacto() {
		// faltante 6000 kg * 60 / 2800 kg/h = 128.5714... -> 128.57
		BigDecimal eta = OrdenBusiness.calcularEtaMinutos(
				new BigDecimal("10000"), new BigDecimal("4000"), new BigDecimal("2800"));
		assertEquals(new BigDecimal("128.57"), eta);
	}

	@Test
	void redondeoHalfUp_noHalfEven() {
		// faltante 1 kg * 60 / 480 kg/h = 0.125 exacto: HALF_UP redondea a
		// 0.13 (si fuera HALF_EVEN, redondearia a 0.12). Este caso
		// distingue explicitamente el modo de redondeo pedido.
		BigDecimal eta = OrdenBusiness.calcularEtaMinutos(
				new BigDecimal("10001"), new BigDecimal("10000"), new BigDecimal("480"));
		assertEquals(new BigDecimal("0.13"), eta);
	}

	@Test
	void sinMasa_noEstimable() {
		assertNull(OrdenBusiness.calcularEtaMinutos(new BigDecimal("10000"), null, new BigDecimal("3000")));
	}

	@Test
	void sinCaudal_noEstimable() {
		assertNull(OrdenBusiness.calcularEtaMinutos(new BigDecimal("10000"), new BigDecimal("4000"), null));
	}

	@Test
	void caudalCero_noEstimable() {
		// No alcanzable via registrarLectura (se descarta antes de persistir);
		// se prueba aislado contra la formula, ver javadoc de la clase.
		assertEquals(null,
				OrdenBusiness.calcularEtaMinutos(new BigDecimal("10000"), new BigDecimal("4000"), BigDecimal.ZERO));
	}

	@Test
	void caudalNegativo_noEstimable() {
		// Tampoco alcanzable via registrarLectura; mismo motivo que arriba.
		assertNull(OrdenBusiness.calcularEtaMinutos(new BigDecimal("10000"), new BigDecimal("4000"),
				new BigDecimal("-5")));
	}

	@Test
	void presetAlcanzadoExacto_cero() {
		BigDecimal eta = OrdenBusiness.calcularEtaMinutos(
				new BigDecimal("10000"), new BigDecimal("10000"), new BigDecimal("3000"));
		assertEquals(new BigDecimal("0.00"), eta);
	}

	@Test
	void presetSuperado_cero_sinCerrarLaOrden() {
		// calcularEtaMinutos es una funcion pura: no toca el estado de la
		// orden. "Sin cerrar la orden automaticamente" se verifica por
		// construccion (este metodo no recibe ni modifica un Orden), no
		// hace falta una asercion adicional sobre el estado aca.
		BigDecimal eta = OrdenBusiness.calcularEtaMinutos(
				new BigDecimal("10000"), new BigDecimal("10500"), new BigDecimal("3000"));
		assertEquals(new BigDecimal("0.00"), eta);
	}
}
