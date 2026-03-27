package com.grasshopper.prism;

import com.grasshopper.prism.config.SearchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SearchProperties.class)
public class PrismApplication {

	public static void main(String[] args) {
		SpringApplication.run(PrismApplication.class, args);
	}

}
