package com.elekeza.backend.teacher

import com.elekeza.backend.auth.*
import com.elekeza.backend.learner.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/teacher")
class TeacherController(
    private val userRepo: UserRepository,
    private val learnerProfileRepo: LearnerProfileRepository,
    private val lessonProgressRepo: LessonProgressRepository
) {
    @GetMapping("/students")
    fun getStudents(): ResponseEntity<List<StudentDto>> {
        val students = userRepo.findAll().filter { it.role == UserRole.STUDENT }
        val dtos = students.map { s ->
            val profile = learnerProfileRepo.findByUser(s)
            StudentDto(s.id.toString(), s.name, profile?.sneType?.name ?: "NONE")
        }
        return ResponseEntity.ok(dtos)
    }

    @PostMapping("/student")
    fun createStudent(@RequestBody req: CreateStudentRequest): ResponseEntity<StudentDto> {
        val student = User(
            name = req.fullName,
            email = req.email,
            password = req.password,
            role = UserRole.STUDENT
        )
        userRepo.save(student)
        return ResponseEntity.ok(StudentDto(student.id.toString(), student.name, req.sneType))
    }

    @GetMapping("/student/{studentId}/progress")
    fun getStudentProgress(@PathVariable studentId: Long): ResponseEntity<StudentProgressDto> {
        val student = userRepo.findById(studentId).orElseThrow()
        val progress = lessonProgressRepo.findByUser(student)
        val completedCount = progress.count { it.completed }
        val lastQuiz = progress.lastOrNull { it.quizScore != null }
        return ResponseEntity.ok(StudentProgressDto(student.name, completedCount, lastQuiz?.quizScore))
    }
}

data class CreateStudentRequest(val email: String, val fullName: String, val password: String, val sneType: String)
data class StudentDto(val id: String, val name: String, val sneType: String)
data class StudentProgressDto(val studentName: String, val completedLessons: Int, val lastQuizScore: Double?)
