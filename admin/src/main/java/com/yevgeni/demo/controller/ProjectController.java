package com.yevgeni.demo.controller;

import com.yevgeni.demo.model.Project;
import com.yevgeni.demo.model.User;
import com.yevgeni.demo.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.time.Duration;
import java.util.stream.Stream;

@RequiredArgsConstructor
@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService ProjectService;
    
    
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Project> create(@RequestBody Project Project){
        return ProjectService.createProject(Project);
    }

    @GetMapping
    public Flux<Project> getAllProjects(){
        return ProjectService.getAllProjects();
    }

    @GetMapping("/{ProjectId}")
    public Mono<ResponseEntity<Project>> getProjectById(@PathVariable String ProjectId){
        Mono<Project> Project = ProjectService.findById(ProjectId);
        return Project.map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/{ProjectId}")
    public Mono<ResponseEntity<Project>> updateProjectById(@PathVariable String ProjectId, @RequestBody Project Project){
        return ProjectService.updateProject(ProjectId,Project)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }
    
    @PutMapping("/adduser")
    public Mono<ResponseEntity<Project>> addProjectUser(@RequestParam("projectId") String projectId, @RequestParam("userId") String userId){
        return ProjectService.addProjectUser(projectId, userId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }

    @PutMapping("/removeuser")
    public Mono<ResponseEntity<Project>> removeProjectUser(@RequestParam("projectId") String projectId, @RequestParam("userId") String userId){
        return ProjectService.removeProjectUser(projectId, userId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }


    @DeleteMapping("/{ProjectId}")
    public Mono<ResponseEntity<Void>> deleteProjectById(@PathVariable String ProjectId){
        return ProjectService.deleteProject(ProjectId)
                .map( r -> ResponseEntity.ok().<Void>build())
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public Flux<Project> searchProjects(@RequestParam("name") String name) {
        return ProjectService.fetchProjects(name);
    }

}