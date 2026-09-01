package ar.edu.iua.iw3.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iua.iw3.business.IProductBusiness;
import ar.edu.iua.iw3.business.execption.BusinessException;
import ar.edu.iua.iw3.util.IStandartResponseBusiness;

@RestController
@RequestMapping(Constants.URL_PRODUCTS)
public class ProductRestController extends BaseRestController {
	
	@Autowired
	private IProductBusiness productBusiness;
	
	@Autowired
	private IStandartResponseBusiness response;
	
	@GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> list() {
		try {
			return new ResponseEntity<>(productBusiness.list(), HttpStatus.OK);
		} catch (BusinessException e) {
			return new ResponseEntity<>(response.build(HttpStatus.INTERNAL_SERVER_ERROR, e, e.getMessage()),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}


}
