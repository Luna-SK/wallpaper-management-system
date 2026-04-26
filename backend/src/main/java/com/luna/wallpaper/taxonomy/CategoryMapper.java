package com.luna.wallpaper.taxonomy;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface CategoryMapper extends BaseMapper<Category> {

	@Select("select * from categories order by sort_order asc, name asc")
	List<Category> selectOrdered();

	@Select("select * from categories where code = #{code} limit 1")
	Category selectByCode(@Param("code") String code);

	@Select("select 1 from categories where code = #{code} and id <> #{id} limit 1")
	Integer existsByCodeExcludingId(@Param("code") String code, @Param("id") String id);

	@Select("select count(*) from images where category_id = #{categoryId}")
	long countImagesByCategoryId(@Param("categoryId") String categoryId);

	@Select("select count(*) from upload_batches where category_id = #{categoryId}")
	long countUploadBatchesByCategoryId(@Param("categoryId") String categoryId);

	@Update("update images set category_id = null, updated_at = now(6) where category_id = #{categoryId}")
	int clearImageCategory(@Param("categoryId") String categoryId);

	@Update("update upload_batches set category_id = null, updated_at = now(6) where category_id = #{categoryId}")
	int clearUploadBatchCategory(@Param("categoryId") String categoryId);

	default Optional<Category> findByCode(String code) {
		return Optional.ofNullable(selectByCode(code));
	}

	default boolean hasCodeExcludingId(String code, String id) {
		return existsByCodeExcludingId(code, id) != null;
	}
}
