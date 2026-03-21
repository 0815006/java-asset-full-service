package com.asset.service.impl;

import com.asset.entity.User;
import com.asset.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Assuming username here is the userId as a String
        Long userId = Long.parseLong(username);
        User user = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getId, userId));

        if (user == null) {
            throw new UsernameNotFoundException("User not found with id: " + username);
        }

        // Spring Security User object expects username, password, and authorities
        // For simplicity, we are using userId as username and an empty password
        // In a real application, you would fetch the actual password hash and roles/authorities
        return new org.springframework.security.core.userdetails.User(
                String.valueOf(user.getId()), // Using userId as username
                "", // Password is not used for token authentication in this filter
                new ArrayList<>() // No roles/authorities for simplicity, add if needed
        );
    }
}
