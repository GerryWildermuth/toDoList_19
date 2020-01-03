package com.example.todoui;


import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;

@SpringBootApplication
@Controller
@EnableCircuitBreaker
@EnableRetry
public class TodouiApplication {

	@Value("${backend.host:localhost}")
	String backendHost;

	@Value("${backend.port:8080}")
	String backendPort;

	String endpoint;

	private Bulkhead bulkhead;

	int i = 0;

	RestTemplate template = new RestTemplate();

	@PostConstruct
	private void initEndpoint(){

		endpoint = "http://"+backendHost+":"+backendPort;

	}

	@GetMapping
	@HystrixCommand(fallbackMethod = "itemsFallback")
	public String getItems(Model model)
	{
		System.out.println(" Invoking: "+endpoint +"/todos/");
		ResponseEntity<String[]> response = null;
		response = template.getForEntity(endpoint + "/todos/", String[].class);

		model.addAttribute("items",response.getBody());

		return"items";
	}

	public String itemsFallback(Model model) {
		System.out.println("Fallback called" );
		String [] responseArray = new String[]{"Fallbackdata","more Fallbackdata","even more Fallbackdata"};
		model.addAttribute("items",responseArray);
		return "items";
	}

	@GetMapping(path = "/Retry")
	@HystrixCommand
	@Retryable(value = {Exception.class})
	public String retryService(Model model) throws Exception{
		System.out.println(" Invoking: "+endpoint +"/todos/ the "+i+" time from RETRY");

		ResponseEntity<String[]> response = null;
		response = template.getForEntity(endpoint + "/todos/", String[].class);
		i++;
		model.addAttribute("items",response.getBody());

		return"items";
	}

	@Recover
	public String itemsRecovery(Model model) {
		System.out.println("Fallback called" );
		String [] responseArray = new String[]{"Fallbackdata","more Fallbackdata","even more Fallbackdata"};
		model.addAttribute("items",responseArray);
		return "items";
	}
	@GetMapping(path = "/Bulky")
	//@HystrixCommand
	public String bulkyService(Model model) {
		System.out.println(" Invoking: "+endpoint +"/todos/ the "+i+" time from RETRY");
		BulkheadConfig config = BulkheadConfig.custom().maxConcurrentCalls(5).maxWaitDuration(Duration.ofMillis(5000))
				.build();
		BulkheadRegistry registry = BulkheadRegistry.of(config);

		bulkhead = registry.bulkhead("externalConcurrentService");

		ResponseEntity<String[]> response = null;
		Runnable runnable = ()-> template.getForEntity(endpoint + "/bulkyTodos/", String[].class);
		bulkhead.executeRunnable(runnable);
		i++;
		model.addAttribute("items",response.getBody());

		return"items";
	}

	@PostMapping
	public String addItem(String toDo) {

		try {
			
			template.postForEntity(endpoint + "/todos/" + toDo, null, String.class);
		
		} catch (Exception e) {

			System.out.println(" POST failed ");
			
		}
		return "redirect:/";

	}

	@PostMapping("{toDo}")
	public String setItemDone(@PathVariable String toDo) {

		try {

			template.delete(endpoint + "/todos/" + toDo);

		} catch (Exception e) {

			System.out.println(" DELETE failed ");

		}
		return "redirect:/";

	}

	

	public static void main(String[] args) {
		SpringApplication.run(TodouiApplication.class, args);
	}

}

