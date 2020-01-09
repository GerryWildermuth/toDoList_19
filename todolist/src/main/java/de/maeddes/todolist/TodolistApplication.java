package de.maeddes.todolist;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import javax.persistence.Entity;
import javax.persistence.Id;

import io.github.resilience4j.bulkhead.*;
import io.vavr.CheckedRunnable;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@RestController
public class TodolistApplication {

	@Autowired
	TodoRepository todoRepository;

	private Bulkhead bulkhead;
	private ThreadPoolBulkhead threadBulkhead;

	RestTemplate template = new RestTemplate();

	private static Logger logger = LoggerFactory.getLogger("myTodoLogger");
	int i = 0;
	int k = 0;
	int u = 0;
	@GetMapping("/todos/")
	List<String> getTodos() {

		List<String> todos = new ArrayList<String>();

		Random random = new Random();
		int randomeNumber = random.nextInt(4);
		// for(Todo todo : todoRepository.findAll()) todos.add(todo.getTodo());
		logger.info("called TodoExternalService /todos/ the "+u+" time");
		u++;
		Random randomGenerator = new Random();
		int randomInt = randomGenerator.nextInt(6);
		if (randomInt < 4) {
			k++;
			throw new TodoServiceException("Service is the "+k+" time unavailable");
		}

		todoRepository.findAll().forEach(todo -> todos.add(todo.getTodo()));

		return todos;
	}
	@GetMapping("/saveTodos/")
	List<String> getSaveTodos() {
		List<String> todos = new ArrayList<String>();

		try {
			logger.info("called Save TodoExternalService /todos/ the "+u+" time");
			u++;
			todoRepository.findAll().forEach(todo -> todos.add(todo.getTodo()));

			Thread.sleep(100);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return todos;
	}

	@GetMapping("/bulkyTodos/")
	public List<String> callService() {
		List<String> todos = new ArrayList<String>();
		try {

			logger.info("called BulkyTodoExternalService /bulkyTodos/ the "+i+" time");
			i++;
			// Mock processing time of 2 seconds.


			BulkheadConfig config = BulkheadConfig.custom().maxConcurrentCalls(2).maxWaitDuration(Duration.ofMillis(10))
					.build();
			BulkheadRegistry registry = BulkheadRegistry.of(config);

			bulkhead = registry.bulkhead("externalConcurrentService");

			//Runnable runnable = ()-> todoRepository.findAll().forEach(todo -> todos.add(todo.getTodo()));
			//bulkhead.executeRunnable(runnable);

			//Runnable Runnable = Bulkhead.decorateRunnable(bulkhead, () -> todoRepository.findAll().forEach(todo -> todos.add(todo.getTodo())));
			//Try.runRunnable(Runnable).onFailure(Throwable::printStackTrace);

			//-----------------------------------------------------------------

			ThreadPoolBulkheadConfig threadPoolBulkheadConfig = ThreadPoolBulkheadConfig.custom()
					.maxThreadPoolSize(4)
					.coreThreadPoolSize(2)
					.queueCapacity(8)
					.keepAliveDuration(Duration.ofMillis(100))
					.build();

			ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry = ThreadPoolBulkheadRegistry.of(threadPoolBulkheadConfig);
			threadBulkhead = threadPoolBulkheadRegistry.bulkhead("threadBulkhead");

			Runnable runnable = ()-> todoRepository.findAll().forEach(todo -> todos.add(todo.getTodo()));
			threadBulkhead.executeRunnable(runnable);

			Thread.sleep(1000);



			Thread.sleep(1000);

			System.out.println(LocalTime.now() + " Call processing finished = " + Thread.currentThread().getName());
			return todos;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return todos;
	}

	@SuppressWarnings("serial")
	class TodoServiceException extends RuntimeException {

		public TodoServiceException(String message) {
			super(message);
		}
		@Override
		public synchronized Throwable fillInStackTrace() {
			return this;
		}
	}
	@PostMapping("/todos/{todo}")
	String addTodo(@PathVariable String todo) {

		todoRepository.save(new Todo(todo));
		return "added " + todo;
	}

	@DeleteMapping("/todos/{todo}")
	String removeTodo(@PathVariable String todo) {

		todoRepository.deleteById(todo);
		return "removed " + todo;

	}

	public static void main(String[] args) {
		SpringApplication.run(TodolistApplication.class, args);
	}

}

@Entity
class Todo{

	@Id
	String todo;

	public Todo() {
	}

	public Todo(String todo) {
		this.todo = todo;
	}

	public String getTodo() {
		return todo;
	}

	public void setTodo(String todo) {
		this.todo = todo;
	}

}

@RepositoryRestResource(path = "todo-hal")
interface TodoRepository extends CrudRepository<Todo, String> {

}