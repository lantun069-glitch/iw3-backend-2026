package ar.edu.iua.iw3.ordenes.business;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iua.iw3.business.execption.BusinessException;
import ar.edu.iua.iw3.business.execption.FoundException;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionRequest;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionRequest.CamionDTO;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionRequest.ChoferDTO;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionRequest.ClienteDTO;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionRequest.ProductoDTO;
import ar.edu.iua.iw3.ordenes.model.Camion;
import ar.edu.iua.iw3.ordenes.model.Chofer;
import ar.edu.iua.iw3.ordenes.model.Cliente;
import ar.edu.iua.iw3.ordenes.model.HistorialEstado;
import ar.edu.iua.iw3.ordenes.model.Orden;
import ar.edu.iua.iw3.ordenes.model.Producto;
import ar.edu.iua.iw3.ordenes.model.persistence.CamionRepository;
import ar.edu.iua.iw3.ordenes.model.persistence.ChoferRepository;
import ar.edu.iua.iw3.ordenes.model.persistence.ClienteRepository;
import ar.edu.iua.iw3.ordenes.model.persistence.HistorialEstadoRepository;
import ar.edu.iua.iw3.ordenes.model.persistence.OrdenRepository;
import ar.edu.iua.iw3.ordenes.model.persistence.ProductoRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OrdenBusiness implements IOrdenBusiness {

	/** Convencion explicita de zona para esta unidad: no se usa la zona
	 * por defecto de la maquina. Ver docs/decisiones.md. */
	private static final ZoneId ZONA_RECEPCION = ZoneId.of("America/Argentina/Cordoba");

	/** Nombre explicito del unique constraint de Orden (ver Orden.java),
	 * para distinguir una colision de orderNumber de cualquier otra
	 * violacion de integridad. No se traduce cualquier error de
	 * integridad a "orden duplicada". */
	private static final String NOMBRE_CONSTRAINT_ORDER_NUMBER = "uk_orden_order_number";

	@Autowired
	private OrdenRepository ordenDAO;
	@Autowired
	private CamionRepository camionDAO;
	@Autowired
	private ChoferRepository choferDAO;
	@Autowired
	private ClienteRepository clienteDAO;
	@Autowired
	private ProductoRepository productoDAO;
	@Autowired
	private HistorialEstadoRepository historialDAO;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Orden recibirOrden(OrdenRecepcionRequest request, String actor) throws FoundException, BusinessException {
		try {
			// Chequeo temprano: evita sincronizar referencias para una orden
			// que ya sabemos que esta duplicada en el camino comun (no es la
			// unica proteccion: la restriccion unica de BD cubre la colision
			// concurrente, ver el catch de DataIntegrityViolationException).
			if (ordenDAO.existsByOrderNumber(request.getOrderNumber())) {
				throw FoundException.builder()
						.message("Ya existe una orden con orderNumber=" + request.getOrderNumber()).build();
			}

			Camion camion = sincronizarCamion(request.getCamion());
			Chofer chofer = sincronizarChofer(request.getChofer());
			Cliente cliente = sincronizarCliente(request.getCliente());
			Producto producto = sincronizarProducto(request.getProducto());

			// Una sola fecha/hora para recepcion e historial de creacion.
			LocalDateTime ahora = LocalDateTime.now(ZONA_RECEPCION);

			Orden orden = new Orden();
			orden.setOrderNumber(request.getOrderNumber());
			orden.setCamion(camion);
			orden.setChofer(chofer);
			orden.setCliente(cliente);
			orden.setProducto(producto);
			orden.setFechaCargaPrevista(request.getFechaCargaPrevista());
			orden.setPresetKg(request.getPresetKg());
			orden.setEstado(1);
			orden.setFechaRecepcion(ahora);

			try {
				orden = ordenDAO.save(orden);
			} catch (DataIntegrityViolationException e) {
				String causa = causaMasEspecifica(e);
				if (causa != null && causa.toLowerCase().contains(NOMBRE_CONSTRAINT_ORDER_NUMBER)) {
					// Colision concurrente: dos solicitudes pasaron el chequeo
					// temprano con el mismo orderNumber; la restriccion de BD
					// es la que realmente lo evita. Rollback por la anotacion
					// @Transactional al relanzar esta excepcion checked.
					throw FoundException.builder()
							.message("Ya existe una orden con orderNumber=" + request.getOrderNumber()).build();
				}
				// Cualquier otra violacion de integridad no se interpreta como
				// "orden duplicada".
				log.error(e.getMessage(), e);
				throw BusinessException.builder().ex(e).build();
			}

			HistorialEstado historial = new HistorialEstado();
			historial.setOrden(orden);
			historial.setEstadoNuevo(1);
			historial.setFechaHora(ahora);
			historial.setUsuarioOProceso(actor);
			historialDAO.save(historial);

			return orden;
		} catch (FoundException | BusinessException e) {
			throw e;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}
	}

	private String causaMasEspecifica(Throwable e) {
		Throwable t = e;
		while (t.getCause() != null && t.getCause() != t) {
			t = t.getCause();
		}
		return t.getMessage();
	}

	/**
	 * Buscar por externalCode, crear si no existe, actualizar si existe.
	 * Campos obligatorios siempre se sobreescriben con el valor recibido.
	 * Campos opcionales: si el DTO los trae null, se conserva el valor ya
	 * guardado (al crear, quedan null). No hay borrado implicito de un
	 * opcional ya guardado por omitirlo en una recepcion posterior.
	 */
	private Camion sincronizarCamion(CamionDTO dto) {
		Camion camion = camionDAO.findByExternalCode(dto.getExternalCode()).orElseGet(Camion::new);
		camion.setExternalCode(dto.getExternalCode());
		camion.setPatente(dto.getPatente());
		camion.setCapacidadKg(dto.getCapacidadKg());
		if (dto.getDescripcion() != null) {
			camion.setDescripcion(dto.getDescripcion());
		}
		return camionDAO.save(camion);
	}

	private Chofer sincronizarChofer(ChoferDTO dto) {
		Chofer chofer = choferDAO.findByExternalCode(dto.getExternalCode()).orElseGet(Chofer::new);
		chofer.setExternalCode(dto.getExternalCode());
		chofer.setNombre(dto.getNombre());
		chofer.setApellido(dto.getApellido());
		if (dto.getDocumento() != null) {
			chofer.setDocumento(dto.getDocumento());
		}
		return choferDAO.save(chofer);
	}

	private Cliente sincronizarCliente(ClienteDTO dto) {
		Cliente cliente = clienteDAO.findByExternalCode(dto.getExternalCode()).orElseGet(Cliente::new);
		cliente.setExternalCode(dto.getExternalCode());
		cliente.setRazonSocial(dto.getRazonSocial());
		if (dto.getContacto() != null) {
			cliente.setContacto(dto.getContacto());
		}
		return clienteDAO.save(cliente);
	}

	private Producto sincronizarProducto(ProductoDTO dto) {
		Producto producto = productoDAO.findByExternalCode(dto.getExternalCode()).orElseGet(Producto::new);
		producto.setExternalCode(dto.getExternalCode());
		producto.setNombre(dto.getNombre());
		if (dto.getDescripcion() != null) {
			producto.setDescripcion(dto.getDescripcion());
		}
		return productoDAO.save(producto);
	}
}
