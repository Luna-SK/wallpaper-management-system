package com.luna.wallpaper.taxonomy;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.luna.wallpaper.taxonomy.TaxonomyDtos.CategoryRequest;
import com.luna.wallpaper.taxonomy.TaxonomyDtos.CategoryResponse;
import com.luna.wallpaper.taxonomy.TaxonomyDtos.ReferenceImpact;
import com.luna.wallpaper.taxonomy.TaxonomyDtos.TagGroupRequest;
import com.luna.wallpaper.taxonomy.TaxonomyDtos.TagGroupResponse;
import com.luna.wallpaper.taxonomy.TaxonomyDtos.TagRequest;
import com.luna.wallpaper.taxonomy.TaxonomyDtos.TagResponse;

@RestController
class TaxonomyController {

	private final TaxonomyService service;

	TaxonomyController(TaxonomyService service) {
		this.service = service;
	}

	@GetMapping("/api/categories")
	List<CategoryResponse> categories() {
		return service.listCategories();
	}

	@PostMapping("/api/categories")
	@PreAuthorize("hasAuthority('taxonomy:manage')")
	CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
		return service.createCategory(request);
	}

	@PatchMapping("/api/categories/{id}")
	@PreAuthorize("hasAuthority('taxonomy:manage')")
	CategoryResponse updateCategory(@PathVariable String id, @Valid @RequestBody CategoryRequest request) {
		return service.updateCategory(id, request);
	}

	@PostMapping("/api/categories/{id}/restore")
	@PreAuthorize("hasAuthority('taxonomy:manage')")
	CategoryResponse restoreCategory(@PathVariable String id) {
		return service.restoreCategory(id);
	}

	@DeleteMapping("/api/categories/{id}/purge")
	@PreAuthorize("hasAuthority('taxonomy:manage')")
	ReferenceImpact purgeCategory(@PathVariable String id, @RequestParam(defaultValue = "false") boolean force) {
		return service.purgeCategory(id, force);
	}

	@GetMapping("/api/tag-groups")
	List<TagGroupResponse> tagGroups() {
		return service.listTagGroups();
	}

	@PostMapping("/api/tag-groups")
	@PreAuthorize("hasAuthority('taxonomy:manage')")
	TagGroupResponse createTagGroup(@Valid @RequestBody TagGroupRequest request) {
		return service.createTagGroup(request);
	}

	@PatchMapping("/api/tag-groups/{id}")
	@PreAuthorize("hasAuthority('taxonomy:manage')")
	TagGroupResponse updateTagGroup(@PathVariable String id, @Valid @RequestBody TagGroupRequest request) {
		return service.updateTagGroup(id, request);
	}

	@PostMapping("/api/tag-groups/{id}/restore")
	@PreAuthorize("hasAuthority('taxonomy:manage')")
	TagGroupResponse restoreTagGroup(@PathVariable String id) {
		return service.restoreTagGroup(id);
	}

	@DeleteMapping("/api/tag-groups/{id}/purge")
	@PreAuthorize("hasAuthority('taxonomy:manage')")
	ReferenceImpact purgeTagGroup(@PathVariable String id, @RequestParam(defaultValue = "false") boolean force) {
		return service.purgeTagGroup(id, force);
	}

	@GetMapping("/api/tags")
	List<TagResponse> tags(@RequestParam(required = false) String groupId) {
		return service.listTags(groupId);
	}

	@PostMapping("/api/tags")
	@PreAuthorize("hasAuthority('taxonomy:manage')")
	TagResponse createTag(@Valid @RequestBody TagRequest request) {
		return service.createTag(request);
	}

	@PatchMapping("/api/tags/{id}")
	@PreAuthorize("hasAuthority('taxonomy:manage')")
	TagResponse updateTag(@PathVariable String id, @Valid @RequestBody TagRequest request) {
		return service.updateTag(id, request);
	}

	@PostMapping("/api/tags/{id}/restore")
	@PreAuthorize("hasAuthority('taxonomy:manage')")
	TagResponse restoreTag(@PathVariable String id) {
		return service.restoreTag(id);
	}

	@DeleteMapping("/api/tags/{id}/purge")
	@PreAuthorize("hasAuthority('taxonomy:manage')")
	ReferenceImpact purgeTag(@PathVariable String id, @RequestParam(defaultValue = "false") boolean force) {
		return service.purgeTag(id, force);
	}
}
