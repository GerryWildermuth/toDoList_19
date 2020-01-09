package com.example.todoui;


import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import io.github.resilience4j.bulkhead.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;
import org.springframework.cloud.netflix.hystrix.dashboard.EnableHystrixDashboard;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.CircuitBreaker;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

@EnableRetry
@Controller
@SpringBootApplication
@EnableCircuitBreaker
@EnableHystrixDashboard
public class TodoUiApplication {

	@Value("${backend.host:localhost}")
	String backendHost;

	@Value("${backend.port:8080}")
	String backendPort;

	String endpoint;

	private Bulkhead bulkhead;
	private ThreadPoolBulkhead threadBulkhead;

	int i = 0;

	RestTemplate template = new RestTemplate();

	@PostConstruct
	private void initEndpoint(){

		endpoint = "http://"+backendHost+":"+backendPort;

	}
	@GetMapping(path = "")
	public String getItems(Model model)
	{
		String MethodName = "Standard";
		return getTodos(model, MethodName);
	}

	@GetMapping(path = "/Fallback")
	@HystrixCommand(fallbackMethod = "itemsFallback",
			commandProperties = {
			@HystrixProperty(name = "circuitBreaker.requestVolumeThreshold",value = "2"),
			@HystrixProperty(name = "circuitBreaker.sleepWindowInMilliseconds",value = "1000")}
	)
	public String getItemsWithFallback(Model model)
	{
		String MethodName = "Fallback";
		return getTodos(model, MethodName);
	}

	public String itemsFallback(Model model) {
		System.out.println("Fallback Fallback called" );
		String [] responseArray = new String[]{"Fallbackdata","more Fallbackdata","even more Fallbackdata"};
		model.addAttribute("items",responseArray);
		return "items";
	}

	@GetMapping(path = "/Retry")
	@Retryable(value = {Exception.class})
	public String retryService(Model model) throws Exception{
		String MethodName = "Retry";
		return getTodos(model, MethodName);
	}

	@Recover
	public String itemsRecovery(Model model) {
		System.out.println("Fallback Recovery called" );
		String [] responseArray = new String[]{"Fallbackdata","more Fallbackdata","even more Fallbackdata"};
		model.addAttribute("items",responseArray);
		return "items";
	}

	private String getTodos(Model model, String methodName) {
		System.out.println(" Invoking: " + endpoint + "/todos/ the " + i + " time from " + methodName);
		ResponseEntity<String[]> response = template.getForEntity(endpoint + "/todos/", String[].class);
		i++;
		model.addAttribute("items", response.getBody());

		return "items";
	}

	@GetMapping(path = "/Bulky")
	public String bulkyService(Model model) {
		System.out.println(" Invoking: "+endpoint +"/todos/ the "+i+" time from Bulky");
		i++;
		ResponseEntity<String[]> response;
		//Hystrix Version
		/*HystrixThreadPoolProperties.Setter()
				.withAllowMaximumSizeToDivergeFromCoreSize(true).withMaximumSize(2);

		response = template.getForEntity(endpoint + "/bulkyTodos/", String[].class);*/


		BulkheadConfig config = BulkheadConfig.custom().maxConcurrentCalls(2).maxWaitDuration(Duration.ofMillis(10))
				.build();
		BulkheadRegistry registry = BulkheadRegistry.of(config);
		bulkhead = registry.bulkhead("externalConcurrentService");

		ThreadPoolBulkheadConfig threadPoolBulkheadConfig = ThreadPoolBulkheadConfig.custom()
				.maxThreadPoolSize(4)
				.coreThreadPoolSize(2)
				.queueCapacity(4)
				.keepAliveDuration(Duration.ofMillis(200))
				.build();
		ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry = ThreadPoolBulkheadRegistry.of(threadPoolBulkheadConfig);
		threadBulkhead = threadPoolBulkheadRegistry.bulkhead("threadBulkhead");



		Supplier<ResponseEntity<String[]>> supplier = ()-> template.getForEntity(endpoint + "/bulkyTodos/", String[].class);
		CompletionStage<ResponseEntity<String[]>> responseEntityCompletionStage = threadBulkhead.executeSupplier(supplier);
		response = responseEntityCompletionStage.toCompletableFuture().join();


		//Einfache Lösung
		//Supplier<ResponseEntity<String[]>> supplier = ()-> template.getForEntity(endpoint + "/bulkyTodos/", String[].class);
		//response = bulkhead.executeSupplier(supplier);

		//SemaphorenBulkhead
		/*Supplier<ResponseEntity<String[]>> decoratedSupplier = Bulkhead
				.decorateSupplier(bulkhead, () -> template.getForEntity(endpoint + "/bulkyTodos/", String[].class));
		ResponseEntity<String[]> responseEntity = new ResponseEntity<>(new String[]{"Hello from recovery"}, HttpStatus.BAD_REQUEST);
		ResponseEntity<String[]> result = Try.ofSupplier(decoratedSupplier)
				.recover(throwable -> responseEntity).get();
		*/
		//ThreadPoolBulkhead
		//Easy

		//Complex
		/*Supplier<CompletionStage<ResponseEntity<String[]>>> decoratedSupplier = ThreadPoolBulkhead
				.decorateSupplier(threadBulkhead, () -> template.getForEntity(endpoint + "/bulkyTodos/", String[].class));
		ResponseEntity<String[]> responseEntity = new ResponseEntity<>(new String[]{"Hello from recovery"}, HttpStatus.BAD_REQUEST);
		CompletionStage<ResponseEntity<String[]>> result = Try.ofSupplier(decoratedSupplier)
				.recover(throwable -> responseEntity).get();*/



		model.addAttribute("items",response.getBody());

		return"items";
	}


	@GetMapping(path = "/BulkyCircuit")
	@HystrixCommand(threadPoolKey = "bulkyCircuitItems",
	threadPoolProperties = {
			@HystrixProperty(name = "coreSize",value = "2"),
			@HystrixProperty(name = "maxQueueSize",value = "2"),
			@HystrixProperty(name = "allowMaximumSizeToDivergeFromCoreSize",value = "true"),
			@HystrixProperty(name = "maximumSize",value = "2")
	})
	public String BulkyCircuit(Model model) {
		System.out.println(" Invoking: "+endpoint +"/todos/ the "+i+" time from BulkyCircuit");
		i++;
		ResponseEntity<String[]> response;

		response =template.getForEntity(endpoint + "/bulkyTodos/", String[].class);

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
		SpringApplication.run(TodoUiApplication.class, args);
	}

}
