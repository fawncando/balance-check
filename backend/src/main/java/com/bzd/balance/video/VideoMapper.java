package com.bzd.balance.video;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VideoMapper {

    @Insert("""
            INSERT INTO video (original_name, stored_name, relative_path, size_bytes, sha256, content_type,
                               uploader_id, balance_id, status, message)
            VALUES (#{originalName}, #{storedName}, #{relativePath}, #{sizeBytes}, #{sha256}, #{contentType},
                    #{uploaderId}, #{balanceId}, #{status}, #{message})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Video video);

    @Select("""
            SELECT v.id, v.original_name, v.stored_name, v.size_bytes, v.balance_id, b.code AS balance_code,
                   u.display_name AS uploader_name, v.uploaded_at, v.status, v.message
            FROM video v
            JOIN sys_user u ON u.id = v.uploader_id
            LEFT JOIN balance b ON b.id = v.balance_id
            ORDER BY v.id DESC
            """)
    List<VideoView> findAll();

    @Select("""
            SELECT v.id, v.original_name, v.stored_name, v.size_bytes, v.balance_id, b.code AS balance_code,
                   u.display_name AS uploader_name, v.uploaded_at, v.status, v.message
            FROM video v
            JOIN sys_user u ON u.id = v.uploader_id
            LEFT JOIN balance b ON b.id = v.balance_id
            WHERE v.uploader_id = #{uploaderId}
            ORDER BY v.id DESC
            """)
    List<VideoView> findByUploader(Long uploaderId);

    @Select("SELECT * FROM video WHERE id = #{id}")
    Video findById(Long id);

    @Select("""
            SELECT v.id, v.original_name, v.stored_name, v.size_bytes, v.balance_id, b.code AS balance_code,
                   u.display_name AS uploader_name, v.uploaded_at, v.status, v.message
            FROM video v
            JOIN sys_user u ON u.id = v.uploader_id
            LEFT JOIN balance b ON b.id = v.balance_id
            WHERE v.id = #{id}
            """)
    VideoView findViewById(Long id);

    @Delete("DELETE FROM video WHERE id = #{id}")
    void deleteById(@Param("id") Long id);
}
