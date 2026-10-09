package com.tongtin.groups.shares;

import com.tongtin.common.security.AuthPrincipal;
import com.tongtin.groups.shares.dto.ShareAssignRequest;
import com.tongtin.groups.shares.dto.ShareResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{id}/shares")
@PreAuthorize("hasRole('HOST')")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping
    public ResponseEntity<List<ShareResponse>> assign(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ShareAssignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shareService.assign(principal.ownerId(), id, request));
    }

    @GetMapping
    public List<ShareResponse> list(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        return shareService.list(principal.ownerId(), id);
    }

    @DeleteMapping("/{shareId}")
    public void remove(@AuthenticationPrincipal AuthPrincipal principal,
                       @PathVariable Long id,
                       @PathVariable Long shareId) {
        shareService.remove(principal.ownerId(), id, shareId);
    }
}