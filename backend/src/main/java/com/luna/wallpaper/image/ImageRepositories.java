package com.luna.wallpaper.image;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ImageAssetRepository extends JpaRepository<ImageAsset, String> {
	Optional<ImageAsset> findBySha256AndStatusNot(String sha256, String status);

	long countByStatusNot(String status);

	long countByCreatedAtAfterAndStatusNot(LocalDateTime time, String status);

	@Query("""
			select distinct image from ImageAsset image
			left join image.categories category
			left join image.tags tag
			where image.status <> 'DELETED'
			  and (:keyword is null or lower(image.title) like lower(concat('%', :keyword, '%'))
			    or lower(image.originalFilename) like lower(concat('%', :keyword, '%'))
			    or lower(tag.name) like lower(concat('%', :keyword, '%')))
			  and (:categoryId is null or category.id = :categoryId)
			  and (:tagId is null or tag.id = :tagId)
			order by image.createdAt desc
			""")
	List<ImageAsset> search(@Param("keyword") String keyword, @Param("categoryId") String categoryId,
			@Param("tagId") String tagId, Pageable pageable);

	@Query("select coalesce(sum(image.sizeBytes), 0) from ImageAsset image where image.status <> 'DELETED'")
	long totalStorageBytes();

	@Query("select coalesce(sum(image.viewCount), 0) from ImageAsset image")
	long totalViews();

	@Query("select coalesce(sum(image.downloadCount), 0) from ImageAsset image")
	long totalDownloads();
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
