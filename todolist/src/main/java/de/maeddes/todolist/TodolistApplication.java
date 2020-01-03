package de.maeddes.todolist;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.persistence.Entity;
import javax.persistence.Id;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class TodolistApplication {

	@Autowired
	TodoRepository todoRepository;

	private static Logger logger = LoggerFactory.getLogger("myTodoLogger");


	@GetMapping("/todos/")
	List<String> getTodos() {

		List<String> todos = new ArrayList<String>();
		int i = 0;
		int k = 0;

		Random random = new Random();
		int randomeNumber = random.nextInt(4);
		// for(Todo todo : todoRepository.findAll()) todos.add(todo.getTodo());
		logger.info("called TodoExternalService /todos/ the "+i+" time");
		i++;
		Random randomGenerator = new Random();
		int randomInt = randomGenerator.nextInt(6);
		if (randomInt < 4) {
			k++;
			throw new TodoServiceException("Service is the "+k+" time unavailable");
		}

		todoRepository.findAll().forEach(todo -> todos.add(todo.getTodo()));

		return todos;
	}
	@GetMapping("/bulkyTodos/")
	public List<String> callService() {
		List<String> todos = new ArrayList<String>();
		try {
			int i = 0;
			logger.info("called BulkyTodoExternalService /bulkyTodos/ the "+i+" time");
			i++;
			// Mock processing time of 2 seconds.
			Thread.sleep(2000);
			todoRepository.findAll().forEach(todo -> todos.add(todo.getTodo()));


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