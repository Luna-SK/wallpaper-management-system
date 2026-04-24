package edu.wzut.wallpaper.taxonomy;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, String> {

	List<Tag> findByCategoryIdOrderBySortOrderAscNameAsc(String categoryId);

	List<Tag> findByIdIn(Collection<String> ids);

	boolean existsByCategoryIdAndNameAndIdNot(String categoryId, String name, String id);

	long countByCategoryId(String categoryId);
}
