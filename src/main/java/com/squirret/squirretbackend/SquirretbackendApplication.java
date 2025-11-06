package com.squirret.squirretbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;

@SpringBootApplication(exclude = {
    SecurityAutoConfiguration.class,
    OAuth2ClientAutoConfiguration.class
})
public class SquirretbackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SquirretbackendApplication.class, args);
	}

}
