package com.yevgeni.demo.service;

import com.yevgeni.demo.model.Project;
import com.yevgeni.demo.model.User;
import com.yevgeni.demo.repository.ProjectRepository;
import com.yevgeni.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ProjectService {

    private final ReactiveMongoTemplate reactiveMongoTemplate;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public Mono<Project> createProject(Project project){
        return projectRepository.save(project);
    }

    public Flux<Project> getAllProjects(){
        return projectRepository.findAll();
    }

    public Mono<Project> findById(String projectId){
        return projectRepository.findById(projectId);
    }

    public Mono<Project> updateProject(String projectId,  Project project){
        return projectRepository.findById(projectId)
                .flatMap(dbProject -> {
                    dbProject.setName(project.getName());
                    dbProject.setStatus(project.isStatus());
                    return projectRepository.save(dbProject);
                });
    }
    
    public Mono<Project> addProjectUser(String projectId,  String userId){
    	
        		    return projectRepository.findById(projectId)
        			.flatMap(dbProject -> {
                    List <String> userIds = dbProject.getUserIds();
                    if (userIds==null) {userIds=new ArrayList<String>(0);}
                    userIds.add(userId);
                    dbProject.setUserIds(userIds);
                    return projectRepository.save(dbProject);
                });
    }

    public Mono<Project> removeProjectUser(String projectId,  String userId){
        return projectRepository.findById(projectId)
                .flatMap(dbProject -> {
                    List <String> userIds = dbProject.getUserIds();
                    if (userIds!=null) {userIds.remove(userId);}
                    dbProject.setUserIds(userIds);
                    return projectRepository.save(dbProject);
                });
    }


    public Mono<Project> deleteProject(String projectId){
        return projectRepository.findById(projectId)
                .flatMap(existingProject -> projectRepository.delete(existingProject)
                        .then(Mono.just(existingProject)));
    }

    public Flux<Project> fetchProjects(String name) {
        Query query = new Query()
                .with(Sort
                        .by(Collections.singletonList(Sort.Order.asc("id")))
                );
        query.addCriteria(Criteria
                .where("name")
                .regex(name)
        );

        return reactiveMongoTemplate
                .find(query, Project.class);
    }
}