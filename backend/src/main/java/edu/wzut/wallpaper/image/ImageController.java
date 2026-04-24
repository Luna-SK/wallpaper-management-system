package edu.wzut.wallpaper.image;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

import edu.wzut.wallpaper.image.ImageDtos.ImageResponse;
import edu.wzut.wallpaper.image.ImageDtos.ImageUpdateRequest;
import edu.wzut.wallpaper.image.ImageDtos.UploadBatchResponse;

@RestController
@RequestMapping("/api")
class ImageController {

	private final ImageService service;

	ImageController(ImageService service) {
		this.service = service;
	}

	@GetMapping("/images")
	@PreAuthorize("hasAuthority('image:view')")
	List<ImageResponse> images(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String categoryId, @RequestParam(required = false) String tagId,
			@RequestParam(defaultValue = "100") int limit) {
		return service.list(keyword, categoryId, tagId, limit);
	}

	@GetMapping("/images/{id}")
	@PreAuthorize("hasAuthority('image:view')")
	ImageResponse detail(@PathVariable String id) {
		return service.detail(id);
	}

	@PatchMapping("/images/{id}")
	@PreAuthorize("hasAuthority('image:edit')")
	ImageResponse update(@PathVariable String id, @RequestBody ImageUpdateRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/images/{id}")
	@PreAuthorize("hasAuthority('image:delete')")
	void delete(@PathVariable String id) {
		service.delete(id);
	}

	@PostMapping(path = "/images/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('image:upload')")
	UploadBatchResponse upload(@RequestParam("files") List<MultipartFile> files,
			@RequestParam(required = false) String categoryId, @RequestParam(required = false) List<String> tagIds) {
		return service.upload(files, categoryId, tagIds);
	}

	@GetMapping("/image-upload-batches/{id}")
	@PreAuthorize("hasAuthority('image:upload')")
	UploadBatchResponse batch(@PathVariable String id) {
		return service.batch(id);
	}

	@GetMapping("/image-upload-batches/{id}/events")
	SseEmitter batchEvents(@PathVariable String id) throws Exception {
		SseEmitter emitter = new SseEmitter(5_000L);
		emitter.send(SseEmitter.event().name("batch").data(service.batch(id)));
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
}
