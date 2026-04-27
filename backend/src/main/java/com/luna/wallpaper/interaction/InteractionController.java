package com.luna.wallpaper.interaction;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.luna.wallpaper.interaction.InteractionDtos.CommentPageResponse;
import com.luna.wallpaper.interaction.InteractionDtos.CommentRequest;
import com.luna.wallpaper.interaction.InteractionDtos.CommentResponse;
import com.luna.wallpaper.interaction.InteractionDtos.FeedbackCreateRequest;
import com.luna.wallpaper.interaction.InteractionDtos.FeedbackHandleRequest;
import com.luna.wallpaper.interaction.InteractionDtos.FeedbackPageResponse;
import com.luna.wallpaper.interaction.InteractionDtos.FeedbackResponse;
import com.luna.wallpaper.interaction.InteractionDtos.InteractionStateResponse;
import com.luna.wallpaper.rbac.AuthenticatedUser;

@RestController
@RequestMapping("/api")
class InteractionController {

	private final InteractionService service;

	InteractionController(InteractionService service) {
		this.service = service;
	}

	@GetMapping("/images/{imageId}/comments")
	@PreAuthorize("hasAuthority('image:view')")
	CommentPageResponse comments(@PathVariable String imageId, Authentication authentication,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
		return service.comments(imageId, currentUser(authentication).id(), page, size);
	}

	@PostMapping("/images/{imageId}/comments")
	@PreAuthorize("hasAuthority('image:view')")
	CommentResponse createComment(@PathVariable String imageId, Authentication authentication,
			@RequestBody CommentRequest request) {
		return service.createComment(imageId, currentUser(authentication).id(), request);
	}

	@PatchMapping("/images/{imageId}/comments/{commentId}")
	@PreAuthorize("hasAuthority('image:view')")
	CommentResponse updateComment(@PathVariable String imageId, @PathVariable String commentId,
			Authentication authentication, @RequestBody CommentRequest request) {
		return service.updateComment(imageId, commentId, currentUser(authentication).id(), request);
	}

	@DeleteMapping("/images/{imageId}/comments/{commentId}")
	@PreAuthorize("hasAuthority('image:view')")
	void deleteComment(@PathVariable String imageId, @PathVariable String commentId, Authentication authentication) {
		service.deleteComment(imageId, commentId, currentUser(authentication).id(),
				authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("interaction:manage")));
	}

	@PostMapping("/images/{imageId}/favorite")
	@PreAuthorize("hasAuthority('image:view')")
	InteractionStateResponse favorite(@PathVariable String imageId, Authentication authentication) {
		return service.favorite(imageId, currentUser(authentication).id());
	}

	@DeleteMapping("/images/{imageId}/favorite")
	@PreAuthorize("hasAuthority('image:view')")
	InteractionStateResponse unfavorite(@PathVariable String imageId, Authentication authentication) {
		return service.unfavorite(imageId, currentUser(authentication).id());
	}

	@PostMapping("/images/{imageId}/like")
	@PreAuthorize("hasAuthority('image:view')")
	InteractionStateResponse like(@PathVariable String imageId, Authentication authentication) {
		return service.like(imageId, currentUser(authentication).id());
	}

	@DeleteMapping("/images/{imageId}/like")
	@PreAuthorize("hasAuthority('image:view')")
	InteractionStateResponse unlike(@PathVariable String imageId, Authentication authentication) {
		return service.unlike(imageId, currentUser(authentication).id());
	}

	@GetMapping("/feedback")
	@PreAuthorize("isAuthenticated()")
	FeedbackPageResponse myFeedback(Authentication authentication, @RequestParam(required = false) String status,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
		return service.myFeedback(currentUser(authentication).id(), status, page, size);
	}

	@PostMapping("/feedback")
	@PreAuthorize("isAuthenticated()")
	FeedbackResponse createFeedback(Authentication authentication, @RequestBody FeedbackCreateRequest request) {
		return service.createFeedback(currentUser(authentication).id(), request);
	}

	@PostMapping("/feedback/{id}/close")
	@PreAuthorize("isAuthenticated()")
	void closeFeedback(@PathVariable String id, Authentication authentication) {
		service.closeFeedback(currentUser(authentication).id(), id);
	}

	@GetMapping("/feedback/admin")
	@PreAuthorize("hasAuthority('interaction:manage')")
	FeedbackPageResponse adminFeedback(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.adminFeedback(keyword, status, page, size);
	}

	@PatchMapping("/feedback/admin/{id}")
	@PreAuthorize("hasAuthority('interaction:manage')")
	FeedbackResponse handleFeedback(@PathVariable String id, Authentication authentication,
			@RequestBody FeedbackHandleRequest request) {
		return service.handleFeedback(currentUser(authentication).id(), id, request);
	}

	private AuthenticatedUser currentUser(Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
			return user;
		}
		throw new IllegalArgumentException("请先登录");
	}
}
