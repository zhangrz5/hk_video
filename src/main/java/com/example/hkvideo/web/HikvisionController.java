package com.example.hkvideo.web;

import com.example.hkvideo.hikvision.HikvisionVideoService;
import com.example.hkvideo.web.dto.CameraPageResponse;
import com.example.hkvideo.web.dto.CaptureResponse;
import com.example.hkvideo.web.dto.PlaybackRequest;
import com.example.hkvideo.web.dto.PlaybackResponse;
import com.example.hkvideo.web.dto.PreviewRequest;
import com.example.hkvideo.web.dto.PreviewResponse;
import com.example.hkvideo.web.dto.PtzRequest;
import com.example.hkvideo.web.dto.RecordResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/hikvision")
public class HikvisionController {

    private final HikvisionVideoService videoService;

    public HikvisionController(HikvisionVideoService videoService) {
        this.videoService = videoService;
    }

    // ===================== 监控点列表 =====================

    @GetMapping("/cameras")
    public CameraPageResponse cameras(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String regionIndexCode
    ) {
        return videoService.searchCameras(pageNo, pageSize, keyword, regionIndexCode);
    }

    // ===================== 预览取流 =====================

    @PostMapping("/cameras/{cameraIndexCode}/preview")
    public PreviewResponse preview(
            @PathVariable String cameraIndexCode,
            @RequestBody(required = false) PreviewRequest request
    ) {
        return videoService.previewUrl(cameraIndexCode, request);
    }


    // ===================== 录像回放 =====================

    @PostMapping("/cameras/{cameraIndexCode}/playback")
    public PlaybackResponse playback(
            @PathVariable String cameraIndexCode,
            @RequestBody PlaybackRequest request
    ) {
        return videoService.playbackUrl(cameraIndexCode, request);
    }

    // ===================== 云台控制 =====================

    @PostMapping("/cameras/{cameraIndexCode}/ptz")
    public ResponseEntity<Void> ptz(
            @PathVariable String cameraIndexCode,
            @RequestBody PtzRequest request
    ) {
        videoService.ptzControl(cameraIndexCode, request);
        return ResponseEntity.ok().build();
    }

    // ===================== 手动抓图 =====================

    @PostMapping("/cameras/{cameraIndexCode}/capture")
    public CaptureResponse capture(@PathVariable String cameraIndexCode) {
        return videoService.manualCapture(cameraIndexCode);
    }

    // ===================== 手动录像 =====================

    @PostMapping("/cameras/{cameraIndexCode}/record/start")
    public RecordResponse recordStart(
            @PathVariable String cameraIndexCode,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        Integer type = body != null && body.get("type") instanceof Number n ? n.intValue() : null;
        return videoService.startRecord(cameraIndexCode, type);
    }

    @PostMapping("/cameras/{cameraIndexCode}/record/stop")
    public ResponseEntity<Void> recordStop(
            @PathVariable String cameraIndexCode,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        String taskID = body != null ? (String) body.get("taskID") : null;
        videoService.stopRecord(cameraIndexCode, taskID);
        return ResponseEntity.ok().build();
    }

    // ===================== 调试：原始接口透传 =====================

    /**
     * 透传调用海康 OpenAPI，用于调试。
     * 示例：POST /api/hikvision/debug/invoke?path=/api/resource/v1/cameras/status
     * Body: {"indexCodes":["xxx"]}
     */
    @PostMapping("/debug/invoke")
    public Object debugInvoke(
            @RequestParam("path") String path,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return videoService.debugInvoke(path, body != null ? body : Map.of());
    }
}
