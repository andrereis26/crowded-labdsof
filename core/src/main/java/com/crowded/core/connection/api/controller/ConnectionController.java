package com.crowded.core.connection.api.controller;

import com.crowded.core.connection.api.dto.ConnectionResponse;
import com.crowded.core.connection.api.dto.JoinWithAccessCodeRequest;
import com.crowded.core.connection.api.dto.MyConnectionsResponse;
import com.crowded.core.connection.api.mapper.ConnectionApiMapper;
import com.crowded.core.connection.application.dto.JoinedEvent;
import com.crowded.core.connection.application.service.AccessCodeConnectionService;
import com.crowded.core.connection.application.service.MyConnectionService;
import com.crowded.core.shared.api.controller.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** A participant's connections to events (API Outline, "Connect to an event"). */
@RestController
public class ConnectionController {

  private final AccessCodeConnectionService accessCodes;
  private final MyConnectionService myConnections;
  private final ConnectionApiMapper mapper;

  public ConnectionController(
      AccessCodeConnectionService accessCodes,
      MyConnectionService myConnections,
      ConnectionApiMapper mapper) {
    this.accessCodes = accessCodes;
    this.myConnections = myConnections;
    this.mapper = mapper;
  }

  /**
   * Connects the signed-in participant to the public event of the access code: {@code 201} for a
   * new connection, {@code 200} with the existing one when they were connected already.
   */
  @PostMapping("/connections")
  public ResponseEntity<ConnectionResponse> joinWithAccessCode(
      @AuthenticationPrincipal Jwt token, @Valid @RequestBody JoinWithAccessCodeRequest request) {
    JoinedEvent joined =
        accessCodes.joinWithAccessCode(
            mapper.toCommand(CurrentUser.id(token), request.accessCode()));
    return ResponseEntity.status(joined.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(mapper.toResponse(joined));
  }

  /**
   * The signed-in participant's events, those that have not ended first. The participant comes from
   * the token, never from the request.
   */
  @GetMapping("/me/connections")
  public MyConnectionsResponse myConnections(@AuthenticationPrincipal Jwt token) {
    return mapper.toResponse(myConnections.listOf(CurrentUser.id(token)));
  }
}
