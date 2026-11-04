package com.imohealth.lifescience.axon.pubmed_client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PubMedProperties.class)
public class PubmedClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(PubmedClientApplication.class, args);
	}

}
