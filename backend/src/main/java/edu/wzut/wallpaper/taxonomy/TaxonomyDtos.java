package edu.wzut.wallpaper.taxonomy;

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

	public record CategoryResponse(String id, String code, String name, int sortOrder, boolean enabled, long tagCount,
			LocalDateTime createdAt, LocalDateTime updatedAt) {
		static CategoryResponse from(Category category, long tagCount) {
			return new CategoryResponse(category.id(), category.code(), category.name(), category.sortOrder(),
					category.enabled(), tagCount, category.createdAt(), category.updatedAt());
		}
	}

	public record TagRequest(@NotBlank @Size(max = 120) String name, Integer sortOrder, Boolean enabled) {
	}

	public record TagResponse(String id, String categoryId, String name, int sortOrder, boolean enabled) {
		static TagResponse from(Tag tag) {
			return new TagResponse(tag.id(), tag.categoryId(), tag.name(), tag.sortOrder(), tag.enabled());
		}
	}
}
