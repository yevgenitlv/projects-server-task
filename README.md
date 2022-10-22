# projects-server-task

Dockers for micro-servers


Spring Boot
Webflux
Reactive MongoDB

A micro-services server that allows to create projects that contains multi-users.

1. API Service
An external API server that recieves the requests from the client (API Controller listens to port 9020)
2. Agent Service
A micro-service responsible for all the business logic of the application.
Responsible for saving entities on Mongo DB

API operations:
User:
{
userId: String  (may serve as a login as far as it is unique value)
name: String
password: String
}
Project:
{
name :String
status :boolean
}

User CRUD operations:
1. Retrieve all users:
GET: /api/users
2. Retrieve user by id:
GET: /api/users/{userId}
3. Update user by id:
PUT: /api/users/{userId}
     {User DTO as request body}
4. Delete user:
DELETE: /api/users/{userId}
5. Serach users by name:
GET: /api/users/search
     Parameters: name;

Project CRUD operations:

1. Retreive all projects:
GET: /api/projects
2. Retrieve project by id:
GET: /api/projects/{id}
3. Update project:
PUT: /api/projects/{id} 
      {Project DTO as request body}
4. Delete project:
DELETE: /api/projects/{id}
5. Serach projects by name:
GET: /api/projects/search
     Parameters: name;
6. Add user to project:
PUT: /api/adduser
     Parameters: projectId;
                 userId;
7. Remove user from project:
PUT: /api/removeuser
     Parameters: projectId;
                 userId;
                 
                 
                 
                 
