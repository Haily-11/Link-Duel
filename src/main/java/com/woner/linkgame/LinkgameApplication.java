package com.woner.linkgame;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LinkgameApplication {

	public static void main(String[] args) {
		SpringApplication.run(LinkgameApplication.class, args);
	}

}
