package com.example.ai.controller;

import com.example.ai.audit.AuditAction;
import com.example.ai.common.Result;
import com.example.ai.dto.MaterialCreateDTO;
import com.example.ai.dto.MaterialVO;
import com.example.ai.security.CurrentUser;
import com.example.ai.security.LoginUser;
import com.example.ai.service.MaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "课件接口")
@RestController
@RequestMapping("/material")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @Operation(summary = "上传课件")
    @AuditAction(module = "课件", action = "上传课件")
    @PostMapping("/create")
    public Result<MaterialVO> create(@Valid @RequestBody MaterialCreateDTO dto, @CurrentUser LoginUser loginUser) {
        return Result.success(materialService.createMaterial(dto, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "课程课件列表")
    @GetMapping("/course/{courseId}")
    public Result<List<MaterialVO>> list(@PathVariable Long courseId, @CurrentUser LoginUser loginUser) {
        return Result.success(materialService.getCourseMaterials(courseId, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "删除课件")
    @AuditAction(module = "课件", action = "删除课件")
    @DeleteMapping("/delete/{materialId}")
    public Result<Void> delete(@PathVariable Long materialId, @CurrentUser LoginUser loginUser) {
        materialService.deleteMaterial(materialId, loginUser.id(), loginUser.role());
        return Result.success();
    }

    @Operation(summary = "上架/下架课件")
    @AuditAction(module = "课件", action = "切换课件状态")
    @PutMapping("/toggle/{materialId}")
    public Result<Void> toggle(@PathVariable Long materialId, @CurrentUser LoginUser loginUser) {
        materialService.toggleStatus(materialId, loginUser.id(), loginUser.role());
        return Result.success();
    }
}