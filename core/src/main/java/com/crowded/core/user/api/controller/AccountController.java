package com.crowded.core.user.api.controller;

import com.crowded.core.shared.api.controller.CurrentUser;
import com.crowded.core.user.api.dto.AccountResponse;
import com.crowded.core.user.api.mapper.AccountApiMapper;
import com.crowded.core.user.application.service.AccountService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user, which each app reads at start-up to know which pages to show (ADR 0021). */
@RestController
public class AccountController {

  private final AccountService service;
  private final AccountApiMapper mapper;

  AccountController(AccountService service, AccountApiMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  /** 200 with the signed-in user; 401 when no one is. */
  @GetMapping("/me")
  public AccountResponse current(@AuthenticationPrincipal Jwt token) {
    return mapper.toResponse(service.current(CurrentUser.id(token)));
  }
}
