package com.example.dbexplorer.controller;

import com.example.dbexplorer.config.AppProperties.User;
import com.example.dbexplorer.service.AuthService;
import com.example.dbexplorer.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService userService;
    private final AuthService auth;

    public AdminController(UserService userService, AuthService auth) {
        this.userService = userService;
        this.auth = auth;
    }

    private void requireAdmin(HttpServletRequest req) {
        User u = auth.effectiveUser(req);
        if (u == null || !"ADMIN".equalsIgnoreCase(u.getRole()))
            throw new SecurityException("Admin access required.");
    }

    @GetMapping("/users")
    public List<Map<String,Object>> listUsers(HttpServletRequest req) {
        requireAdmin(req);
        return userService.listUsers();
    }

    @GetMapping("/user/{username}")
    public Map<String,Object> getUserDetails(@PathVariable String username, HttpServletRequest req) {
        requireAdmin(req);
        return userService.getUserDetails(username);
    }

    @PostMapping("/user")
    @SuppressWarnings("unchecked")
    public void saveUser(@RequestBody Map<String,Object> body, HttpServletRequest req) {
        requireAdmin(req);
        userService.saveUser(
            (String) body.get("username"),
            (String) body.get("password"),
            (String) body.get("role"),
            (List<String>) body.get("privileges"),
            (List<String>) body.get("allowedTables"),
            (List<Map<String,String>>) body.get("filters")
        );
    }

    @DeleteMapping("/user/{username}")
    public void deleteUser(@PathVariable String username, HttpServletRequest req) {
        requireAdmin(req);
        userService.deleteUser(username);
    }
}
