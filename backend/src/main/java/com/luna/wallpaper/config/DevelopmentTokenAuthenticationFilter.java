package com.luna.wallpaper.config;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
class DevelopmentTokenAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private static final List<SimpleGrantedAuthority> DEVELOPMENT_AUTHORITIES = List.of(
			new SimpleGrantedAuthority("image:view"),
			new SimpleGrantedAuthority("image:upload"),
			new SimpleGrantedAuthority("image:edit"),
			new SimpleGrantedAuthority("image:delete"),
			new SimpleGrantedAuthority("taxonomy:manage"),
			new SimpleGrantedAuthority("user:manage"),
			new SimpleGrantedAuthority("role:manage"),
			new SimpleGrantedAuthority("audit:view"),
			new SimpleGrantedAuthority("audit:manage"),
			new SimpleGrantedAuthority("setting:manage"),
			new SimpleGrantedAuthority("backup:manage"));

	private final SecurityProperties properties;

	DevelopmentTokenAuthenticationFilter(SecurityProperties properties) {
		this.properties = properties;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		if (properties.hasDevelopmentToken() && authorization != null && authorization.startsWith(BEARER_PREFIX)) {
			String token = authorization.substring(BEARER_PREFIX.length());
			if (properties.developmentToken().equals(token)
					&& SecurityContextHolder.getContext().getAuthentication() == null) {
				UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
						"development-admin", token, DEVELOPMENT_AUTHORITIES);
				SecurityContextHolder.getContext().setAuthentication(authentication);
			}
		}
		filterChain.doFilter(request, response);
	}
}
