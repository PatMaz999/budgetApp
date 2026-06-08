package pk.ni.pasir_mazurek_patryk.service;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pk.ni.pasir_mazurek_patryk.dto.LoginDto;
import pk.ni.pasir_mazurek_patryk.dto.UserDto;
import pk.ni.pasir_mazurek_patryk.exception.UserAlreadyExistsException;
import pk.ni.pasir_mazurek_patryk.model.User;
import pk.ni.pasir_mazurek_patryk.repository.UserRepository;
import pk.ni.pasir_mazurek_patryk.security.JwtUtil;

import java.util.ArrayList;

@NullMarked
@RequiredArgsConstructor
@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    public User register(UserDto dto){
        if(userRepository.findByEmail(dto.getEmail()).isPresent())
            throw new UserAlreadyExistsException("Uzytkownik z tym adresem e-mail juz istnieje");

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPassword(encoder.encode(dto.getPassword()));
        return userRepository.save(user);
    }

    public String login(LoginDto dto){
        User user = userRepository.findByEmail(dto.email())
                .orElseThrow(() -> new UsernameNotFoundException("Nie znaleziono uzywtkownika"));
        if(!encoder.matches(dto.password(), user.getPassword()))
            throw new BadCredentialsException("Nieprawidlowe dane logowania");
        return jwtUtil.generateToken(user);
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Nie znaleziono uzytkownika: " + email));
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                new ArrayList<>()
        );
    }
}
