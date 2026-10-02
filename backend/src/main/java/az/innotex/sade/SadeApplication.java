package az.innotex.sade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(considerNestedRepositories = true)
public class SadeApplication {
    public static void main(String[] args) {
        SpringApplication.run(SadeApplication.class, args);
    }
}
