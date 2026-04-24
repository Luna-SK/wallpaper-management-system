package com.luna.wallpaper.taxonomy;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.luna.wallpaper.taxonomy.TaxonomyDtos.CategoryRequest;
import com.luna.wallpaper.taxonomy.TaxonomyDtos.CategoryResponse;
import com.luna.wallpaper.taxonomy.TaxonomyDtos.TagRequest;
import com.luna.wallpaper.taxonomy.TaxonomyDtos.TagResponse;

@RestController
@RequestMapping("/api/categories")
class TaxonomyController {

	private final TaxonomyService service;

	TaxonomyController(TaxonomyService service) {
		this.service = service;
	}

	@GetMapping
	List<CategoryResponse> categories() {
		return service.listCategories();
	}

	@PostMapping
	@PreAuthorize("hasAuthority('taxonomy:manage')")
	CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
		return service.createCategory(request);
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasAuthority('taxonomy:manage')")
	CategoryResponse updateCategory(@PathVariable String id, @Valid @RequestBody CategoryRequest request) {
		return service.updateCategory(id, request);
	}

	@GetMapping("/{categoryId}/tags")
	List<TagResponse> tags(@PathVariable String categoryId) {
		return service.listTags(categoryId);
	}

	@PostMapping("/{categoryId}/tags")
	@PreAuthorize("hasAuthority('taxonomy:manage')")
	TagResponse createTag(@PathVariable String categoryId, @Valid @RequestBody TagRequest request) {
		return service.createTag(categoryId, request);
	}

	@PatchMapping("/{categoryId}/tags/{tagId}")
	@PreAuthorize("hasAuthority('taxonomy:manage')")
	TagResponse updateTag(@PathVariable String categoryId, @PathVariable String tagId,
			@Valid @RequestBody TagRequest request) {
		return service.updateTag(categoryId, tagId, request);
	}
}
