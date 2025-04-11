# Recruitment Automation System

A Spring Boot backend application for automating the recruitment process, featuring applicant management, CV analysis with AI, automated testing, candidate ranking, and an admin panel for recruiters.

## Features

- **User Management**: Registration, login, and role-based access control (Admin, Recruiter, Applicant)
- **Applicant Management**: Store and manage applicant profiles, CVs, and skills
- **AI-Powered CV Analysis**: Analyze CVs using Hugging Face AI models
- **Automated Testing**: Create and manage tests, automatically grade responses
- **Candidate Ranking**: Rank candidates based on test results and AI analysis
- **Security**: JWT-based authentication and authorization

## Technology Stack

- **Backend**: Spring Boot 3.x
- **Database**: MySQL
- **Security**: Spring Security with JWT
- **Documentation**: Swagger/OpenAPI
- **AI Integration**: Hugging Face API
- **Object Mapping**: ModelMapper
- **HTTP Client**: OkHttp

## Project Structure

The project follows a layered architecture:

- **Controller Layer**: REST API endpoints
- **Service Layer**: Business logic
- **Repository Layer**: Data access
- **Model Layer**: Entity classes
- **DTO Layer**: Data Transfer Objects
- **Security Layer**: Authentication and authorization
- **Exception Handling**: Centralized error handling
- **Configuration**: Application configuration

## API Documentation

API documentation is available via Swagger UI at `/swagger-ui.html` when the application is running.

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven
- MySQL

### Configuration

Update the `application.properties` file with your database and Hugging Face API credentials:

```properties
# Database Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/recruitment_db
spring.datasource.username=your_username
spring.datasource.password=your_password

# JWT Configuration
jwt.secret=your_jwt_secret_key
jwt.expiration=86400000

# Hugging Face API
huggingface.api.url=https://api-inference.huggingface.co/models/
huggingface.api.token=your_huggingface_token
```

### Running the Application

```bash
mvn spring-boot:run
```

## API Endpoints

### Authentication
- `POST /api/auth/register`: Register a new user
- `POST /api/auth/login`: Login and get JWT token

### Users
- `GET /api/users`: Get all users (Admin only)
- `GET /api/users/{id}`: Get user by ID
- `PUT /api/users/{id}`: Update user
- `DELETE /api/users/{id}`: Delete user (Admin only)

### Applicants
- `GET /api/applicants`: Get all applicants
- `GET /api/applicants/{id}`: Get applicant by ID
- `POST /api/applicants/user/{userId}`: Create applicant profile
- `PUT /api/applicants/{id}`: Update applicant
- `POST /api/applicants/{id}/cv`: Upload CV
- `DELETE /api/applicants/{id}`: Delete applicant (Admin only)

### Tests
- `GET /api/tests`: Get all tests
- `GET /api/tests/{id}`: Get test by ID
- `POST /api/tests`: Create a new test
- `PUT /api/tests/{id}`: Update test
- `DELETE /api/tests/{id}`: Delete test
- `POST /api/tests/{testId}/questions`: Add question to test
- `POST /api/tests/{testId}/generate-questions`: Generate AI questions

### Test Results
- `GET /api/test-results`: Get all test results
- `GET /api/test-results/{id}`: Get test result by ID
- `POST /api/test-results/start`: Start a test
- `PUT /api/test-results/{testResultId}/submit`: Submit test answers
- `PUT /api/test-results/{testResultId}/review`: Review test result

## License

This project is licensed under the MIT License.
