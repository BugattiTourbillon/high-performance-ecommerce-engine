package com.ecommerce.engine.service;

import com.ecommerce.engine.dto.auth.AuthResponse;
import com.ecommerce.engine.dto.auth.LoginRequest;
import com.ecommerce.engine.dto.auth.RegisterRequest;
import com.ecommerce.engine.entity.AppUser;
import com.ecommerce.engine.entity.Cart;
import com.ecommerce.engine.entity.RoleName;
import com.ecommerce.engine.exception.DuplicateResourceException;
import com.ecommerce.engine.repository.AppUserRepository;
import com.ecommerce.engine.repository.CartRepository;
import com.ecommerce.engine.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ecommerce.engine.exception.AuthenticationFailedException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (appUserRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username is already taken");
        }
        if (appUserRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email is already registered");
        }

        AppUser user = new AppUser();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(RoleName.CUSTOMER);
        AppUser savedUser = appUserRepository.save(user);

        Cart cart = new Cart();
        cart.setUser(savedUser);
        cartRepository.save(cart);

        UserDetails userDetails = userDetailsService.loadUserByUsername(savedUser.getUsername());
        return new AuthResponse(jwtService.generateToken(userDetails), savedUser.getUsername(), savedUser.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
            UserDetails principal = (UserDetails) authentication.getPrincipal();
            String role = principal.getAuthorities().stream()
                .findFirst()
                .map(Object::toString)
                .map(authority -> authority.replace("ROLE_", ""))
                .orElse(RoleName.CUSTOMER.name());
            return new AuthResponse(jwtService.generateToken(principal), principal.getUsername(), role);
        } catch (AuthenticationException ex) {
            throw new AuthenticationFailedException("Invalid username or password");
        }
    }
}
