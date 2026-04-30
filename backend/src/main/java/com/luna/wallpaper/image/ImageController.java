package com.luna.wallpaper.image;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.luna.wallpaper.image.ImageDtos.ImageBatchRequest;
import com.luna.wallpaper.image.ImageDtos.ImagePageResponse;
import com.luna.wallpaper.image.ImageDtos.ImagePurgeResponse;
import com.luna.wallpaper.image.ImageDtos.ImageResponse;
import com.luna.wallpaper.image.ImageDtos.ImageUpdateRequest;
import com.luna.wallpaper.image.ImageDtos.ImageVersionResponse;
import com.luna.wallpaper.image.ImageDtos.UploadBatchResponse;
import com.luna.wallpaper.image.ImageDtos.UploadSettingsResponse;
import com.luna.wallpaper.image.ImageDtos.UploadSessionCreateRequest;
import com.luna.wallpaper.rbac.AuthenticatedUser;

@RestController
@RequestMapping("/api")
class ImageController {

	private final ImageService service;

	ImageController(ImageService service) {
		this.service = service;
	}

	@GetMapping("/images")
	@PreAuthorize("hasAuthority('image:view')")
	ImagePageResponse images(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String categoryId, @RequestParam(required = false) String tagId,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "false") boolean favoriteOnly,
			@RequestParam(required = false) String sortBy, @RequestParam(required = false) String sortDirection,
			Authentication authentication,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(keyword, categoryId, tagId, status, favoriteOnly, currentUserId(authentication),
				sortBy, sortDirection, page, size);
	}

	@GetMapping("/images/{id}")
	@PreAuthorize("hasAuthority('image:view')")
	ImageResponse detail(@PathVariable String id, Authentication authentication) {
		return service.detail(id, currentUserId(authentication));
	}

	@PatchMapping("/images/{id}")
	@PreAuthorize("hasAuthority('image:edit')")
	ImageResponse update(@PathVariable String id, @RequestBody ImageUpdateRequest request) {
		return service.update(id, request);
	}

	@PostMapping(path = "/images/{id}/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('image:edit')")
	ImageResponse editImage(@PathVariable String id, @RequestParam("file") MultipartFile file,
			@RequestParam(required = false) String operations) {
		return service.editImage(id, file, operations);
	}

	@GetMapping("/images/{id}/versions")
	@PreAuthorize("hasAuthority('image:view')")
	List<ImageVersionResponse> versions(@PathVariable String id) {
		return service.versions(id);
	}

	@GetMapping("/images/{id}/versions/{versionId}/thumbnail")
	@PreAuthorize("hasAuthority('image:view')")
	ResponseEntity<byte[]> versionThumbnail(@PathVariable String id, @PathVariable String versionId) {
		return inline(service.versionThumbnail(id, versionId));
	}

	@GetMapping("/images/{id}/versions/{versionId}/preview")
	@PreAuthorize("hasAuthority('image:view')")
	ResponseEntity<byte[]> versionPreview(@PathVariable String id, @PathVariable String versionId) {
		return inline(service.versionPreview(id, versionId));
	}

	@PostMapping("/images/{id}/versions/{versionId}/restore")
	@PreAuthorize("hasAuthority('image:edit')")
	ImageResponse restoreVersion(@PathVariable String id, @PathVariable String versionId) {
		return service.restoreVersion(id, versionId);
	}

	@DeleteMapping("/images/{id}/versions/{versionId}")
	@PreAuthorize("hasAuthority('image:delete')")
	void deleteVersion(@PathVariable String id, @PathVariable String versionId) {
		service.deleteVersion(id, versionId);
	}

	@DeleteMapping("/images/{id}")
	@PreAuthorize("hasAuthority('image:delete')")
	void delete(@PathVariable String id) {
		service.delete(id);
	}

	@PostMapping("/images/batch-disable")
	@PreAuthorize("hasAuthority('image:delete')")
	void batchDisable(@RequestBody ImageBatchRequest request) {
		service.batchDisable(request);
	}

	@PostMapping("/images/{id}/restore")
	@PreAuthorize("hasAuthority('image:delete')")
	void restore(@PathVariable String id) {
		service.restore(id);
	}

	@PostMapping("/images/batch-restore")
	@PreAuthorize("hasAuthority('image:delete')")
	void batchRestore(@RequestBody ImageBatchRequest request) {
		service.batchRestore(request);
	}

	@DeleteMapping("/images/{id}/purge")
	@PreAuthorize("hasAuthority('image:delete')")
	void purge(@PathVariable String id) {
		service.purge(id);
	}

	@PostMapping("/images/batch-purge")
	@PreAuthorize("hasAuthority('image:delete')")
	void batchPurge(@RequestBody ImageBatchRequest request) {
		service.batchPurge(request);
	}

	@PostMapping("/images/deleted/purge")
	@PreAuthorize("hasAuthority('image:delete')")
	ImagePurgeResponse purgeDeleted() {
		return new ImagePurgeResponse(service.purgeDeleted());
	}

	@PostMapping("/images/batch-download")
	@PreAuthorize("hasAuthority('image:view')")
	ResponseEntity<byte[]> batchDownload(@RequestBody ImageBatchRequest request) {
		ImageService.BatchDownloadFile file = service.batchDownload(request);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/zip"))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
				.body(file.content());
	}

	@PostMapping(path = "/images/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('image:upload')")
	UploadBatchResponse upload(@RequestParam("files") List<MultipartFile> files,
			@RequestParam(required = false) String categoryId, @RequestParam(required = false) List<String> tagIds) {
		return service.upload(files, categoryId, tagIds);
	}

	@GetMapping("/image-upload-settings")
	@PreAuthorize("hasAuthority('image:upload')")
	UploadSettingsResponse uploadSettings() {
		return new UploadSettingsResponse(service.uploadDeduplicationEnabled());
	}

	@PostMapping("/image-upload-sessions")
	@PreAuthorize("hasAuthority('image:upload')")
	UploadBatchResponse createUploadSession(@RequestBody UploadSessionCreateRequest request) {
		return service.createUploadSession(request);
	}

	@PostMapping(path = "/image-upload-sessions/{id}/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('image:upload')")
	UploadBatchResponse stageUploadSessionItem(@PathVariable String id, @RequestParam("file") MultipartFile file,
			@RequestParam(required = false) String title) {
		return service.stageUploadSessionItem(id, file, title);
	}

	@PostMapping(path = "/image-upload-sessions/{id}/items/{itemId}/retry", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('image:upload')")
	UploadBatchResponse retryUploadSessionItem(@PathVariable String id, @PathVariable String itemId,
			@RequestParam("file") MultipartFile file, @RequestParam(required = false) String title) {
		return service.retryUploadSessionItem(id, itemId, file, title);
	}

	@PostMapping("/image-upload-sessions/{id}/confirm")
	@PreAuthorize("hasAuthority('image:upload')")
	UploadBatchResponse confirmUploadSession(@PathVariable String id) {
		return service.confirmUploadSession(id);
	}

	@PostMapping("/image-upload-sessions/{id}/cancel")
	@PreAuthorize("hasAuthority('image:upload')")
	UploadBatchResponse cancelUploadSession(@PathVariable String id) {
		return service.cancelUploadSession(id);
	}

	@GetMapping("/image-upload-batches/{id}")
	@PreAuthorize("hasAuthority('image:upload')")
	UploadBatchResponse batch(@PathVariable String id) {
		return service.batch(id);
	}

	@GetMapping("/image-upload-sessions/{id}")
	@PreAuthorize("hasAuthority('image:upload')")
	UploadBatchResponse uploadSession(@PathVariable String id) {
		return service.uploadSession(id);
	}

	@GetMapping("/image-upload-batches/{id}/events")
	SseEmitter batchEvents(@PathVariable String id) throws Exception {
		SseEmitter emitter = new SseEmitter(5_000L);
		emitter.send(SseEmitter.event().name("batch").data(service.batch(id)));
		emitter.complete();
		return emitter;
	}

	@GetMapping("/image-upload-sessions/{id}/events")
	SseEmitter uploadSessionEvents(@PathVariable String id) throws Exception {
		SseEmitter emitter = new SseEmitter(5_000L);
		emitter.send(SseEmitter.event().name("session").data(service.uploadSession(id)));
		emitter.complete();
		return emitter;
	}

	@PostMapping("/image-upload-batches/{id}/items/{itemId}/retry")
	@PreAuthorize("hasAuthority('image:upload')")
	UploadBatchResponse retry(@PathVariable String id, @PathVariable String itemId) {
		return service.retry(id, itemId);
	}

	@GetMapping("/images/{id}/thumbnail")
	@PreAuthorize("hasAuthority('image:view')")
	ResponseEntity<byte[]> thumbnail(@PathVariable String id) {
		return inline(service.thumbnail(id));
	}

	@GetMapping("/images/{id}/preview")
	@PreAuthorize("hasAuthority('image:view')")
	ResponseEntity<byte[]> preview(@PathVariable String id) {
		return inline(service.preview(id));
	}

	@GetMapping("/images/{id}/download")
	@PreAuthorize("hasAuthority('image:view')")
	ResponseEntity<byte[]> download(@PathVariable String id) {
		ImageService.ObjectFile file = service.download(id);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.mimeType()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
				.body(service.read(file));
	}

	@GetMapping("/images/{id}/edit-source")
	@PreAuthorize("hasAuthority('image:edit')")
	ResponseEntity<byte[]> editSource(@PathVariable String id) {
		return inline(service.editSource(id));
	}

	@GetMapping("/statistics")
	@PreAuthorize("hasAuthority('image:view')")
	ImageService.Statistics statistics() {
		return service.statistics();
	}

	private ResponseEntity<byte[]> inline(ImageService.ObjectFile file) {
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.mimeType()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.inline().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
				.body(service.read(file));
	}

	private String currentUserId(Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
			return user.id();
		}
		throw new IllegalArgumentException("请先登录");
	}
}
