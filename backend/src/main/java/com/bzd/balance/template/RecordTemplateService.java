package com.bzd.balance.template;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.bzd.balance.auth.SessionUser;
import com.bzd.balance.common.BizException;
import com.bzd.balance.log.AuditService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 记录表模板管理：只维护版本与启停状态，模板内容暂以 {note} 结构保存说明文字。
 * 记录表导出仍使用内置版式，启用版本在导出页脚与系统提示中体现。
 */
@Service
public class RecordTemplateService {

    private static final TypeReference<Map<String, Object>> CONTENT_TYPE =
            new TypeReference<Map<String, Object>>() { };

    private final RecordTemplateMapper recordTemplateMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public RecordTemplateService(RecordTemplateMapper recordTemplateMapper,
                                 AuditService auditService,
                                 ObjectMapper objectMapper) {
        this.recordTemplateMapper = recordTemplateMapper;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<RecordTemplateView> list(SessionUser user) {
        ensureAdmin(user);
        List<RecordTemplateView> rows = recordTemplateMapper.findAll();
        for (RecordTemplateView row : rows) {
            row.setNote(noteOf(row.getContent()));
        }
        return rows;
    }

    public RecordTemplateView create(RecordTemplateRequest request, SessionUser user, String ip) {
        ensureAdmin(user);
        if (request == null) {
            throw new BizException("请填写模板信息");
        }
        String name = requireText(request.getName(), "请填写模板名称", 100);
        String version = requireText(request.getVersion(), "请填写模板版本", 20);
        String note = trimToNull(request.getNote());
        if (note != null && note.length() > 500) {
            throw new BizException("模板说明不能超过 500 个字符");
        }
        if (recordTemplateMapper.countByNameVersion(name, version) > 0) {
            throw new BizException("同名同版本的模板已存在，请换一个版本号");
        }

        RecordTemplate template = new RecordTemplate();
        template.setName(name);
        template.setVersion(version);
        template.setContent(contentOf(note));
        template.setCreatorId(user.getId());
        template.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        recordTemplateMapper.insert(template);
        if (Boolean.TRUE.equals(template.getEnabled())) {
            recordTemplateMapper.disableOthers(name, template.getId());
        }
        auditService.log(user.getId(), "TEMPLATE_CREATE", "TEMPLATE", String.valueOf(template.getId()),
                name + " " + version, ip);
        return viewOf(template.getId());
    }

    public RecordTemplateView setEnabled(Long id, boolean enabled, SessionUser user, String ip) {
        ensureAdmin(user);
        RecordTemplate template = recordTemplateMapper.findById(id);
        if (template == null) {
            throw new BizException(404, "模板不存在");
        }
        recordTemplateMapper.updateEnabled(id, enabled);
        if (enabled) {
            recordTemplateMapper.disableOthers(template.getName(), id);
        }
        auditService.log(user.getId(), enabled ? "TEMPLATE_ENABLE" : "TEMPLATE_DISABLE",
                "TEMPLATE", String.valueOf(id), template.getName() + " " + template.getVersion(), ip);
        return viewOf(id);
    }

    private RecordTemplateView viewOf(Long id) {
        RecordTemplateView view = recordTemplateMapper.findView(id);
        if (view == null) {
            throw new BizException(404, "模板不存在");
        }
        view.setNote(noteOf(view.getContent()));
        return view;
    }

    private String contentOf(String note) {
        try {
            return objectMapper.writeValueAsString(Map.of("note", note == null ? "" : note));
        } catch (Exception ex) {
            throw new BizException("模板说明格式不正确");
        }
    }

    /** 历史数据或手工改过的 content 读不出说明时按空处理，不影响列表。 */
    private String noteOf(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        try {
            Map<String, Object> map = objectMapper.readValue(content, CONTENT_TYPE);
            Object note = map.get("note");
            return note == null ? "" : String.valueOf(note);
        } catch (Exception ex) {
            return "";
        }
    }

    private void ensureAdmin(SessionUser user) {
        if (!user.isAdmin()) {
            throw new BizException(403, "只有管理员可以维护记录表模板");
        }
    }

    private String requireText(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BizException(message);
        }
        String text = value.trim();
        if (text.length() > maxLength) {
            throw new BizException(message.replace("请填写", "") + "不能超过 " + maxLength + " 个字符");
        }
        return text;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
