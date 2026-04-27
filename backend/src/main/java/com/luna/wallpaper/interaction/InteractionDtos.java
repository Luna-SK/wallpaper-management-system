package com.luna.wallpaper.interaction;

import java.time.LocalDateTime;
import java.util.List;

public final class InteractionDtos {
	private InteractionDtos() {
	}

	public record ImageInteractionSummary(String imageId, long favoriteCount, long likeCount, long commentCount,
			boolean favoritedByMe, boolean likedByMe) {
		public static ImageInteractionSummary empty(String imageId) {
			return new ImageInteractionSummary(imageId, 0, 0, 0, false, false);
		}
	}

	public record InteractionStateResponse(String imageId, long favoriteCount, long likeCount, long commentCount,
			boolean favoritedByMe, boolean likedByMe) {
		static InteractionStateResponse from(ImageInteractionSummary summary) {
			return new InteractionStateResponse(summary.imageId(), summary.favoriteCount(), summary.likeCount(),
					summary.commentCount(), summary.favoritedByMe(), summary.likedByMe());
		}
	}

	public record CommentRequest(String content) {
	}

	public record CommentPageResponse(List<CommentResponse> items, int page, int size, long total) {
	}

	public record CommentResponse(String id, String imageId, String userId, String authorName, String content,
			String status, LocalDateTime createdAt, LocalDateTime updatedAt, boolean mine) {
		static CommentResponse from(ImageCommentRow row, String currentUserId) {
			return new CommentResponse(row.id(), row.imageId(), row.userId(), row.authorName(), row.content(),
					row.status(), row.createdAt(), row.updatedAt(), row.userId().equals(currentUserId));
		}
	}

	public record FeedbackCreateRequest(String type, String title, String content, String imageId) {
	}

	public record FeedbackHandleRequest(String status, String response) {
	}

	public record FeedbackPageResponse(List<FeedbackResponse> items, int page, int size, long total) {
	}

	public record FeedbackResponse(String id, String userId, String username, String displayName, String imageId,
			String imageTitle, String type, String title, String content, String status, String response,
			String handledBy, LocalDateTime handledAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
		static FeedbackResponse from(UserFeedbackRow row) {
			return new FeedbackResponse(row.id(), row.userId(), row.username(), row.displayName(), row.imageId(),
					row.imageTitle(), row.type(), row.title(), row.content(), row.status(), row.response(),
					row.handledBy(), row.handledAt(), row.createdAt(), row.updatedAt());
		}
	}
}
