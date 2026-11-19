package com.example.ai.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.annotation.ExcelProperty;
import com.example.ai.common.Result;
import com.example.ai.dto.*;
import com.example.ai.security.CurrentUser;
import com.example.ai.security.LoginUser;
import com.example.ai.service.ScoreRuleService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/score")
public class ScoreController {

    @Autowired
    private ScoreRuleService scoreRuleService;

    @GetMapping("/course/{courseId}")
    public Result<List<StudentScoreVO>> getCourseScores(@PathVariable Long courseId, @CurrentUser LoginUser loginUser) {
        return Result.success(scoreRuleService.getCourseScores(courseId, loginUser.id(), loginUser.role()));
    }

    @GetMapping("/my/{courseId}")
    public Result<MyScoreVO> getMyScore(@PathVariable Long courseId, @CurrentUser LoginUser loginUser) {
        return Result.success(scoreRuleService.getMyScore(courseId, loginUser.id(), loginUser.role()));
    }

    @GetMapping("/detail/{courseId}/{studentId}")
    public Result<ScoreDetailVO> getScoreDetail(@PathVariable Long courseId, @PathVariable Long studentId,
                                                @CurrentUser LoginUser loginUser) {
        return Result.success(scoreRuleService.getScoreDetail(courseId, studentId, loginUser.id(), loginUser.role()));
    }

    @GetMapping("/export/{courseId}")
    public void export(@PathVariable Long courseId,
                       @RequestParam(value = "sort", required = false, defaultValue = "studentNo") String sort,
                       @CurrentUser LoginUser loginUser,
                       HttpServletResponse response) throws IOException {
        List<StudentScoreVO> scores = "score".equals(sort)
                ? scoreRuleService.getCourseScores(courseId, loginUser.id(), loginUser.role())
                : scoreRuleService.getCourseScoresSortedByStudentNo(courseId, loginUser.id(), loginUser.role());
        List<ScoreDetailExportVO> details = scoreRuleService.getExportDetailRows(courseId, loginUser.id(), loginUser.role());

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        String fileName = URLEncoder.encode(buildFileName(scoreRuleService.getCourseName(courseId)),
                StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

        List<ScoreExcelRow> rows = new ArrayList<>();
        for (StudentScoreVO s : scores) {
            ScoreExcelRow row = new ScoreExcelRow();
            row.setStudentNo(s.getStudentNo());
            row.setStudentName(s.getStudentName());
            row.setRank(s.getRank());
            row.setCheckinCount(s.getCheckinCount());
            row.setTotalCheckin(s.getTotalCheckin());
            row.setCheckinScore(s.getCheckinScore());
            row.setHomeworkScore(s.getHomeworkScore());
            row.setQuizCount(s.getQuizCount());
            row.setTotalQuiz(s.getTotalQuiz());
            row.setQuizScore(s.getQuizScore());
            row.setCheckinWeight(s.getCheckinWeight());
            row.setHomeworkWeight(s.getHomeworkWeight());
            row.setQuizWeight(s.getQuizWeight());
            row.setTotalScore(s.getTotalScore());
            rows.add(row);
        }

        List<ScoreDetailExcelRow> detailRows = new ArrayList<>();
        for (ScoreDetailExportVO d : details) {
            ScoreDetailExcelRow row = new ScoreDetailExcelRow();
            row.setStudentNo(d.getStudentNo());
            row.setStudentName(d.getStudentName());
            row.setType(d.getType());
            row.setTitle(d.getTitle());
            row.setFullScore(d.getFullScore());
            row.setScore(d.getScore());
            row.setRate(d.getRate());
            row.setStatus(d.getStatus());
            row.setTime(d.getTime());
            detailRows.add(row);
        }

        ExcelWriter writer = EasyExcel.write(response.getOutputStream()).build();
        try {
            writer.write(rows, EasyExcel.writerSheet(0, "成绩汇总").head(ScoreExcelRow.class).build());
            writer.write(detailRows, EasyExcel.writerSheet(1, "成绩明细").head(ScoreDetailExcelRow.class).build());
        } finally {
            writer.finish();
        }
    }

    private String buildFileName(String courseName) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        if (courseName == null || courseName.isBlank()) {
            return "成绩表_" + date;
        }
        return courseName.replaceAll("[\\\\/:*?\"<>|]", "_") + "_成绩表_" + date;
    }

    @Data
    public static class ScoreExcelRow {
        @ExcelProperty("学号")
        private String studentNo;
        @ExcelProperty("姓名")
        private String studentName;
        @ExcelProperty("排名")
        private Integer rank;
        @ExcelProperty("签到次数")
        private Integer checkinCount;
        @ExcelProperty("总签到次数")
        private Integer totalCheckin;
        @ExcelProperty("签到得分")
        private BigDecimal checkinScore;
        @ExcelProperty("作业得分")
        private BigDecimal homeworkScore;
        @ExcelProperty("测验次数")
        private Integer quizCount;
        @ExcelProperty("总测验次数")
        private Integer totalQuiz;
        @ExcelProperty("测验得分")
        private BigDecimal quizScore;
        @ExcelProperty("签到占比(%)")
        private Integer checkinWeight;
        @ExcelProperty("作业占比(%)")
        private Integer homeworkWeight;
        @ExcelProperty("测验占比(%)")
        private Integer quizWeight;
        @ExcelProperty("总评成绩")
        private BigDecimal totalScore;
    }

    @Data
    public static class ScoreDetailExcelRow {
        @ExcelProperty("学号")
        private String studentNo;
        @ExcelProperty("姓名")
        private String studentName;
        @ExcelProperty("类型")
        private String type;
        @ExcelProperty("项目")
        private String title;
        @ExcelProperty("满分")
        private BigDecimal fullScore;
        @ExcelProperty("得分")
        private BigDecimal score;
        @ExcelProperty("得分率(%)")
        private BigDecimal rate;
        @ExcelProperty("状态")
        private String status;
        @ExcelProperty("时间")
        private String time;
    }
}