package com.yevgeni.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.yevgeni.api.model.Project;
import com.yevgeni.api.model.User;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor

public class ProjectService {

	private static final String PROJECT_AGENT_SERVICE_URL = "http://admin:9010/admin/projects";
	
    @Autowired
    private WebClient webClient;

	
    public Mono<Project> createProject(Project project){
    	return webClient.post()
                .uri(PROJECT_AGENT_SERVICE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(project))
                .retrieve()
                .bodyToMono(Project.class);

    }

    public Flux<Project> getAllProjects(){
    	return webClient.get()
                .uri(PROJECT_AGENT_SERVICE_URL)
                .retrieve()
                .bodyToFlux(Project.class);

    }

    public Mono<Project> findById(String id){
    	return webClient.get()
                .uri(PROJECT_AGENT_SERVICE_URL+'/'+id)
                .retrieve()
                .bodyToMono(Project.class);

    }

    public Mono<Project> updateProject(String id,  Project project){
    	return webClient.put()
                .uri(PROJECT_AGENT_SERVICE_URL+'/'+id)
                .body(BodyInserters.fromValue(project))
                .retrieve()
                .bodyToMono(Project.class);
    }
    
    public Mono<Project> addProjectUser(String projectId,  String userId){
    	return webClient.put()
                .uri(PROJECT_AGENT_SERVICE_URL+"/adduser?projectId="+projectId+"&userId="+userId)
                .retrieve()
                .bodyToMono(Project.class);
    }

    public Mono<Project> removeProjectUser(String projectId,  String userId){
    	return webClient.put()
                .uri(PROJECT_AGENT_SERVICE_URL+"/removeuser?projectId="+projectId+"&userId="+userId)
                .retrieve()
                .bodyToMono(Project.class);
    }


    public Mono<Project> deleteProject(String id){
    	return webClient.delete()
                .uri(PROJECT_AGENT_SERVICE_URL+'/'+id)
                .retrieve()
                .bodyToMono(Project.class);

    }

    public Flux<Project> fetchProjects(String name) {
    	return webClient.get()
                .uri(PROJECT_AGENT_SERVICE_URL+"/search")
                .attribute("name", name)
                .retrieve()
                .bodyToFlux(Project.class);
    }

	public Mono<Project> addProductUser(String id, User user) {
    	return webClient.put()
                .uri(PROJECT_AGENT_SERVICE_URL+"/adduser/"+id)
                .body(BodyInserters.fromValue(user))
                .retrieve()
                .bodyToMono(Project.class);

	}

    
}