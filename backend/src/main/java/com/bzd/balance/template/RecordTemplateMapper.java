package com.bzd.balance.template;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RecordTemplateMapper {

    @Insert("""
            INSERT INTO record_template (name, version, content, creator_id, enabled)
            VALUES (#{name}, #{version}, #{content}, #{creatorId}, #{enabled})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(RecordTemplate template);

    @Select("SELECT COUNT(*) FROM record_template WHERE name = #{name} AND version = #{version}")
    int countByNameVersion(@Param("name") String name, @Param("version") String version);

    @Select("SELECT * FROM record_template WHERE id = #{id}")
    RecordTemplate findById(Long id);

    @Select("""
            SELECT t.id, t.name, t.version, t.content, t.enabled, t.created_at,
                   u.display_name AS creator_name
            FROM record_template t
            LEFT JOIN sys_user u ON u.id = t.creator_id
            ORDER BY t.name, t.created_at DESC, t.id DESC
            """)
    List<RecordTemplateView> findAll();

    @Select("""
            SELECT t.id, t.name, t.version, t.content, t.enabled, t.created_at,
                   u.display_name AS creator_name
            FROM record_template t
            LEFT JOIN sys_user u ON u.id = t.creator_id
            WHERE t.id = #{id}
            """)
    RecordTemplateView findView(Long id);

    @Update("UPDATE record_template SET enabled = #{enabled} WHERE id = #{id}")
    int updateEnabled(@Param("id") Long id, @Param("enabled") boolean enabled);

    /** 同一模板名称下只保留一个启用版本。 */
    @Update("UPDATE record_template SET enabled = 0 WHERE name = #{name} AND id <> #{id}")
    void disableOthers(@Param("name") String name, @Param("id") Long id);
}
