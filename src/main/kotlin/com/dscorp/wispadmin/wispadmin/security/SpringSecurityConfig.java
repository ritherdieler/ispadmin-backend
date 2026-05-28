//package com.dscorp.wispadmin.wispadmin.security;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.env.Environment;
//import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.annotation.web.builders.WebSecurity;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
//import org.springframework.security.config.http.SessionCreationPolicy;
//import org.springframework.security.core.userdetails.User;
//import org.springframework.security.core.userdetails.UserDetailsService;
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//import org.springframework.security.crypto.factory.PasswordEncoderFactories;
//import org.springframework.security.crypto.password.NoOpPasswordEncoder;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.security.provisioning.InMemoryUserDetailsManager;
//
///**
// * Configuration for server security.
// * <p/>
// * This is just a basic authentication configuration. The application will require a basic auth
// * in order to allow to call the endpoints. <p/>
// * User and password are defined in application.properties file, but password can be overrided by
// * an environment variable called SERVER_AUTH_TOKEN
// *
// * @author Lyra Network
// */
//@Configuration
//@EnableWebSecurity
//public class SpringSecurityConfig extends WebSecurityConfigurerAdapter {
//    private final Environment env;
//
//    @Autowired
//    public SpringSecurityConfig(Environment env) {
//        this.env = env;
//    }
//
//    @Value("${spring.security.user.name}")
//    private String configurationAuthName;
//
//    @Value("${spring.security.user.password}")
//    private String configurationAuthToken;
//
//    @Override
//    protected void configure(HttpSecurity http) throws Exception {
//        http
//                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
//                .and().csrf().disable().authorizeRequests()
//                .antMatchers("/izipay/**").authenticated()
//                .anyRequest().permitAll()
//                .and().httpBasic().and().logout();
//    }
//
//    @Override
//    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
////        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
////
////        auth.userDetailsService(userDetailsService())
////           .passwordEncoder(encoder);
//
//        auth.userDetailsService(userDetailsService())
//                .passwordEncoder(passwordEncoder());
//    }
//
//
//    @Bean
//    public PasswordEncoder passwordEncoder() {
//        // Utiliza un PasswordEncoder seguro en producción
//        return NoOpPasswordEncoder.getInstance();
//    }
//
//
//    @Bean
//    public UserDetailsService userDetailsService() {
//        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
//        User.UserBuilder userBuilder = User.withUsername(configurationAuthName).password(configurationAuthToken);
//
//        manager.createUser(userBuilder.roles("USER").build());
//        return manager;
//    }
//
//
//
//}