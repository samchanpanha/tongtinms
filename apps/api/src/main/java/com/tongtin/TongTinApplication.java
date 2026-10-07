package com.tongtin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TongTinApplication {

    public static void main(String[] args) {
        SpringApplication.run(TongTinApplication.class, args);
    }
}
