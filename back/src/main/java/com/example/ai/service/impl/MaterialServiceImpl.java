package com.example.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ai.common.BizException;
import com.example.ai.common.ErrorCode;
import com.example.ai.dto.MaterialCreateDTO;
import com.example.ai.dto.MaterialVO;
import com.example.ai.entity.Course;
import com.example.ai.entity.Material;
import com.example.ai.entity.User;
import com.example.ai.mapper.MaterialMapper;
import com.example.ai.mapper.UserMapper;
import com.example.ai.security.FileUrlSigner;
import com.example.ai.security.Roles;
import com.example.ai.service.CourseAccessService;
import com.example.ai.service.MaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private final MaterialMapper materialMapper;
    private final UserMapper userMapper;
    private final CourseAccessService courseAccessService;
    private final FileUrlSigner fileUrlSigner;

    @Override
    public MaterialVO createMaterial(MaterialCreateDTO dto, Long userId, String role) {
        Course course = courseAccessService.requireTeacher(dto.getCourseId(), userId, role);

        Material material = new Material();
        material.setCourseId(dto.getCourseId());
        material.setTitle(dto.getTitle().trim());
        material.setDescription(dto.getDescription());
        material.setFileUrl(fileUrlSigner.normalize(dto.getFileUrl()));
        material.setFileName(dto.getFileName());
        material.setFileSize(dto.getFileSize());
        material.setFileType(dto.getFileType() != null ? dto.getFileType() : detectType(dto.getFileName()));
        material.setStatus(1);
        material.setTeacherId(userId);
        materialMapper.insert(material);

        MaterialVO vo = new MaterialVO();
        BeanUtils.copyProperties(material, vo);
        vo.setFileUrl(fileUrlSigner.sign(material.getFileUrl()));
        vo.setTeacherName(resolveTeacherName(course, userId));
        return vo;
    }

    @Override
    public List<MaterialVO> getCourseMaterials(Long courseId, Long userId, String role) {
        Course course = courseAccessService.requireMember(courseId, userId, role);
        boolean isTeacher = Roles.TEACHER.equals(role);

        List<Material> list = materialMapper.selectList(
                new LambdaQueryWrapper<Material>()
                        .eq(Material::getCourseId, courseId)
                        .eq(!isTeacher, Material::getStatus, 1)
                        .orderByDesc(Material::getCreateTime)
        );

        List<MaterialVO> result = new ArrayList<>();
        String teacherName = resolveTeacherName(course, course.getTeacherId());
        for (Material m : list) {
            MaterialVO vo = new MaterialVO();
            BeanUtils.copyProperties(m, vo);
            vo.setFileUrl(fileUrlSigner.sign(m.getFileUrl()));
            vo.setTeacherName(teacherName);
            result.add(vo);
        }
        return result;
    }

    @Override
    public void deleteMaterial(Long materialId, Long userId, String role) {
        Material material = getMaterialOrThrow(materialId);
        courseAccessService.requireTeacher(material.getCourseId(), userId, role);
        materialMapper.deleteById(materialId);
    }

    @Override
    public void toggleStatus(Long materialId, Long userId, String role) {
        Material material = getMaterialOrThrow(materialId);
        courseAccessService.requireTeacher(material.getCourseId(), userId, role);
        material.setStatus(material.getStatus() != null && material.getStatus() == 1 ? 0 : 1);
        materialMapper.updateById(material);
    }

    private Material getMaterialOrThrow(Long materialId) {
        Material material = materialMapper.selectById(materialId);
        if (material == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "课件不存在");
        }
        return material;
    }

    private String resolveTeacherName(Course course, Long teacherId) {
        if (course.getTeacherName() != null && !course.getTeacherName().isEmpty()) {
            return course.getTeacherName();
        }
        User teacher = userMapper.selectById(teacherId);
        if (teacher == null) {
            return "";
        }
        return teacher.getNickname() != null ? teacher.getNickname() : teacher.getUsername();
    }

    private String detectType(String fileName) {
        if (fileName == null) {
            return "other";
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "pdf";
        }
        if (lower.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp)$")) {
            return "image";
        }
        if (lower.matches(".*\\.(ppt|pptx)$")) {
            return "ppt";
        }
        if (lower.matches(".*\\.(doc|docx)$")) {
            return "word";
        }
        if (lower.matches(".*\\.(xls|xlsx)$")) {
            return "excel";
        }
        return "other";
    }
}