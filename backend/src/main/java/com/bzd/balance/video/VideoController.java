package com.bzd.balance.video;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.bzd.balance.auth.CurrentUser;
import com.bzd.balance.auth.SessionUser;
import com.bzd.balance.common.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @PostMapping
    public ApiResponse<VideoView> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "balanceId", required = false) Long balanceId,
                                         HttpSession session,
                                         HttpServletRequest request) {
        SessionUser user = CurrentUser.require(session);
        return ApiResponse.ok(videoService.upload(file, balanceId, user, request.getRemoteAddr()));
    }

    @GetMapping
    public ApiResponse<List<VideoView>> list(HttpSession session) {
        return ApiResponse.ok(videoService.list(CurrentUser.require(session)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpSession session, HttpServletRequest request) {
        videoService.delete(id, CurrentUser.require(session), request.getRemoteAddr());
        return ApiResponse.ok();
    }
}
