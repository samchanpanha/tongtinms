package com.tongtin.groups.web;

import com.tongtin.common.security.AuthPrincipal;
import com.tongtin.groups.dto.GroupCreateRequest;
import com.tongtin.groups.dto.GroupPatchRequest;
import com.tongtin.groups.dto.GroupResponse;
import com.tongtin.groups.service.GroupService;
import com.tongtin.groups.shares.ShareService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups")
@PreAuthorize("hasRole('HOST')")
public class GroupController {

    private final GroupService groupService;
    private final ShareService shareService;

    public GroupController(GroupService groupService, ShareService shareService) {
        this.groupService = groupService;
        this.shareService = shareService;
    }

    @PostMapping
    public ResponseEntity<GroupResponse> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody GroupCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groupService.create(principal.ownerId(), request));
    }

    @PatchMapping("/{id}")
    public GroupResponse update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody GroupPatchRequest request) {
        return groupService.update(principal.ownerId(), id, request);
    }

    @GetMapping
    public List<GroupResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return groupService.list(principal.ownerId());
    }

    @GetMapping("/{id}")
    public GroupResponse get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        return groupService.get(principal.ownerId(), id);
    }

    @PostMapping("/{id}/start")
    public GroupResponse start(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        return shareService.start(principal.ownerId(), id);
    }
}
