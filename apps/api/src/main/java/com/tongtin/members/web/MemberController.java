package com.tongtin.members.web;

import com.tongtin.common.security.AuthPrincipal;
import com.tongtin.members.dto.MemberCreateRequest;
import com.tongtin.members.dto.MemberPatchRequest;
import com.tongtin.members.dto.MemberResponse;
import com.tongtin.members.dto.SetMemberLoginRequest;
import com.tongtin.members.service.MemberService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
@PreAuthorize("hasRole('HOST')")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping
    public ResponseEntity<MemberResponse> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody MemberCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(memberService.create(principal.ownerId(), request));
    }

    @GetMapping
    public ResponseEntity<List<MemberResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(name = "q", required = false) String q) {
        return ResponseEntity.ok(memberService.list(principal.ownerId(), q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MemberResponse> get(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(memberService.get(principal.ownerId(), id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<MemberResponse> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") Long id,
            @Valid @RequestBody MemberPatchRequest request) {
        return ResponseEntity.ok(memberService.update(principal.ownerId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MemberResponse> deactivate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(memberService.deactivate(principal.ownerId(), id));
    }

    @PostMapping("/{id}/set-login")
    public ResponseEntity<Map<String, Object>> setLogin(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") Long id,
            @Valid @RequestBody SetMemberLoginRequest request) {
        return ResponseEntity.ok(memberService.setLogin(principal.ownerId(), id, request.password()));
    }
}
