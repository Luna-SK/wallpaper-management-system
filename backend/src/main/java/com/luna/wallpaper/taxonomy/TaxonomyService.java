package com.luna.wallpaper.taxonomy;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luna.wallpaper.audit.AuditLogService;
import com.luna.wallpaper.taxonomy.TaxonomyDtos.CategoryRequest;
import com.luna.wallpaper.taxonomy.TaxonomyDtos.CategoryResponse;
import com.luna.wallpaper.taxonomy.TaxonomyDtos.ReferenceImpact;
import com.luna.wallpaper.taxonomy.TaxonomyDtos.TagGroupRequest;
import com.luna.wallpaper.taxonomy.TaxonomyDtos.TagGroupResponse;
import com.luna.wallpaper.taxonomy.TaxonomyDtos.TagRequest;
import com.luna.wallpaper.taxonomy.TaxonomyDtos.TagResponse;

@Service
public class TaxonomyService {

	private final CategoryMapper categories;
	private final TagGroupMapper tagGroups;
	private final TagMapper tags;
	private final AuditLogService auditLogService;

	TaxonomyService(CategoryMapper categories, TagGroupMapper tagGroups, TagMapper tags, AuditLogService auditLogService) {
		this.categories = categories;
		this.tagGroups = tagGroups;
		this.tags = tags;
		this.auditLogService = auditLogService;
	}

	@Transactional(readOnly = true)
	public List<CategoryResponse> listCategories() {
		return categories.selectOrdered().stream()
				.map(category -> CategoryResponse.from(category, categories.countImagesByCategoryId(category.id())))
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
		categories.insert(category);
		auditLogService.record("taxonomy.category.create", "CATEGORY", category.id(),
				"{\"name\":\"" + escape(category.name()) + "\"}");
		return CategoryResponse.from(category, 0);
	}

	@Transactional
	public CategoryResponse updateCategory(String id, CategoryRequest request) {
		Category category = getCategory(id);
		String code = normalizeCode(request.code());
		if (categories.hasCodeExcludingId(code, id)) {
			throw new IllegalArgumentException("分类编码已存在");
		}
		category.update(code, request.name().trim(), request.sortOrder() == null ? 0 : request.sortOrder(),
				request.enabled() == null || request.enabled());
		categories.updateById(category);
		auditLogService.record("taxonomy.category.update", "CATEGORY", category.id(),
				"{\"enabled\":" + category.enabled() + "}");
		return CategoryResponse.from(category, categories.countImagesByCategoryId(category.id()));
	}

	@Transactional
	public CategoryResponse restoreCategory(String id) {
		Category category = getCategory(id);
		category.update(category.code(), category.name(), category.sortOrder(), true);
		categories.updateById(category);
		auditLogService.record("taxonomy.category.restore", "CATEGORY", id, "{}");
		return CategoryResponse.from(category, categories.countImagesByCategoryId(id));
	}

	@Transactional
	public ReferenceImpact purgeCategory(String id, boolean force) {
		Category category = getCategory(id);
		requireDisabled(category.enabled(), "请先停用分类，再彻底删除");
		ReferenceImpact impact = categoryImpact(id);
		if (impact.hasReferences() && !force) {
			throw new TaxonomyReferenceException("分类仍被图片或上传会话引用", impact);
		}
		categories.clearImageCategory(id);
		categories.clearUploadBatchCategory(id);
		categories.deleteById(id);
		auditLogService.record("taxonomy.category.purge", "CATEGORY", id, "{\"force\":" + force + "}");
		return impact;
	}

	@Transactional(readOnly = true)
	public List<TagGroupResponse> listTagGroups() {
		return tagGroups.selectOrdered().stream()
				.map(group -> TagGroupResponse.from(group, tagGroups.countTagsByGroupId(group.id())))
				.toList();
	}

	@Transactional
	public TagGroupResponse createTagGroup(TagGroupRequest request) {
		TagGroup group = new TagGroup(normalizeCode(request.code()), request.name().trim(),
				request.sortOrder() == null ? 0 : request.sortOrder());
		if (tagGroups.findByCode(group.code()).isPresent()) {
			throw new IllegalArgumentException("标签组编码已存在");
		}
		group.update(group.code(), group.name(), group.sortOrder(), request.enabled() == null || request.enabled());
		tagGroups.insert(group);
		auditLogService.record("taxonomy.tag-group.create", "TAG_GROUP", group.id(),
				"{\"name\":\"" + escape(group.name()) + "\"}");
		return TagGroupResponse.from(group, 0);
	}

	@Transactional
	public TagGroupResponse updateTagGroup(String id, TagGroupRequest request) {
		TagGroup group = getTagGroup(id);
		boolean wasEnabled = group.enabled();
		String code = normalizeCode(request.code());
		if (tagGroups.hasCodeExcludingId(code, id)) {
			throw new IllegalArgumentException("标签组编码已存在");
		}
		group.update(code, request.name().trim(), request.sortOrder() == null ? 0 : request.sortOrder(),
				request.enabled() == null || request.enabled());
		tagGroups.updateById(group);
		int disabledTags = wasEnabled && !group.enabled() ? tags.disableByGroupId(id) : 0;
		auditLogService.record("taxonomy.tag-group.update", "TAG_GROUP", id,
				"{\"enabled\":" + group.enabled() + ",\"disabledTags\":" + disabledTags + "}");
		return TagGroupResponse.from(group, tagGroups.countTagsByGroupId(id));
	}

	@Transactional
	public TagGroupResponse restoreTagGroup(String id) {
		TagGroup group = getTagGroup(id);
		group.update(group.code(), group.name(), group.sortOrder(), true);
		tagGroups.updateById(group);
		auditLogService.record("taxonomy.tag-group.restore", "TAG_GROUP", id, "{}");
		return TagGroupResponse.from(group, tagGroups.countTagsByGroupId(id));
	}

	@Transactional
	public ReferenceImpact purgeTagGroup(String id, boolean force) {
		TagGroup group = getTagGroup(id);
		requireDisabled(group.enabled(), "请先停用标签组，再彻底删除");
		ReferenceImpact impact = tagGroupImpact(id);
		if (impact.hasReferences() && !force) {
			throw new TaxonomyReferenceException("标签组仍包含标签或被图片、上传会话引用", impact);
		}
		tagGroups.deleteImageRefsByGroupId(id);
		tagGroups.deleteUploadBatchRefsByGroupId(id);
		tagGroups.deleteTagsByGroupId(id);
		tagGroups.deleteById(id);
		auditLogService.record("taxonomy.tag-group.purge", "TAG_GROUP", id, "{\"force\":" + force + "}");
		return impact;
	}

	@Transactional(readOnly = true)
	public List<TagResponse> listTags(String groupId) {
		List<Tag> result = groupId == null || groupId.isBlank()
				? tags.selectOrderedWithGroup()
				: tags.selectByGroupIdOrdered(groupId.trim());
		return result.stream().map(TagResponse::from).toList();
	}

	@Transactional
	public TagResponse createTag(TagRequest request) {
		TagGroup group = requireEnabledGroup(request.groupId());
		String name = request.name().trim();
		if (tags.hasGroupNameExcludingId(group.id(), name, "")) {
			throw new IllegalArgumentException("该标签组下标签名称已存在");
		}
		Tag tag = new Tag(group.id(), name, request.sortOrder() == null ? 0 : request.sortOrder());
		if (request.enabled() != null) {
			tag.update(tag.name(), tag.sortOrder(), request.enabled());
		}
		tags.insert(tag);
		tag.attachGroup(group);
		auditLogService.record("taxonomy.tag.create", "TAG", tag.id(), "{\"name\":\"" + escape(tag.name()) + "\"}");
		return TagResponse.from(tag);
	}

	@Transactional
	public TagResponse updateTag(String tagId, TagRequest request) {
		Tag tag = getTag(tagId);
		TagGroup group = getTagGroup(request.groupId());
		String name = request.name().trim();
		if (tags.hasGroupNameExcludingId(group.id(), name, tagId)) {
			throw new IllegalArgumentException("该标签组下标签名称已存在");
		}
		boolean enabled = request.enabled() == null || request.enabled();
		if (!group.enabled() && enabled) {
			throw new IllegalArgumentException("标签组已停用，不能启用标签");
		}
		tag.moveToGroup(group.id());
		tag.update(name, request.sortOrder() == null ? 0 : request.sortOrder(), enabled);
		tags.updateById(tag);
		tag.attachGroup(group);
		auditLogService.record("taxonomy.tag.update", "TAG", tag.id(), "{\"enabled\":" + tag.enabled() + "}");
		return TagResponse.from(tag);
	}

	@Transactional
	public TagResponse restoreTag(String id) {
		Tag tag = getTag(id);
		TagGroup group = requireEnabledGroup(tag.groupId());
		tag.update(tag.name(), tag.sortOrder(), true);
		tags.updateById(tag);
		tag.attachGroup(group);
		auditLogService.record("taxonomy.tag.restore", "TAG", id, "{}");
		return TagResponse.from(tag);
	}

	@Transactional
	public ReferenceImpact purgeTag(String id, boolean force) {
		Tag tag = getTag(id);
		requireDisabled(tag.enabled(), "请先停用标签，再彻底删除");
		ReferenceImpact impact = tagImpact(id);
		if (impact.hasReferences() && !force) {
			throw new TaxonomyReferenceException("标签仍被图片或上传会话引用", impact);
		}
		tags.deleteImageRefsByTagId(id);
		tags.deleteUploadBatchRefsByTagId(id);
		tags.deleteById(id);
		auditLogService.record("taxonomy.tag.purge", "TAG", id, "{\"force\":" + force + "}");
		return impact;
	}

	Category getCategory(String id) {
		return Optional.ofNullable(categories.selectById(id))
				.orElseThrow(() -> new IllegalArgumentException("分类不存在"));
	}

	TagGroup getTagGroup(String id) {
		return Optional.ofNullable(tagGroups.selectById(id))
				.orElseThrow(() -> new IllegalArgumentException("标签组不存在"));
	}

	Tag getTag(String id) {
		return Optional.ofNullable(tags.selectById(id))
				.orElseThrow(() -> new IllegalArgumentException("标签不存在"));
	}

	private TagGroup requireEnabledGroup(String id) {
		TagGroup group = getTagGroup(id);
		if (!group.enabled()) {
			throw new IllegalArgumentException("选择的标签组已停用");
		}
		return group;
	}

	private ReferenceImpact categoryImpact(String id) {
		return new ReferenceImpact("CATEGORY", id, categories.countImagesByCategoryId(id),
				categories.countUploadBatchesByCategoryId(id), 0);
	}

	private ReferenceImpact tagGroupImpact(String id) {
		return new ReferenceImpact("TAG_GROUP", id, tagGroups.countImageRefsByGroupId(id),
				tagGroups.countUploadBatchRefsByGroupId(id), tagGroups.countTagsByGroupId(id));
	}

	private ReferenceImpact tagImpact(String id) {
		return new ReferenceImpact("TAG", id, tags.countImageRefsByTagId(id), tags.countUploadBatchRefsByTagId(id), 0);
	}

	private static void requireDisabled(boolean enabled, String message) {
		if (enabled) {
			throw new IllegalArgumentException(message);
		}
	}

	private static String normalizeCode(String code) {
		return code.trim().toUpperCase().replace(' ', '_');
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
