package com.luna.wallpaper.interaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luna.wallpaper.audit.AuditLogService;
import com.luna.wallpaper.image.ImageAssetMapper;
import com.luna.wallpaper.interaction.InteractionDtos.CommentPageResponse;
import com.luna.wallpaper.interaction.InteractionDtos.CommentRequest;
import com.luna.wallpaper.interaction.InteractionDtos.CommentResponse;
import com.luna.wallpaper.interaction.InteractionDtos.FeedbackCreateRequest;
import com.luna.wallpaper.interaction.InteractionDtos.FeedbackHandleRequest;
import com.luna.wallpaper.interaction.InteractionDtos.FeedbackPageResponse;
import com.luna.wallpaper.interaction.InteractionDtos.FeedbackResponse;
import com.luna.wallpaper.interaction.InteractionDtos.ImageInteractionSummary;
import com.luna.wallpaper.interaction.InteractionDtos.InteractionStateResponse;
import com.luna.wallpaper.rbac.UserAvatars;

@Service
public class InteractionService {

	private final ImageAssetMapper images;
	private final ImageCommentMapper comments;
	private final ImageFavoriteMapper favorites;
	private final ImageLikeMapper likes;
	private final UserFeedbackMapper feedback;
	private final AuditLogService auditLogService;

	InteractionService(ImageAssetMapper images, ImageCommentMapper comments, ImageFavoriteMapper favorites,
			ImageLikeMapper likes, UserFeedbackMapper feedback, AuditLogService auditLogService) {
		this.images = images;
		this.comments = comments;
		this.favorites = favorites;
		this.likes = likes;
		this.feedback = feedback;
		this.auditLogService = auditLogService;
	}

	@Transactional(readOnly = true)
	public Map<String, ImageInteractionSummary> summaries(Collection<String> imageIds, String userId) {
		if (imageIds == null || imageIds.isEmpty()) {
			return Map.of();
		}
		return favorites.selectSummaries(imageIds.stream().distinct().toList(), userId).stream()
				.map(row -> new ImageInteractionSummary(row.imageId(), row.favoriteCount(), row.likeCount(),
						row.commentCount(), row.favoritedByMe(), row.likedByMe()))
				.collect(Collectors.toMap(ImageInteractionSummary::imageId, Function.identity()));
	}

	@Transactional(readOnly = true)
	public ImageInteractionSummary summary(String imageId, String userId) {
		ensureRetainedImage(imageId);
		return summaries(List.of(imageId), userId).getOrDefault(imageId, ImageInteractionSummary.empty(imageId));
	}

	@Transactional(readOnly = true)
	public CommentPageResponse comments(String imageId, String userId, int page, int size) {
		ensureRetainedImage(imageId);
		int safePage = Math.max(1, page);
		int safeSize = Math.min(Math.max(1, size), 100);
		long total = comments.countVisibleRootThreadsByImageId(imageId);
		long commentTotal = comments.countActiveByImageId(imageId);
		long offset = (long) (safePage - 1) * safeSize;
		List<ImageCommentRow> roots = total == 0 ? List.of()
				: comments.selectVisibleRootRowsByImageId(imageId, offset, safeSize);
		List<CommentResponse> items = roots.isEmpty() ? List.of()
				: buildCommentTree(roots, comments.selectRowsByRootIds(roots.stream().map(ImageCommentRow::id).toList()),
						userId);
		return new CommentPageResponse(items, safePage, safeSize, total, commentTotal);
	}

	@Transactional
	public CommentResponse createComment(String imageId, String userId, CommentRequest request) {
		ensureRetainedImage(imageId);
		String parentCommentId = blankToNull(request.parentCommentId());
		ImageComment comment;
		if (parentCommentId == null) {
			comment = new ImageComment(imageId, userId, requiredText(request.content(), "请输入评论内容", 1000));
		} else {
			ImageComment parent = getCommentForUpdate(parentCommentId);
			if (!parent.imageId().equals(imageId) || parent.status() != ImageCommentStatus.ACTIVE) {
				throw new IllegalArgumentException("回复的评论不存在");
			}
			if (request.parentUpdatedAt() == null || !request.parentUpdatedAt().equals(parent.updatedAt())) {
				throw new IllegalArgumentException("该评论已更新，请刷新后再回复");
			}
			String rootCommentId = blankToNull(parent.rootCommentId()) == null ? parent.id() : parent.rootCommentId();
			comment = new ImageComment(imageId, userId, parent.id(), rootCommentId, parent.depth() + 1,
					requiredText(request.content(), "请输入评论内容", 1000));
		}
		comments.insert(comment);
		auditLogService.record("interaction.comment.create", "IMAGE", imageId,
				Map.of("commentId", comment.id(), "parentCommentId", parentCommentId == null ? "" : parentCommentId));
		return commentResponse(comment.id(), userId, "评论保存失败");
	}

	@Transactional
	public CommentResponse updateComment(String imageId, String commentId, String userId, CommentRequest request) {
		ensureRetainedImage(imageId);
		ImageComment comment = getCommentForUpdate(commentId);
		if (!comment.imageId().equals(imageId) || comment.status() != ImageCommentStatus.ACTIVE) {
			throw new IllegalArgumentException("评论不存在");
		}
		if (!comment.userId().equals(userId)) {
			throw new AccessDeniedException("只能编辑自己的评论");
		}
		if (comments.countByParentId(comment.id()) > 0) {
			throw new IllegalArgumentException("已有回复的评论不能编辑");
		}
		comment.updateContent(requiredText(request.content(), "请输入评论内容", 1000));
		comments.updateById(comment);
		auditLogService.record("interaction.comment.update", "IMAGE", imageId, Map.of("commentId", comment.id()));
		return commentResponse(comment.id(), userId, "评论更新失败");
	}

	@Transactional
	public void deleteComment(String imageId, String commentId, String userId, boolean manage) {
		ensureRetainedImage(imageId);
		ImageComment comment = getComment(commentId);
		if (!comment.imageId().equals(imageId) || comment.status() != ImageCommentStatus.ACTIVE) {
			return;
		}
		if (!manage && !comment.userId().equals(userId)) {
			throw new AccessDeniedException("只能删除自己的评论");
		}
		comment.delete();
		comments.updateById(comment);
		auditLogService.record("interaction.comment.delete", "IMAGE", imageId,
				Map.of("commentId", comment.id(), "managed", manage));
	}

	@Transactional
	public InteractionStateResponse favorite(String imageId, String userId) {
		ensureRetainedImage(imageId);
		favorites.insertIgnore(UUID.randomUUID().toString(), imageId, userId);
		auditLogService.record("interaction.favorite", "IMAGE", imageId, Map.of("enabled", true));
		return InteractionStateResponse.from(summary(imageId, userId));
	}

	@Transactional
	public InteractionStateResponse unfavorite(String imageId, String userId) {
		ensureRetainedImage(imageId);
		favorites.deleteByImageIdAndUserId(imageId, userId);
		auditLogService.record("interaction.favorite", "IMAGE", imageId, Map.of("enabled", false));
		return InteractionStateResponse.from(summary(imageId, userId));
	}

	@Transactional
	public InteractionStateResponse like(String imageId, String userId) {
		ensureRetainedImage(imageId);
		likes.insertIgnore(UUID.randomUUID().toString(), imageId, userId);
		auditLogService.record("interaction.like", "IMAGE", imageId, Map.of("enabled", true));
		return InteractionStateResponse.from(summary(imageId, userId));
	}

	@Transactional
	public InteractionStateResponse unlike(String imageId, String userId) {
		ensureRetainedImage(imageId);
		likes.deleteByImageIdAndUserId(imageId, userId);
		auditLogService.record("interaction.like", "IMAGE", imageId, Map.of("enabled", false));
		return InteractionStateResponse.from(summary(imageId, userId));
	}

	@Transactional(readOnly = true)
	public FeedbackPageResponse myFeedback(String userId, String status, int page, int size) {
		FeedbackStatus statusFilter = parseNullableStatus(status);
		int safePage = Math.max(1, page);
		int safeSize = Math.min(Math.max(1, size), 100);
		long total = feedback.countMine(userId, statusFilter);
		List<FeedbackResponse> items = total == 0 ? List.of() : feedback.selectMine(userId, statusFilter,
						(long) (safePage - 1) * safeSize, safeSize).stream()
				.map(FeedbackResponse::from)
				.toList();
		return new FeedbackPageResponse(items, safePage, safeSize, total);
	}

	@Transactional
	public FeedbackResponse createFeedback(String userId, FeedbackCreateRequest request) {
		String imageId = blankToNull(request.imageId());
		if (imageId != null) {
			ensureRetainedImage(imageId);
		}
		UserFeedback saved = new UserFeedback(userId, imageId, normalizeType(request.type()),
				requiredText(request.title(), "请输入反馈标题", 160),
				requiredText(request.content(), "请输入反馈内容", 2000));
		feedback.insert(saved);
		auditLogService.record("interaction.feedback.create", "USER_FEEDBACK", saved.id(),
				Map.of("imageId", imageId == null ? "" : imageId));
		return feedbackResponse(saved.id(), "反馈保存失败");
	}

	@Transactional
	public void closeFeedback(String userId, String id) {
		UserFeedback item = getFeedback(id);
		if (!item.userId().equals(userId)) {
			throw new AccessDeniedException("只能关闭自己的反馈");
		}
		item.close();
		feedback.updateById(item);
		auditLogService.record("interaction.feedback.close", "USER_FEEDBACK", item.id(), Map.of());
	}

	@Transactional(readOnly = true)
	public FeedbackPageResponse adminFeedback(String keyword, String status, int page, int size) {
		String query = blankToNull(keyword);
		FeedbackStatus statusFilter = parseNullableStatus(status);
		int safePage = Math.max(1, page);
		int safeSize = Math.min(Math.max(1, size), 100);
		long total = feedback.countAdmin(query, statusFilter);
		List<FeedbackResponse> items = total == 0 ? List.of() : feedback.selectAdmin(query, statusFilter,
						(long) (safePage - 1) * safeSize, safeSize).stream()
				.map(FeedbackResponse::from)
				.toList();
		return new FeedbackPageResponse(items, safePage, safeSize, total);
	}

	@Transactional
	public FeedbackResponse handleFeedback(String handlerId, String id, FeedbackHandleRequest request) {
		UserFeedback item = getFeedback(id);
		FeedbackStatus status = FeedbackStatus.parse(request.status());
		item.handle(status, optionalText(request.response(), 2000), handlerId);
		feedback.updateById(item);
		auditLogService.record("interaction.feedback.handle", "USER_FEEDBACK", item.id(),
				Map.of("status", status.name()));
		return feedbackResponse(item.id(), "反馈处理失败");
	}

	@Transactional
	public void cleanupForImagePurge(String imageId) {
		comments.deleteByImageId(imageId);
		favorites.deleteByImageId(imageId);
		likes.deleteByImageId(imageId);
		feedback.clearImageId(imageId);
	}

	@Transactional
	public void cleanupForUserPurge(String userId) {
		comments.deleteByUserId(userId);
		favorites.deleteByUserId(userId);
		likes.deleteByUserId(userId);
		feedback.deleteByUserId(userId);
	}

	private CommentResponse commentResponse(String id, String currentUserId, String errorMessage) {
		ImageCommentRow row = comments.selectRowById(id);
		if (row == null) {
			throw new IllegalStateException(errorMessage);
		}
		return toCommentResponse(new CommentNode(row), currentUserId);
	}

	private FeedbackResponse feedbackResponse(String id, String errorMessage) {
		UserFeedbackRow row = feedback.selectRowById(id);
		if (row == null) {
			throw new IllegalStateException(errorMessage);
		}
		return FeedbackResponse.from(row);
	}

	private void ensureRetainedImage(String imageId) {
		if (imageId == null || imageId.isBlank() || images.countRetainedById(imageId) == 0) {
			throw new IllegalArgumentException("图片不存在");
		}
	}

	private ImageComment getComment(String id) {
		ImageComment comment = comments.selectById(id);
		if (comment == null) {
			throw new IllegalArgumentException("评论不存在");
		}
		return comment;
	}

	private ImageComment getCommentForUpdate(String id) {
		ImageComment comment = comments.selectByIdForUpdate(id);
		if (comment == null) {
			throw new IllegalArgumentException("评论不存在");
		}
		return comment;
	}

	private UserFeedback getFeedback(String id) {
		UserFeedback item = feedback.selectById(id);
		if (item == null) {
			throw new IllegalArgumentException("反馈不存在");
		}
		return item;
	}

	private static FeedbackStatus parseNullableStatus(String status) {
		return status == null || status.isBlank() ? null : FeedbackStatus.parse(status);
	}

	private static String normalizeType(String value) {
		String type = value == null || value.isBlank() ? "GENERAL" : value.trim().toUpperCase();
		if (type.length() > 40) {
			throw new IllegalArgumentException("反馈类型不能超过 40 个字符");
		}
		return type;
	}

	private static String requiredText(String value, String blankMessage, int maxLength) {
		String text = blankToNull(value);
		if (text == null) {
			throw new IllegalArgumentException(blankMessage);
		}
		if (text.length() > maxLength) {
			throw new IllegalArgumentException("内容不能超过 %d 个字符".formatted(maxLength));
		}
		return text;
	}

	private static String optionalText(String value, int maxLength) {
		String text = blankToNull(value);
		if (text != null && text.length() > maxLength) {
			throw new IllegalArgumentException("内容不能超过 %d 个字符".formatted(maxLength));
		}
		return text;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static List<CommentResponse> buildCommentTree(List<ImageCommentRow> roots, List<ImageCommentRow> rows,
			String currentUserId) {
		Map<String, CommentNode> nodes = new LinkedHashMap<>();
		for (ImageCommentRow row : rows) {
			nodes.put(row.id(), new CommentNode(row));
		}
		for (CommentNode node : nodes.values()) {
			String parentId = node.row.parentCommentId();
			CommentNode parent = parentId == null ? null : nodes.get(parentId);
			if (parent != null && !parent.row.id().equals(node.row.id())) {
				parent.children.add(node);
				parent.hasReplies = true;
			}
		}
		List<CommentResponse> result = new ArrayList<>();
		for (int i = 0; i < roots.size(); i++) {
			CommentNode root = nodes.get(roots.get(i).id());
			if (root != null && pruneInvisibleDeletedLeaves(root)) {
				result.add(toCommentResponse(root, currentUserId));
			}
		}
		return List.copyOf(result);
	}

	private static boolean pruneInvisibleDeletedLeaves(CommentNode node) {
		node.children.removeIf(child -> !pruneInvisibleDeletedLeaves(child));
		return "ACTIVE".equals(node.row.status()) || !node.children.isEmpty();
	}

	private static CommentResponse toCommentResponse(CommentNode node, String currentUserId) {
		ImageCommentRow row = node.row;
		boolean deleted = !"ACTIVE".equals(row.status());
		List<CommentResponse> replies = new ArrayList<>();
		for (int i = 0; i < node.children.size(); i++) {
			replies.add(toCommentResponse(node.children.get(i), currentUserId));
		}
		return new CommentResponse(row.id(), row.imageId(), row.userId(), row.authorName(),
				UserAvatars.url(row.userId(), row.authorAvatarObjectKey(), row.authorAvatarUpdatedAt()),
				deleted ? null : row.content(), row.status(), row.createdAt(), row.updatedAt(),
				row.userId().equals(currentUserId), row.parentCommentId(), row.rootCommentId(), row.depth(), deleted,
				node.hasReplies || row.hasReplies(), List.copyOf(replies));
	}

	private static final class CommentNode {
		private final ImageCommentRow row;
		private final List<CommentNode> children = new ArrayList<>();
		private boolean hasReplies;

		private CommentNode(ImageCommentRow row) {
			this.row = row;
		}
	}
}
