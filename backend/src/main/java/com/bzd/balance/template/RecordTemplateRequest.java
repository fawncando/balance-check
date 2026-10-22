package com.bzd.balance.template;

/** 新建模板版本：content 在服务端按 {note} 结构生成，前端只提交说明文字。 */
public class RecordTemplateRequest {

    private String name;
    private String version;
    private String note;
    private Boolean enabled;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
