package com.ecommerce.engine.service;

import com.ecommerce.engine.entity.AppUser;
import com.ecommerce.engine.exception.ResourceNotFoundException;
import com.ecommerce.engine.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserLookupService {

    private final AppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public AppUser getRequiredUser(String username) {
        return appUserRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }
}
