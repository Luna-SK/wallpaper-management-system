package edu.wzut.wallpaper.taxonomy;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.wzut.wallpaper.audit.AuditLogService;
import edu.wzut.wallpaper.taxonomy.TaxonomyDtos.CategoryRequest;
import edu.wzut.wallpaper.taxonomy.TaxonomyDtos.CategoryResponse;
import edu.wzut.wallpaper.taxonomy.TaxonomyDtos.TagRequest;
import edu.wzut.wallpaper.taxonomy.TaxonomyDtos.TagResponse;

@Service
public class TaxonomyService {

	private final CategoryRepository categories;
	private final TagRepository tags;
	private final AuditLogService auditLogService;

	TaxonomyService(CategoryRepository categories, TagRepository tags, AuditLogService auditLogService) {
		this.categories = categories;
		this.tags = tags;
		this.auditLogService = auditLogService;
	}

	@Transactional(readOnly = true)
	public List<CategoryResponse> listCategories() {
		return categories.findAllByOrderBySortOrderAscNameAsc().stream()
				.map(category -> CategoryResponse.from(category, tags.countByCategoryId(category.id())))
				.toList();
	}

	@Transactional
	public CategoryResponse createCategory(CategoryRequest request) {
		Category category = new Category(normalizeCode(request.code()), request.name().trim(),
				request.sortOrder() == null ? 0 : request.sortOrder());
		if (categories.findByCode(category.code()).isPresent()) {
			throw new IllegalArgumentException("分类编码已存在");
		}
		category.update(category.code(), category.name(), category.sortOrder(), request.enabled() == null || request.enabled());
		category = categories.save(category);
		auditLogService.record("taxonomy.category.create", "CATEGORY", category.id(),
				"{\"name\":\"" + escape(category.name()) + "\"}");
		return CategoryResponse.from(category, 0);
	}

	@Transactional
	public CategoryResponse updateCategory(String id, CategoryRequest request) {
		Category category = getCategory(id);
		String code = normalizeCode(request.code());
		if (categories.existsByCodeAndIdNot(code, id)) {
			throw new IllegalArgumentException("分类编码已存在");
		}
		category.update(code, request.name().trim(), request.sortOrder() == null ? 0 : request.sortOrder(),
				request.enabled() == null || request.enabled());
		auditLogService.record("taxonomy.category.update", "CATEGORY", category.id(),
				"{\"enabled\":" + category.enabled() + "}");
		return CategoryResponse.from(category, tags.countByCategoryId(category.id()));
	}

	@Transactional(readOnly = true)
	public List<TagResponse> listTags(String categoryId) {
		requireCategory(categoryId);
		return tags.findByCategoryIdOrderBySortOrderAscNameAsc(categoryId).stream().map(TagResponse::from).toList();
	}

	@Transactional
	public TagResponse createTag(String categoryId, TagRequest request) {
		requireCategory(categoryId);
		String name = request.name().trim();
		if (tags.existsByCategoryIdAndNameAndIdNot(categoryId, name, "")) {
			throw new IllegalArgumentException("该分类下标签名称已存在");
		}
		Tag tag = tags.save(new Tag(categoryId, name, request.sortOrder() == null ? 0 : request.sortOrder()));
		if (request.enabled() != null) {
			tag.update(tag.name(), tag.sortOrder(), request.enabled());
		}
		auditLogService.record("taxonomy.tag.create", "TAG", tag.id(), "{\"name\":\"" + escape(tag.name()) + "\"}");
		return TagResponse.from(tag);
	}

	@Transactional
	public TagResponse updateTag(String categoryId, String tagId, TagRequest request) {
		requireCategory(categoryId);
		Tag tag = tags.findById(tagId).filter(existing -> existing.categoryId().equals(categoryId))
				.orElseThrow(() -> new IllegalArgumentException("标签不存在"));
		String name = request.name().trim();
		if (tags.existsByCategoryIdAndNameAndIdNot(categoryId, name, tagId)) {
			throw new IllegalArgumentException("该分类下标签名称已存在");
		}
		tag.update(name, request.sortOrder() == null ? 0 : request.sortOrder(), request.enabled() == null || request.enabled());
		auditLogService.record("taxonomy.tag.update", "TAG", tag.id(), "{\"enabled\":" + tag.enabled() + "}");
		return TagResponse.from(tag);
	}

	Category getCategory(String id) {
		return categories.findById(id).orElseThrow(() -> new IllegalArgumentException("分类不存在"));
	}

	private void requireCategory(String id) {
		getCategory(id);
	}

	private static String normalizeCode(String code) {
		return code.trim().toUpperCase().replace(' ', '_');
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
