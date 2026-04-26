package com.luna.wallpaper.taxonomy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.luna.wallpaper.audit.AuditLogService;
import com.luna.wallpaper.taxonomy.TaxonomyDtos.TagGroupRequest;
import com.luna.wallpaper.taxonomy.TaxonomyDtos.TagRequest;

class TaxonomyServiceTests {

	private final CategoryMapper categories = mock(CategoryMapper.class);
	private final TagGroupMapper tagGroups = mock(TagGroupMapper.class);
	private final TagMapper tags = mock(TagMapper.class);
	private final AuditLogService auditLogService = mock(AuditLogService.class);
	private final TaxonomyService service = new TaxonomyService(categories, tagGroups, tags, auditLogService);

	@Test
	void disablingTagGroupDisablesEnabledTagsInGroup() {
		TagGroup group = new TagGroup("STYLE", "风格", 10);
		when(tagGroups.selectById(group.id())).thenReturn(group);
		when(tags.disableByGroupId(group.id())).thenReturn(3);

		service.updateTagGroup(group.id(), new TagGroupRequest("STYLE", "风格", 10, false));

		verify(tagGroups).updateById(group);
		verify(tags).disableByGroupId(group.id());
		verify(auditLogService).record("taxonomy.tag-group.update", "TAG_GROUP", group.id(),
				"{\"enabled\":false,\"disabledTags\":3}");
	}

	@Test
	void updatingTagCannotEnableTagUnderDisabledGroup() {
		TagGroup group = disabledGroup();
		Tag tag = new Tag(group.id(), "日系", 1);
		when(tags.selectById(tag.id())).thenReturn(tag);
		when(tagGroups.selectById(group.id())).thenReturn(group);

		assertThatThrownBy(() -> service.updateTag(tag.id(), new TagRequest(group.id(), "日系", 1, true)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("标签组已停用，不能启用标签");

		verify(tags, never()).updateById(tag);
	}

	@Test
	void restoringTagRequiresEnabledGroup() {
		TagGroup group = disabledGroup();
		Tag tag = new Tag(group.id(), "日系", 1);
		tag.update(tag.name(), tag.sortOrder(), false);
		when(tags.selectById(tag.id())).thenReturn(tag);
		when(tagGroups.selectById(group.id())).thenReturn(group);

		assertThatThrownBy(() -> service.restoreTag(tag.id()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("选择的标签组已停用");

		verify(tags, never()).updateById(tag);
	}

	private TagGroup disabledGroup() {
		TagGroup group = new TagGroup("STYLE", "风格", 10);
		group.update(group.code(), group.name(), group.sortOrder(), false);
		return group;
	}
}
