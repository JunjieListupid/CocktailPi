package net.alex9849.cocktailpi.endpoints;

import jakarta.validation.Valid;
import net.alex9849.cocktailpi.model.user.ERole;
import net.alex9849.cocktailpi.model.user.User;
import net.alex9849.cocktailpi.payload.dto.user.UserDto;
import net.alex9849.cocktailpi.payload.request.UpdateUserRequest;
import net.alex9849.cocktailpi.service.IngredientService;
import net.alex9849.cocktailpi.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user/")
public class UserEndpoint {

    @Autowired
    UserService userService;

    @Autowired
    IngredientService ingredientService;

    @PreAuthorize("hasRole('ADMIN')")
    @RequestMapping(value = "", method = RequestMethod.POST)
    public ResponseEntity<Object> createUser(@Valid @RequestBody UserDto.Request.Create createUser, UriComponentsBuilder uriBuilder) {
        User user = userService.fromDto(createUser);
        user.setAuthority(userService.toRole(createUser.getAdminLevel()));
        user = userService.createUser(user);
        UriComponents uriComponents = uriBuilder.path("/api/user/{id}").buildAndExpand(user.getId());
        return ResponseEntity.created(uriComponents.toUri()).build();
    }



    private Long getUserIdToUpdate(Long userId, User principal) {
        if (userId == null) {
            return principal.getId();
        }
        return userId;
    }

    private void checkAdminPermissions(User principal, Long userId) {
        if (!principal.getAuthorities().contains(ERole.ROLE_ADMIN) && principal.getId() != (userId)) {
            throw new IllegalArgumentException("Admin permissions required");
        }
    }



    private void checkSelfEditRestrictions(User principal, Long userId, User updateUser, User beforeUpdate) {
        if (principal.getId()!=(userId)) {
            if (!principal.getAuthorities().contains(ERole.ROLE_ADMIN)) {
                throw new IllegalArgumentException("You can't edit your own role or lock/unlock yourself!");
            }
            if (!updateUser.getAuthority().equals(beforeUpdate.getAuthority())) {
                throw new IllegalArgumentException("You can't edit your own role!");
            }
            if (updateUser.isAccountNonLocked() != beforeUpdate.isAccountNonLocked()) {
                throw new IllegalArgumentException("You can't lock/unlock yourself!");
            }
        }
    }

    private void setUserPassword(User updateUser, User beforeUpdate, boolean updatePassword) {
        if (!updatePassword) {
            updateUser.setPassword(beforeUpdate.getPassword());
        }
    }


    @PreAuthorize("hasRole('ADMIN')")
    @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
    public ResponseEntity<Object> deleteUser(@PathVariable("id") long userId) {
        User principal = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(principal.getId() == userId) {
            throw new IllegalArgumentException("You can't delete yourself!");
        }
        if(userService.getUser(userId) == null) {
            return ResponseEntity.notFound().build();
        }
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @RequestMapping(value = {"{id}", "current"}, method = RequestMethod.GET)
    public ResponseEntity<?> getUser(@PathVariable(value = "id", required = false) Long userId) {
        User principal = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(userId == null) {
            userId = principal.getId();
        } else {
            if(!principal.getAuthorities().contains(ERole.ROLE_ADMIN)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        User user = userService.getUser(userId);
        if(user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new UserDto.Response.Detailed(user));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @RequestMapping(value = "", method = RequestMethod.GET)
    public ResponseEntity<?> getUsers() {
        List<UserDto.Response.Detailed> userDtoList = userService.getUsers()
                .stream().map(UserDto.Response.Detailed::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(userDtoList);
    }
}
