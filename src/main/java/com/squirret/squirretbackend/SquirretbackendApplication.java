package com.squirret.squirretbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Squirret Backend Application - Guest Mode
 * 게스트 전용 모드로 동작하며 사용자 인증 없이 모든 기능을 사용할 수 있습니다.
 */
@SpringBootApplication(scanBasePackages = {"com.squirret.squirretbackend", "config"})
public class SquirretbackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SquirretbackendApplication.class, args);
	}

}
