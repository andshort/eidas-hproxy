package eu.europa.ec.healtheid.eidashproxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({"eu.eidas.sp", "eu.europa.ec.healtheid.eidashproxy"})
public class EidasHproxyApplication {

	public static void main(String[] args) {
		SpringApplication.run(EidasHproxyApplication.class, args);
	}

}
