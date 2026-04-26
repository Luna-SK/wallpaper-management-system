package com.luna.wallpaper.taxonomy;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class TaxonomyDtos {

	private TaxonomyDtos() {
	}

	public record CategoryRequest(
			@NotBlank @Size(max = 64) String code,
			@NotBlank @Size(max = 120) String name,
			Integer sortOrder,
			Boolean enabled) {
	}

	public record CategoryResponse(String id, String code, String name, int sortOrder, boolean enabled, long imageCount,
			LocalDateTime createdAt, LocalDateTime updatedAt) {
		static CategoryResponse from(Category category, long imageCount) {
			return new CategoryResponse(category.id(), category.code(), category.name(), category.sortOrder(),
					category.enabled(), imageCount, category.createdAt(), category.updatedAt());
		}
	}

	public record TagGroupRequest(
			@NotBlank @Size(max = 64) String code,
			@NotBlank @Size(max = 120) String name,
			Integer sortOrder,
			Boolean enabled) {
	}

	public record TagGroupResponse(String id, String code, String name, int sortOrder, boolean enabled, long tagCount,
			LocalDateTime createdAt, LocalDateTime updatedAt) {
		static TagGroupResponse from(TagGroup group, long tagCount) {
			return new TagGroupResponse(group.id(), group.code(), group.name(), group.sortOrder(), group.enabled(),
					tagCount, group.createdAt(), group.updatedAt());
		}
	}

	public record TagRequest(@NotBlank String groupId, @NotBlank @Size(max = 120) String name, Integer sortOrder,
			Boolean enabled) {
	}

	public record TagResponse(String id, String groupId, String groupName, String name, int sortOrder, boolean enabled) {
		static TagResponse from(Tag tag) {
			return new TagResponse(tag.id(), tag.groupId(), tag.groupName(), tag.name(), tag.sortOrder(), tag.enabled());
		}
	}

	public record ReferenceImpact(String resourceType, String resourceId, long imageCount, long uploadBatchCount,
			long tagCount) {
		boolean hasReferences() {
			return imageCount > 0 || uploadBatchCount > 0 || tagCount > 0;
		}
	}
}
