package ml.dibidani.service;

import ml.dibidani.api.dto.UserDtos.*;
import ml.dibidani.common.ConflictException;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.domain.*;
import ml.dibidani.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourierManagementTest {
  private final AppUserRepository users = mock(AppUserRepository.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final UserService service = new UserService(users, encoder);

  @Test void createsOnlyCourierAccountsWithEncodedPasswords() {
    when(encoder.encode("Password123!")).thenReturn("encoded");
    when(users.save(any(AppUser.class))).thenAnswer(invocation -> {
      AppUser user = invocation.getArgument(0);
      assertEquals("encoded", user.getPasswordHash());
      user.setId(5L); return user;
    });
    var result = service.createCourier(new CreateCourierRequest(" Amadou ", " AMADOU@example.com ", "+223 70000000", "Password123!"));
    assertEquals(Role.COURIER, result.role());
    assertEquals("Amadou", result.fullName());
    assertEquals("amadou@example.com", result.email());
    assertTrue(result.active());
  }

  @Test void rejectsDuplicateEmail() {
    when(users.existsByEmailIgnoreCase("a@example.com")).thenReturn(true);
    assertThrows(ConflictException.class, () -> service.createCourier(new CreateCourierRequest("A", "a@example.com", "+223 70000000", "Password123!")));
    verify(users, never()).save(any());
  }

  @Test void courierEndpointCannotChangeOtherRoles() {
    for (Role role : new Role[]{Role.ADMIN, Role.MANAGER, Role.CUSTOMER, Role.SELLER}) {
      var user = new AppUser(); user.setRole(role);
      when(users.findById(1L)).thenReturn(Optional.of(user));
      assertThrows(NotFoundException.class, () -> service.updateCourier(1L, new UpdateCourierRequest("Changed", "+223 70000000", false, null), "manager@example.com"));
      assertEquals(role, user.getRole()); assertTrue(user.isActive());
    }
  }

  @Test void deactivationRevokesExistingTokensWithoutChangingRole() {
    var user = new AppUser(); user.setRole(Role.COURIER); user.setEmail("c@example.com"); user.setPasswordHash("unchanged");
    when(users.findById(1L)).thenReturn(Optional.of(user));
    service.updateCourier(1L, new UpdateCourierRequest("Courier", "+223 70000000", false, null), "manager@example.com");
    assertFalse(user.isActive()); assertEquals(1, user.getTokenVersion());
    assertEquals(Role.COURIER, user.getRole()); assertEquals("unchanged", user.getPasswordHash());
  }
}
