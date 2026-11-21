package com.bzd.balance.video;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
                   c.id AS calibration_id,
                   u.display_name AS uploader_name, v.uploaded_at, v.status, v.message,
                   v.person_card, v.identity_verified, v.analysis_profile,
                   v.analysis_confidence, v.anomaly_frames
            FROM video v
            JOIN sys_user u ON u.id = v.uploader_id
            LEFT JOIN balance b ON b.id = v.balance_id
            LEFT JOIN calibration c ON c.video_id = v.id
            ORDER BY v.id DESC
            """)
    List<VideoView> findAll();

    @Select("""
            SELECT v.id, v.original_name, v.stored_name, v.size_bytes, v.balance_id, b.code AS balance_code,
                   c.id AS calibration_id,
                   u.display_name AS uploader_name, v.uploaded_at, v.status, v.message,
                   v.person_card, v.identity_verified, v.analysis_profile,
                   v.analysis_confidence, v.anomaly_frames
            FROM video v
            JOIN sys_user u ON u.id = v.uploader_id
            LEFT JOIN balance b ON b.id = v.balance_id
            LEFT JOIN calibration c ON c.video_id = v.id
            WHERE v.uploader_id = #{uploaderId}
            ORDER BY v.id DESC
            """)
    List<VideoView> findByUploader(Long uploaderId);

    @Select("SELECT * FROM video WHERE id = #{id}")
    Video findById(Long id);

    @Select("""
            SELECT v.id, v.original_name, v.stored_name, v.size_bytes, v.balance_id, b.code AS balance_code,
                   c.id AS calibration_id,
                   u.display_name AS uploader_name, v.uploaded_at, v.status, v.message,
                   v.person_card, v.identity_verified, v.analysis_profile,
                   v.analysis_confidence, v.anomaly_frames
            FROM video v
            JOIN sys_user u ON u.id = v.uploader_id
            LEFT JOIN balance b ON b.id = v.balance_id
            LEFT JOIN calibration c ON c.video_id = v.id
            WHERE v.id = #{id}
            """)
    VideoView findViewById(Long id);

    @Update("""
            UPDATE video
            SET status = 'ANALYZING', message = '正在识别视频'
            WHERE id = #{id} AND status IN ('UPLOADED', 'FAILED')
            """)
    int markAnalyzing(Long id);

    @Update("""
            UPDATE video
            SET status = #{status}, message = #{message}
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("message") String message);

    @Update("""
            UPDATE video
            SET status = 'ANALYZED', message = #{message},
                person_card = #{personCard}, identity_verified = #{identityVerified},
                analysis_profile = #{profile}, analysis_confidence = #{confidence},
                anomaly_frames = #{anomalyFrames}
            WHERE id = #{id}
            """)
    int completeAnalysis(@Param("id") Long id,
                         @Param("message") String message,
                         @Param("personCard") String personCard,
                         @Param("identityVerified") Boolean identityVerified,
                         @Param("profile") String profile,
                         @Param("confidence") BigDecimal confidence,
                         @Param("anomalyFrames") Integer anomalyFrames);

    @Delete("DELETE FROM video WHERE id = #{id}")
    void deleteById(@Param("id") Long id);
}
