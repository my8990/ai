package com.example.ai.security;

public record LoginUser(Long id, String username, String role) {

    public static final String REQUEST_ATTRIBUTE = "loginUser";

    public boolean isAdmin() {
        return Roles.ADMIN.equals(role);
    }

    public boolean isTeacher() {
        return Roles.TEACHER.equals(role);
    }

    public boolean isStudent() {
        return Roles.STUDENT.equals(role);
    }
}