package com.example.ai.service;

import com.example.ai.dto.MaterialCreateDTO;
import com.example.ai.dto.MaterialVO;

import java.util.List;

public interface MaterialService {

    MaterialVO createMaterial(MaterialCreateDTO dto, Long userId, String role);

    List<MaterialVO> getCourseMaterials(Long courseId, Long userId, String role);

    void deleteMaterial(Long materialId, Long userId, String role);

    void toggleStatus(Long materialId, Long userId, String role);
}