package ar.edu.iua.iw3;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import ar.edu.iua.iw3.business.IProductBusiness;
import lombok.extern.slf4j.Slf4j;


@SpringBootApplication
@Slf4j
public class Iw3Application implements CommandLineRunner{

	public static void main(String[] args) {
		SpringApplication.run(Iw3Application.class, args);
		
	}

	@Autowired
	private IProductBusiness productService;
	
	@Override
	public void run(String... args) throws Exception {
		/*
		log.debug("==============================================================================================");
		log.debug(productService.list().toString());
		log.debug(productService.load(1).toString());
		log.debug(productService.load("Arroz").toString());
		log.debug(productService.load("Leche").toString());
		*/
	}

}
