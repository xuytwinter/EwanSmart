package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.AppAuthProperties;
import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.RegistrationMode;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.OrganizationTagRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.utils.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationTagRepository organizationTagRepository;

    @Mock
    private OrgTagCacheService orgTagCacheService;

    @Mock
    private AppAuthProperties appAuthProperties;

    @Mock
    private AppAuthProperties.Registration registration;

    @Mock
    private InviteCodeService inviteCodeService;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(appAuthProperties.getRegistration()).thenReturn(registration);
        when(registration.getMode()).thenReturn(RegistrationMode.OPEN);
        when(registration.isInviteRequired()).thenReturn(false);
    }

    @Test
    void testRegisterUserSuccessWhenOpenRegistration() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(organizationTagRepository.existsByTagId("DEFAULT")).thenReturn(true);
        when(organizationTagRepository.existsByTagId("PRIVATE_testuser")).thenReturn(false);

        userService.registerUser("testuser", "password123", null);

        verify(userRepository, atLeastOnce()).save(any(User.class));
        verify(inviteCodeService, never()).consume(anyString(), anyString());
    }

    @Test
    void testRegisterUserClosed() {
        when(registration.getMode()).thenReturn(RegistrationMode.CLOSED);

        CustomException exception = assertThrows(CustomException.class,
                () -> userService.registerUser("testuser", "password123", null));

        assertEquals("REGISTRATION_CLOSED", exception.getMessage());
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void testRegisterUserInviteRequired() {
        when(registration.getMode()).thenReturn(RegistrationMode.INVITE_ONLY);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(organizationTagRepository.existsByTagId("DEFAULT")).thenReturn(true);
        when(organizationTagRepository.existsByTagId("PRIVATE_testuser")).thenReturn(false);

        userService.registerUser("testuser", "password123", "INVITE-001");

        verify(inviteCodeService, times(1)).consume("INVITE-001", "testuser");
    }

    @Test
    void testRegisterUserUsernameExists() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(new User()));

        CustomException exception = assertThrows(CustomException.class,
                () -> userService.registerUser("testuser", "password123", null));

        assertEquals("Username already exists", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void testAuthenticateUserSuccess() {
        String rawPassword = "password123";
        String encodedPassword = PasswordUtil.encode(rawPassword);

        User user = new User();
        user.setUsername("testuser");
        user.setPassword(encodedPassword);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        String username = userService.authenticateUser("testuser", rawPassword);
        assertEquals("testuser", username);
    }

    @Test
    void testAuthenticateUserInvalidCredentials() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class,
                () -> userService.authenticateUser("testuser", "wrongpassword"));

        assertEquals("Invalid username or password", exception.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void testEnsureDefaultOrgRequiresAdminWhenMissing() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(registration.getMode()).thenReturn(RegistrationMode.OPEN);
        when(registration.isInviteRequired()).thenReturn(false);

        when(organizationTagRepository.existsByTagId("DEFAULT")).thenReturn(false);
        when(userRepository.findAll()).thenReturn(List.of());

        CustomException exception = assertThrows(CustomException.class,
                () -> userService.registerUser("testuser", "password123", null));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatus());
    }
}
