package com.example.todoui;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.PostConstruct;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

@RestController()
public class resilienceController {


    @Value("${backend.host:localhost}")
    String backendHost;

    @Value("${backend.port:8080}")
    String backendPort;

    String endpoint;

    int i = 0;

    RestTemplate template = new RestTemplate();
    @PostConstruct
    private void initEndpoint(){

        endpoint = "http://"+backendHost+":"+backendPort;

    }

    @CircuitBreaker(name="BACKEND",fallbackMethod = "firstFallback")
    @RateLimiter(name="BACKEND")
    @Retry(name="BACKEND",fallbackMethod = "secondFallback")
    @GetMapping(path = "resilience")
    @Bulkhead(name="BACKEND",type = Bulkhead.Type.THREADPOOL)
    public CompletableFuture<ModelAndView> getItems()
    {
        String MethodName = "Standard";
        return CompletableFuture.completedFuture(getTodos(MethodName));
    }
    public CompletableFuture<ModelAndView> firstFallback(HttpServerErrorException.InternalServerError e) {
        ModelAndView modelAndView = new ModelAndView();
        System.out.println("first Fallback called" );
        modelAndView.addObject("items",new String[]{"Fallbackdata","more Fallbackdata","even more Fallbackdata"});
        modelAndView.setViewName("items");
        return CompletableFuture.completedFuture(modelAndView);
    }
    public CompletableFuture<ModelAndView> secondFallback(CompletionException e) {
        ModelAndView modelAndView = new ModelAndView();
        System.out.println("second Fallback called" );
        modelAndView.addObject("items",new String[]{"Fallbackdata","more Fallbackdata","even more Fallbackdata"});
        modelAndView.setViewName("items");
        return CompletableFuture.completedFuture(modelAndView);
    }





    private ModelAndView getTodos(String methodName) {
        ModelAndView modelAndView = new ModelAndView();
        System.out.println(" Invoking: " + endpoint + "/todos/ the " + i + " time from " + methodName);
        i++;
        ResponseEntity<String[]> response;
        Supplier<ResponseEntity<String[]>> supplier = ()-> template.getForEntity(endpoint + "/todos/", String[].class);
        ResponseEntity<String[]> responseEntity = CompletableFuture.completedFuture(supplier).join().get();

        modelAndView.addObject("items",responseEntity.getBody());
        modelAndView.setViewName("items");


        return modelAndView;
    }

}
