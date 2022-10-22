package com.yevgeni.api.controller;

import com.yevgeni.api.model.*;
import com.yevgeni.api.service.*;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Project> create(@RequestBody Project Project){
        return projectService.createProject(Project);
    }

    @GetMapping
    public Flux<Project> getAllProjects(){
        return projectService.getAllProjects();
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Project>> getProjectById(@PathVariable String id){
        Mono<Project> Project = projectService.findById(id);
        return Project.map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Project>> updateProjectById(@PathVariable String id, @RequestBody Project Project){
        return projectService.updateProject(id,Project)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }

    @PutMapping("/adduser")
    public Mono<ResponseEntity<Project>> addProjectUser(@RequestParam("projectId") String projectId, @RequestParam("userId") String userId){
        return projectService.addProjectUser(projectId, userId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }

    @PutMapping("/removeuser")
    public Mono<ResponseEntity<Project>> removeProjectUser(@RequestParam("projectId") String projectId, @RequestParam("userId") String userId){
        return projectService.removeProjectUser(projectId, userId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }
    
    
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteProjectById(@PathVariable String id){
        return projectService.deleteProject(id)
                .map( r -> ResponseEntity.ok().<Void>build())
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public Flux<Project> searchProjects(@RequestParam("name") String name) {
        return projectService.fetchProjects(name);
    }


}