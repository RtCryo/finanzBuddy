package com.finanzbuddy.parser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class ParserServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ParserServiceApplication.class, args);
	}

}
