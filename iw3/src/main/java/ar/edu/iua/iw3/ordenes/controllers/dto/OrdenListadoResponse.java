package ar.edu.iua.iw3.ordenes.controllers.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Estructura de paginacion explicita y documentada (TP31, F7):
 * a proposito NO se serializa un Page de Spring Data directamente (su
 * formato interno no es un contrato estable ni documentado para
 * OpenAPI) - esta clase es el contrato real, fijo.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrdenListadoResponse {
	private List<OrdenResumenResponse> content;
	private int page;
	private int size;
	private long totalElements;
	private int totalPages;
}
