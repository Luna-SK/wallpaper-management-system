package com.luna.wallpaper.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.luna.wallpaper.audit.AuditLogService;
import com.luna.wallpaper.image.ImageAssetMapper;
import com.luna.wallpaper.interaction.InteractionDtos.CommentRequest;
import com.luna.wallpaper.interaction.InteractionDtos.FeedbackCreateRequest;
import com.luna.wallpaper.interaction.InteractionDtos.FeedbackHandleRequest;

class InteractionServiceTests {

	private final ImageAssetMapper images = mock(ImageAssetMapper.class);
	private final ImageCommentMapper comments = mock(ImageCommentMapper.class);
	private final ImageFavoriteMapper favorites = mock(ImageFavoriteMapper.class);
	private final ImageLikeMapper likes = mock(ImageLikeMapper.class);
	private final UserFeedbackMapper feedback = mock(UserFeedbackMapper.class);
	private final AuditLogService auditLogService = mock(AuditLogService.class);
	private final InteractionService service = new InteractionService(images, comments, favorites, likes, feedback,
			auditLogService);

	@BeforeEach
	void setUp() {
		when(images.countRetainedById("image-1")).thenReturn(1);
	}

	@Test
	void summariesIncludeCountsAndCurrentUserState() {
		ImageInteractionStatsRow row = statsRow("image-1", 2, 3, 4, true, false);
		when(favorites.selectSummaries(List.of("image-1"), "user-1")).thenReturn(List.of(row));

		Map<String, InteractionDtos.ImageInteractionSummary> result = service.summaries(List.of("image-1"), "user-1");

		assertThat(result.get("image-1").favoriteCount()).isEqualTo(2);
		assertThat(result.get("image-1").likeCount()).isEqualTo(3);
		assertThat(result.get("image-1").commentCount()).isEqualTo(4);
		assertThat(result.get("image-1").favoritedByMe()).isTrue();
		assertThat(result.get("image-1").likedByMe()).isFalse();
	}

	@Test
	void createCommentStoresTrimmedContentAndReturnsAuthorContext() {
		when(comments.selectRowById(anyString())).thenAnswer(invocation -> commentRow(invocation.getArgument(0),
				"image-1", "user-1", "用户一", "你好", "ACTIVE"));
		ArgumentCaptor<ImageComment> captor = ArgumentCaptor.forClass(ImageComment.class);

		var response = service.createComment("image-1", "user-1", new CommentRequest("  你好  ", null));

		verify(comments).insert(captor.capture());
		assertThat(captor.getValue().content()).isEqualTo("你好");
		assertThat(captor.getValue().parentCommentId()).isNull();
		assertThat(captor.getValue().rootCommentId()).isEqualTo(captor.getValue().id());
		assertThat(response.mine()).isTrue();
		assertThat(response.authorName()).isEqualTo("用户一");
		assertThat(response.authorAvatarUrl()).isNull();
		verify(auditLogService).record("interaction.comment.create", "IMAGE", "image-1",
				Map.of("commentId", response.id(), "parentCommentId", ""));
	}

	@Test
	void createReplyStoresParentRootAndDepth() {
		ImageComment parent = new ImageComment("image-1", "owner-1", "父评论");
		LocalDateTime parentUpdatedAt = LocalDateTime.now();
		ReflectionTestUtils.setField(parent, "updatedAt", parentUpdatedAt);
		when(comments.selectByIdForUpdate(parent.id())).thenReturn(parent);
		when(comments.selectRowById(anyString())).thenAnswer(invocation -> commentRow(invocation.getArgument(0),
				"image-1", "user-1", "用户一", parent.id(), parent.id(), 1, "回复", "ACTIVE"));
		ArgumentCaptor<ImageComment> captor = ArgumentCaptor.forClass(ImageComment.class);

		var response = service.createComment("image-1", "user-1",
				new CommentRequest(" 回复 ", parent.id(), parentUpdatedAt));

		verify(comments).insert(captor.capture());
		assertThat(captor.getValue().parentCommentId()).isEqualTo(parent.id());
		assertThat(captor.getValue().rootCommentId()).isEqualTo(parent.id());
		assertThat(captor.getValue().depth()).isEqualTo(1);
		assertThat(response.parentCommentId()).isEqualTo(parent.id());
	}

	@Test
	void createReplyRejectsParentFromAnotherImage() {
		ImageComment parent = new ImageComment("image-2", "owner-1", "父评论");
		LocalDateTime parentUpdatedAt = LocalDateTime.now();
		ReflectionTestUtils.setField(parent, "updatedAt", parentUpdatedAt);
		when(comments.selectByIdForUpdate(parent.id())).thenReturn(parent);

		assertThatThrownBy(() -> service.createComment("image-1", "user-1",
				new CommentRequest("回复", parent.id(), parentUpdatedAt)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("回复的评论不存在");
	}

	@Test
	void createReplyRejectsStaleParentVersion() {
		ImageComment parent = new ImageComment("image-1", "owner-1", "父评论");
		LocalDateTime currentUpdatedAt = LocalDateTime.now();
		ReflectionTestUtils.setField(parent, "updatedAt", currentUpdatedAt);
		when(comments.selectByIdForUpdate(parent.id())).thenReturn(parent);

		assertThatThrownBy(() -> service.createComment("image-1", "user-1",
				new CommentRequest("回复", parent.id(), currentUpdatedAt.minusSeconds(1))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("该评论已更新，请刷新后再回复");
	}

	@Test
	void updateCommentAllowsOwnerWhenNoReplies() {
		ImageComment comment = new ImageComment("image-1", "user-1", "原内容");
		when(comments.selectByIdForUpdate(comment.id())).thenReturn(comment);
		when(comments.countByParentId(comment.id())).thenReturn(0L);
		when(comments.selectRowById(comment.id())).thenReturn(commentRow(comment.id(), "image-1", "user-1",
				"用户一", "新内容", "ACTIVE"));

		var response = service.updateComment("image-1", comment.id(), "user-1",
				new CommentRequest(" 新内容 ", null));

		assertThat(comment.content()).isEqualTo("新内容");
		assertThat(response.content()).isEqualTo("新内容");
		verify(comments).updateById(comment);
	}

	@Test
	void updateCommentRejectsCommentWithReplies() {
		ImageComment comment = new ImageComment("image-1", "user-1", "原内容");
		when(comments.selectByIdForUpdate(comment.id())).thenReturn(comment);
		when(comments.countByParentId(comment.id())).thenReturn(1L);

		assertThatThrownBy(() -> service.updateComment("image-1", comment.id(), "user-1",
				new CommentRequest("新内容", null)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("已有回复的评论不能编辑");
	}

	@Test
	void commentsReturnThreadTreeWithAuthorAvatarUrl() {
		ImageCommentRow root = commentRow("root-1", "image-1", "user-1", "用户一", null, "root-1", 0, "顶层",
				"ACTIVE");
		ImageCommentRow child = commentRow("child-1", "image-1", "user-2", "用户二", "root-1", "root-1", 1,
				"回复", "ACTIVE");
		ReflectionTestUtils.setField(child, "authorAvatarObjectKey", "avatars/user-2/avatar.png");
		when(comments.countVisibleRootThreadsByImageId("image-1")).thenReturn(1L);
		when(comments.countActiveByImageId("image-1")).thenReturn(2L);
		when(comments.selectVisibleRootRowsByImageId("image-1", 0, 20)).thenReturn(List.of(root));
		when(comments.selectRowsByRootIds(List.of("root-1"))).thenReturn(List.of(root, child));

		var page = service.comments("image-1", "user-1", 1, 20);

		assertThat(page.total()).isEqualTo(1);
		assertThat(page.commentTotal()).isEqualTo(2);
		assertThat(page.items()).hasSize(1);
		assertThat(page.items().getFirst().depth()).isZero();
		assertThat(page.items().getFirst().hasReplies()).isTrue();
		assertThat(page.items().getFirst().replies().getFirst().parentCommentId()).isEqualTo("root-1");
		assertThat(page.items().getFirst().replies().getFirst().depth()).isEqualTo(1);
		assertThat(page.items().getFirst().replies().getFirst().authorAvatarUrl())
				.isEqualTo("/api/users/user-2/avatar?v=0");
	}

	@Test
	void deletedCommentWithRepliesIsReturnedAsPlaceholder() {
		ImageCommentRow root = commentRow("root-1", "image-1", "user-1", "用户一", null, "root-1", 0, "已删内容",
				"DELETED");
		ImageCommentRow child = commentRow("child-1", "image-1", "user-2", "用户二", "root-1", "root-1", 1,
				"回复", "ACTIVE");
		when(comments.countVisibleRootThreadsByImageId("image-1")).thenReturn(1L);
		when(comments.countActiveByImageId("image-1")).thenReturn(1L);
		when(comments.selectVisibleRootRowsByImageId("image-1", 0, 20)).thenReturn(List.of(root));
		when(comments.selectRowsByRootIds(List.of("root-1"))).thenReturn(List.of(root, child));

		var item = service.comments("image-1", "user-2", 1, 20).items().getFirst();

		assertThat(item.deleted()).isTrue();
		assertThat(item.content()).isNull();
		assertThat(item.replies()).hasSize(1);
		assertThat(item.replies().getFirst().content()).isEqualTo("回复");
	}

	@Test
	void deleteCommentRejectsOtherUsersWithoutManagePermission() {
		ImageComment comment = new ImageComment("image-1", "owner-1", "内容");
		when(comments.selectById(comment.id())).thenReturn(comment);

		assertThatThrownBy(() -> service.deleteComment("image-1", comment.id(), "user-2", false))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("只能删除自己的评论");
	}

	@Test
	void favoriteAndLikeReturnUpdatedState() {
		when(favorites.selectSummaries(List.of("image-1"), "user-1")).thenReturn(List.of(
				statsRow("image-1", 1, 1, 0, true, true)));

		var favoriteState = service.favorite("image-1", "user-1");
		var likeState = service.like("image-1", "user-1");

		assertThat(favoriteState.favoritedByMe()).isTrue();
		assertThat(likeState.likedByMe()).isTrue();
		verify(favorites).insertIgnore(anyString(), anyString(), anyString());
		verify(likes).insertIgnore(anyString(), anyString(), anyString());
	}

	@Test
	void createFeedbackCanReferenceRetainedImage() {
		when(feedback.selectRowById(anyString())).thenAnswer(invocation -> feedbackRow(invocation.getArgument(0),
				"user-1", "alice", "Alice", "image-1", "图片一", "IMAGE", "标题", "内容", "OPEN", null));

		var response = service.createFeedback("user-1",
				new FeedbackCreateRequest("image", " 标题 ", " 内容 ", "image-1"));

		assertThat(response.status()).isEqualTo("OPEN");
		assertThat(response.imageId()).isEqualTo("image-1");
		assertThat(response.title()).isEqualTo("标题");
		verify(feedback).insert(any(UserFeedback.class));
		verify(auditLogService).record("interaction.feedback.create", "USER_FEEDBACK", response.id(),
				Map.of("imageId", "image-1"));
	}

	@Test
	void handleFeedbackUpdatesStatusAndResponse() {
		UserFeedback item = new UserFeedback("user-1", null, "GENERAL", "标题", "内容");
		when(feedback.selectById(item.id())).thenReturn(item);
		when(feedback.selectRowById(item.id())).thenReturn(feedbackRow(item.id(), "user-1", "alice", "Alice",
				null, null, "GENERAL", "标题", "内容", "RESOLVED", "已处理"));

		var response = service.handleFeedback("admin-1", item.id(), new FeedbackHandleRequest("RESOLVED", " 已处理 "));

		assertThat(item.status()).isEqualTo(FeedbackStatus.RESOLVED);
		assertThat(response.status()).isEqualTo("RESOLVED");
		assertThat(response.response()).isEqualTo("已处理");
		verify(feedback).updateById(item);
		verify(auditLogService).record("interaction.feedback.handle", "USER_FEEDBACK", item.id(),
				Map.of("status", "RESOLVED"));
	}

	@Test
	void cleanupForImagePurgeDeletesImageInteractionsAndKeepsFeedbackRecord() {
		service.cleanupForImagePurge("image-1");

		verify(comments).deleteByImageId("image-1");
		verify(favorites).deleteByImageId("image-1");
		verify(likes).deleteByImageId("image-1");
		verify(feedback).clearImageId("image-1");
	}

	@Test
	void cleanupForUserPurgeDeletesUserInteractionsAndFeedback() {
		service.cleanupForUserPurge("user-1");

		verify(comments).deleteByUserId("user-1");
		verify(favorites).deleteByUserId("user-1");
		verify(likes).deleteByUserId("user-1");
		verify(feedback).deleteByUserId("user-1");
	}

	private static ImageInteractionStatsRow statsRow(String imageId, long favoriteCount, long likeCount,
			long commentCount, boolean favoritedByMe, boolean likedByMe) {
		ImageInteractionStatsRow row = new ImageInteractionStatsRow();
		ReflectionTestUtils.setField(row, "imageId", imageId);
		ReflectionTestUtils.setField(row, "favoriteCount", favoriteCount);
		ReflectionTestUtils.setField(row, "likeCount", likeCount);
		ReflectionTestUtils.setField(row, "commentCount", commentCount);
		ReflectionTestUtils.setField(row, "favoritedByMe", favoritedByMe);
		ReflectionTestUtils.setField(row, "likedByMe", likedByMe);
		return row;
	}

	private static ImageCommentRow commentRow(String id, String imageId, String userId, String authorName,
			String content, String status) {
		return commentRow(id, imageId, userId, authorName, null, id, 0, content, status);
	}

	private static ImageCommentRow commentRow(String id, String imageId, String userId, String authorName,
			String parentCommentId, String rootCommentId, int depth, String content, String status) {
		ImageCommentRow row = new ImageCommentRow();
		ReflectionTestUtils.setField(row, "id", id);
		ReflectionTestUtils.setField(row, "imageId", imageId);
		ReflectionTestUtils.setField(row, "userId", userId);
		ReflectionTestUtils.setField(row, "authorName", authorName);
		ReflectionTestUtils.setField(row, "parentCommentId", parentCommentId);
		ReflectionTestUtils.setField(row, "rootCommentId", rootCommentId);
		ReflectionTestUtils.setField(row, "depth", depth);
		ReflectionTestUtils.setField(row, "content", content);
		ReflectionTestUtils.setField(row, "status", status);
		ReflectionTestUtils.setField(row, "createdAt", LocalDateTime.now());
		ReflectionTestUtils.setField(row, "updatedAt", LocalDateTime.now());
		return row;
	}

	private static UserFeedbackRow feedbackRow(String id, String userId, String username, String displayName,
			String imageId, String imageTitle, String type, String title, String content, String status,
			String response) {
		UserFeedbackRow row = new UserFeedbackRow();
		ReflectionTestUtils.setField(row, "id", id);
		ReflectionTestUtils.setField(row, "userId", userId);
		ReflectionTestUtils.setField(row, "username", username);
		ReflectionTestUtils.setField(row, "displayName", displayName);
		ReflectionTestUtils.setField(row, "imageId", imageId);
		ReflectionTestUtils.setField(row, "imageTitle", imageTitle);
		ReflectionTestUtils.setField(row, "type", type);
		ReflectionTestUtils.setField(row, "title", title);
		ReflectionTestUtils.setField(row, "content", content);
		ReflectionTestUtils.setField(row, "status", status);
		ReflectionTestUtils.setField(row, "response", response);
		ReflectionTestUtils.setField(row, "createdAt", LocalDateTime.now());
		ReflectionTestUtils.setField(row, "updatedAt", LocalDateTime.now());
		return row;
	}
}
