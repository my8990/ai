package com.example.ai.controller;

import com.example.ai.audit.AuditAction;
import com.example.ai.common.Result;
import com.example.ai.dto.LiveEndDTO;
import com.example.ai.dto.LiveEndPreviewVO;
import com.example.ai.dto.LiveHeartbeatDTO;
import com.example.ai.dto.LiveInteractionVO;
import com.example.ai.dto.LiveJoinDTO;
import com.example.ai.dto.LivePageDTO;
import com.example.ai.dto.LiveQuestionDTO;
import com.example.ai.dto.LiveQuizPublishDTO;
import com.example.ai.dto.LiveQuizVO;
import com.example.ai.dto.LiveReportVO;
import com.example.ai.dto.LiveSessionVO;
import com.example.ai.dto.LiveSlideAddDTO;
import com.example.ai.dto.LiveSlideBatchDTO;
import com.example.ai.dto.LiveSlideVO;
import com.example.ai.dto.LiveStartDTO;
import com.example.ai.dto.LiveStateVO;
import com.example.ai.dto.LiveStudentVO;
import com.example.ai.dto.LiveWsTicketVO;
import com.example.ai.security.CurrentUser;
import com.example.ai.security.LoginUser;
import com.example.ai.service.CoursewarePublishService;
import com.example.ai.service.LiveService;
import com.example.ai.websocket.LiveWebSocketHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Tag(name = "课堂接口")
@RestController
@RequestMapping("/live")
@RequiredArgsConstructor
public class LiveController {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LiveService liveService;
    private final CoursewarePublishService coursewarePublishService;
    private final LiveWebSocketHandler liveWebSocketHandler;

    @Operation(summary = "开始上课(自动发布签到)")
    @AuditAction(module = "课堂", action = "开始上课")
    @PostMapping("/start")
    public Result<LiveSessionVO> start(@Valid @RequestBody LiveStartDTO dto, @CurrentUser LoginUser loginUser) {
        LiveSessionVO vo = liveService.start(dto, loginUser.id(), loginUser.role());
        liveWebSocketHandler.broadcast(vo.getId());
        return Result.success(vo);
    }

    @Operation(summary = "下课")
    @AuditAction(module = "课堂", action = "下课")
    @PutMapping("/end/{sessionId}")
    public Result<Void> end(@PathVariable Long sessionId,
                            @RequestBody(required = false) LiveEndDTO dto,
                            @CurrentUser LoginUser loginUser) {
        boolean publishCourseware = dto != null && Boolean.TRUE.equals(dto.getPublishCourseware());
        liveService.end(sessionId, loginUser.id(), loginUser.role(), publishCourseware);
        liveWebSocketHandler.broadcast(sessionId);
        return Result.success();
    }

    @Operation(summary = "下课课件发布预览(弹窗用)")
    @GetMapping("/end-preview/{sessionId}")
    public Result<LiveEndPreviewVO> endPreview(@PathVariable Long sessionId, @CurrentUser LoginUser loginUser) {
        return Result.success(coursewarePublishService.preview(sessionId, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "发布本节课件给学生(下课后补发)")
    @AuditAction(module = "课堂", action = "发布课件")
    @PostMapping("/courseware/publish/{sessionId}")
    public Result<Long> publishCourseware(@PathVariable Long sessionId, @CurrentUser LoginUser loginUser) {
        return Result.success(coursewarePublishService.publish(sessionId, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "学生扫码进入课堂(自动签到)")
    @PostMapping("/join")
    public Result<LiveStateVO> join(@Valid @RequestBody LiveJoinDTO dto, @CurrentUser LoginUser loginUser) {
        LiveStateVO vo = liveService.join(dto, loginUser.id(), loginUser.role());
        liveWebSocketHandler.broadcast(vo.getSessionId());
        return Result.success(vo);
    }

    @Operation(summary = "课堂实时状态(轮询)")
    @GetMapping("/state/{sessionId}")
    public Result<LiveStateVO> state(@PathVariable Long sessionId, @CurrentUser LoginUser loginUser) {
        return Result.success(liveService.getState(sessionId, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "教师翻页")
    @PutMapping("/page")
    public Result<LiveStateVO> turnPage(@Valid @RequestBody LivePageDTO dto, @CurrentUser LoginUser loginUser) {
        LiveStateVO vo = liveService.turnPage(dto, loginUser.id(), loginUser.role());
        liveWebSocketHandler.broadcast(dto.getSessionId());
        return Result.success(vo);
    }

    @Operation(summary = "课堂发布随堂测验")
    @AuditAction(module = "课堂", action = "发布随堂测验")
    @PostMapping("/quiz/publish/{sessionId}")
    public Result<LiveQuizVO> publishQuiz(@PathVariable Long sessionId,
                                          @RequestBody LiveQuizPublishDTO dto,
                                          @CurrentUser LoginUser loginUser) {
        LiveQuizVO vo = liveService.publishQuiz(sessionId, dto, loginUser.id(), loginUser.role());
        liveWebSocketHandler.broadcast(sessionId);
        return Result.success(vo);
    }

    @Operation(summary = "结束随堂测验")
    @AuditAction(module = "课堂", action = "结束随堂测验")
    @PostMapping("/quiz/end/{sessionId}")
    public Result<Void> endQuiz(@PathVariable Long sessionId, @CurrentUser LoginUser loginUser) {
        liveService.endQuiz(sessionId, loginUser.id(), loginUser.role());
        liveWebSocketHandler.broadcast(sessionId);
        return Result.success();
    }

    @Operation(summary = "课件页列表")
    @GetMapping("/slides/{sessionId}")
    public Result<List<LiveSlideVO>> slides(@PathVariable Long sessionId, @CurrentUser LoginUser loginUser) {
        return Result.success(liveService.getSlides(sessionId, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "添加课件页")
    @AuditAction(module = "课堂", action = "添加课件页")
    @PostMapping("/slide/add")
    public Result<LiveSlideVO> addSlide(@Valid @RequestBody LiveSlideAddDTO dto, @CurrentUser LoginUser loginUser) {
        LiveSlideVO vo = liveService.addSlide(dto, loginUser.id(), loginUser.role());
        liveWebSocketHandler.broadcast(dto.getSessionId());
        return Result.success(vo);
    }

    @Operation(summary = "批量添加课件页")
    @AuditAction(module = "课堂", action = "批量添加课件页")
    @PostMapping("/slide/batch")
    public Result<List<LiveSlideVO>> addSlides(@Valid @RequestBody LiveSlideBatchDTO dto, @CurrentUser LoginUser loginUser) {
        List<LiveSlideVO> list = liveService.addSlides(dto, loginUser.id(), loginUser.role());
        liveWebSocketHandler.broadcast(dto.getSessionId());
        return Result.success(list);
    }

    @Operation(summary = "删除课件页")
    @AuditAction(module = "课堂", action = "删除课件页")
    @DeleteMapping("/slide/{slideId}")
    public Result<Void> deleteSlide(@PathVariable Long slideId, @CurrentUser LoginUser loginUser) {
        Long sessionId = liveService.deleteSlide(slideId, loginUser.id(), loginUser.role());
        liveWebSocketHandler.broadcast(sessionId);
        return Result.success();
    }

    @Operation(summary = "课堂小程序码")
    @GetMapping("/qrcode/{sessionId}")
    public Result<String> wxacode(@PathVariable Long sessionId, @CurrentUser LoginUser loginUser) {
        return Result.success(liveService.getWxacode(sessionId, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "课程进行中的课堂")
    @GetMapping("/current/{courseId}")
    public Result<LiveSessionVO> current(@PathVariable Long courseId, @CurrentUser LoginUser loginUser) {
        return Result.success(liveService.current(courseId, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "进行中的课堂列表(学生动态-课堂)")
    @GetMapping("/active")
    public Result<List<LiveSessionVO>> active(@CurrentUser LoginUser loginUser) {
        return Result.success(liveService.listActive(loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "课堂心跳(在线状态上报)")
    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@Valid @RequestBody LiveHeartbeatDTO dto, @CurrentUser LoginUser loginUser) {
        liveService.heartbeat(dto.getSessionId(), loginUser.id(), loginUser.role());
        liveWebSocketHandler.broadcastThrottled(dto.getSessionId());
        return Result.success();
    }

    @Operation(summary = "课堂在线学生名单")
    @GetMapping("/online/{sessionId}")
    public Result<List<LiveStudentVO>> online(@PathVariable Long sessionId, @CurrentUser LoginUser loginUser) {
        return Result.success(liveService.onlineStudents(sessionId, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "课程历史课堂")
    @GetMapping("/history/{courseId}")
    public Result<List<LiveSessionVO>> history(@PathVariable Long courseId, @CurrentUser LoginUser loginUser) {
        return Result.success(liveService.history(courseId, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "课堂课后报告")
    @GetMapping("/report/{sessionId}")
    public Result<LiveReportVO> report(@PathVariable Long sessionId, @CurrentUser LoginUser loginUser) {
        return Result.success(liveService.report(sessionId, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "学生举手/取消举手")
    @PostMapping("/hand/{sessionId}")
    public Result<Boolean> hand(@PathVariable Long sessionId, @CurrentUser LoginUser loginUser) {
        boolean raised = liveService.toggleHand(sessionId, loginUser.id(), loginUser.role());
        liveWebSocketHandler.broadcastThrottled(sessionId);
        return Result.success(raised);
    }

    @Operation(summary = "学生课堂提问")
    @PostMapping("/question")
    public Result<Long> question(@Valid @RequestBody LiveQuestionDTO dto, @CurrentUser LoginUser loginUser) {
        Long id = liveService.askQuestion(dto, loginUser.id(), loginUser.role());
        liveWebSocketHandler.broadcastThrottled(dto.getSessionId());
        return Result.success(id);
    }

    @Operation(summary = "教师查看待处理互动(举手/提问)")
    @GetMapping("/interactions/{sessionId}")
    public Result<List<LiveInteractionVO>> interactions(@PathVariable Long sessionId,
                                                        @CurrentUser LoginUser loginUser) {
        return Result.success(liveService.interactions(sessionId, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "教师处理互动")
    @PutMapping("/interaction/{id}/resolve")
    public Result<Void> resolveInteraction(@PathVariable Long id, @CurrentUser LoginUser loginUser) {
        Long sessionId = liveService.resolveInteraction(id, loginUser.id(), loginUser.role());
        liveWebSocketHandler.broadcastThrottled(sessionId);
        return Result.success();
    }

    @Operation(summary = "导出课堂课后报告")
    @GetMapping("/report/{sessionId}/export")
    public void exportReport(@PathVariable Long sessionId, @CurrentUser LoginUser loginUser,
                             HttpServletResponse response) throws IOException {
        LiveReportVO report = liveService.report(sessionId, loginUser.id(), loginUser.role());
        response.setContentType("text/csv;charset=UTF-8");
        String rawName = (report.getCourseName() == null ? "课堂" : report.getCourseName()) + "课堂报告";
        String fileName = URLEncoder.encode(rawName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".csv");

        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        appendCsvRow(sb, "课堂报告");
        appendCsvRow(sb, "课程", report.getCourseName());
        appendCsvRow(sb, "课堂标题", report.getTitle());
        appendCsvRow(sb, "授课教师", report.getTeacherName());
        appendCsvRow(sb, "开始时间", formatTime(report.getStartTime()));
        appendCsvRow(sb, "结束时间", formatTime(report.getEndTime()));
        appendCsvRow(sb, "课堂时长(分钟)", report.getDurationMinutes());
        appendCsvRow(sb, "课件页数", report.getSlideTotal());
        appendCsvRow(sb, "应到人数", report.getTotalCount());
        appendCsvRow(sb, "签到人数", report.getCheckedCount());
        appendCsvRow(sb, "迟到人数", report.getLateCount());
        appendCsvRow(sb, "未签到人数", report.getAbsentCount());
        appendCsvRow(sb, "在线人数", report.getOnlineCount());
        sb.append("\r\n");
        appendCsvRow(sb, "学号", "姓名", "是否在线", "是否签到", "是否迟到", "签到时间");
        if (report.getStudents() != null) {
            for (LiveStudentVO student : report.getStudents()) {
                appendCsvRow(sb,
                        student.getStudentNo(),
                        student.getName(),
                        student.getOnline() != null && student.getOnline() ? "在线" : "离线",
                        student.getChecked() != null && student.getChecked() ? "已签到" : "未签到",
                        student.getLate() != null && student.getLate() ? "迟到" : "",
                        formatTime(student.getCheckinTime()));
            }
        }
        response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }

    private static void appendCsvRow(StringBuilder sb, Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(csvCell(cells[i]));
        }
        sb.append("\r\n");
    }

    private static String csvCell(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String formatTime(java.time.LocalDateTime time) {
        return time == null ? "" : time.format(TIME_FORMATTER);
    }

    @Operation(summary = "获取课堂 WebSocket 一次性连接凭证")
    @PostMapping("/ws-ticket/{sessionId}")
    public Result<LiveWsTicketVO> wsTicket(@PathVariable Long sessionId, @CurrentUser LoginUser loginUser) {
        return Result.success(liveService.createWsTicket(sessionId, loginUser.id(), loginUser.role()));
    }
}