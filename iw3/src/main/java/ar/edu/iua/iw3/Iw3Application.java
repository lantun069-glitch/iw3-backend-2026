package ar.edu.iua.iw3;

import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

import ar.edu.iua.iw3.business.IProductBusiness;
import lombok.extern.slf4j.Slf4j;


@SpringBootApplication
@Slf4j
public class Iw3Application extends SpringBootServletInitializer implements CommandLineRunner{

	public static void main(String[] args) {
		SpringApplication.run(Iw3Application.class, args);
		
	}

	@Autowired
	private IProductBusiness productService;
	
	@Value("${spring.profiles.active}")
	private String profile;

	@Value("${spring.jackson.time-zone:-}")
	private String backendTimezone;

	
	@Override
	public void run(String... args) throws Exception {
		String tzId = backendTimezone.equals("-") ? TimeZone.getDefault().getID() : backendTimezone;
		TimeZone.setDefault(TimeZone.getTimeZone(tzId));
		
		log.info("-------------------------------------------------------------------------------------------------------------------");
		log.info("- Initial TimeZone: {} ({})", TimeZone.getDefault().getDisplayName(), TimeZone.getDefault().getID());
		log.info("- Perfil activo {}",profile);
		log.info("-------------------------------------------------------------------------------------------------------------------");


		/*
		log.debug("==============================================================================================");
		log.debug(productService.list().toString());
		log.debug(productService.load(1).toString());
		log.debug(productService.load("Arroz").toString());
		log.debug(productService.load("Leche").toString());
		*/
	}

}
