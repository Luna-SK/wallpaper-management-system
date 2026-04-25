package com.luna.wallpaper.image;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ImageAssetRepository extends JpaRepository<ImageAsset, String> {
	Optional<ImageAsset> findBySha256AndStatusNot(String sha256, String status);

	List<ImageAsset> findByStatus(String status);

	long countByStatusNot(String status);

	long countByCreatedAtAfterAndStatusNot(LocalDateTime time, String status);

	@Query(value = """
			select distinct image from ImageAsset image
			left join image.category category
			left join image.tags tag
			where ((:status is null and image.status <> 'DELETED') or (:status is not null and image.status = :status))
			  and (:keyword is null or lower(image.title) like lower(concat('%', :keyword, '%'))
			    or lower(image.originalFilename) like lower(concat('%', :keyword, '%'))
			    or lower(tag.name) like lower(concat('%', :keyword, '%')))
			  and (:categoryId is null or category.id = :categoryId)
			  and (:tagId is null or tag.id = :tagId)
			order by image.createdAt desc
			""", countQuery = """
			select count(distinct image) from ImageAsset image
			left join image.category category
			left join image.tags tag
			where ((:status is null and image.status <> 'DELETED') or (:status is not null and image.status = :status))
			  and (:keyword is null or lower(image.title) like lower(concat('%', :keyword, '%'))
			    or lower(image.originalFilename) like lower(concat('%', :keyword, '%'))
			    or lower(tag.name) like lower(concat('%', :keyword, '%')))
			  and (:categoryId is null or category.id = :categoryId)
			  and (:tagId is null or tag.id = :tagId)
			""")
	Page<ImageAsset> search(@Param("keyword") String keyword, @Param("categoryId") String categoryId,
			@Param("tagId") String tagId, @Param("status") String status, Pageable pageable);

	@Query("select coalesce(sum(image.sizeBytes), 0) from ImageAsset image where image.status <> 'DELETED'")
	long totalStorageBytes();

	@Query("select coalesce(sum(image.viewCount), 0) from ImageAsset image where image.status <> 'DELETED'")
	long totalViews();

	@Query("select coalesce(sum(image.downloadCount), 0) from ImageAsset image where image.status <> 'DELETED'")
	long totalDownloads();

	@Query(value = """
			select date(image.created_at), count(*)
			from images image
			where image.status <> 'DELETED'
			  and image.created_at >= :startAt
			group by date(image.created_at)
			order by date(image.created_at) asc
			""", nativeQuery = true)
	List<Object[]> countUploadsByDaySince(@Param("startAt") LocalDateTime startAt);

	@Query(value = """
			select category.id, category.name, count(image.id)
			from images image
			join categories category on category.id = image.category_id
			where image.status <> 'DELETED'
			group by category.id, category.name
			order by count(image.id) desc, category.name asc
			""", nativeQuery = true)
	List<Object[]> countImagesByCategory();

	@Query("select count(image) from ImageAsset image where image.status <> 'DELETED' and image.category is null")
	long countUncategorizedImages();

	List<ImageAsset> findByStatusNotOrderByViewCountDescCreatedAtDesc(String status, Pageable pageable);

	List<ImageAsset> findByStatusNotOrderByDownloadCountDescCreatedAtDesc(String status, Pageable pageable);
}

interface ImageVersionRepository extends JpaRepository<ImageVersion, String> {
	Optional<ImageVersion> findFirstByImageIdOrderByVersionNoDesc(String imageId);
	List<ImageVersion> findByImageIdOrderByVersionNoDesc(String imageId);
}

interface UploadBatchRepository extends JpaRepository<UploadBatch, String> {
	List<UploadBatch> findAllByOrderByCreatedAtDesc(Pageable pageable);
	List<UploadBatch> findByStatusInAndExpiresAtBefore(Collection<String> statuses, LocalDateTime expiresAt);
}

interface UploadBatchItemRepository extends JpaRepository<UploadBatchItem, String> {
	List<UploadBatchItem> findByBatchIdOrderByCreatedAtAsc(String batchId);
	List<UploadBatchItem> findByBatchIdIn(Collection<String> batchIds);
	List<UploadBatchItem> findByStatusIn(Collection<String> statuses);
}
