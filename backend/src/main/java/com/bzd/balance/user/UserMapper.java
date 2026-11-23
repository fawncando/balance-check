package com.bzd.balance.user;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    @Select("""
            SELECT id, username, password_hash, display_name, role, card_no, status, created_at
            FROM sys_user
            WHERE username = #{username} AND status = 'ACTIVE'
            """)
    User findByUsername(String username);

    @Select("""
            SELECT id, username, display_name, role, card_no, status, created_at
            FROM sys_user
            ORDER BY id
            """)
    List<User> findAll();

    @Select("""
            SELECT id, username, display_name, role, card_no, status, created_at
            FROM sys_user WHERE id = #{id}
            """)
    User findById(Long id);

    @Select("""
            SELECT id FROM sys_user
            WHERE card_no = #{cardNo} AND id <> #{userId}
            LIMIT 1
            """)
    Long findOtherUserIdByCard(@Param("cardNo") String cardNo,
                               @Param("userId") Long userId);

    @Select("SELECT id FROM sys_user WHERE username = #{username} LIMIT 1")
    Long findIdByUsername(@Param("username") String username);

    @Insert("""
            INSERT INTO sys_user
                (username, password_hash, display_name, role, status)
            VALUES
                (#{username}, #{passwordHash}, #{displayName}, #{role}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(User user);

    @Update("""
            UPDATE sys_user
            SET display_name = #{displayName}, role = #{role}, status = #{status}
            WHERE id = #{id}
            """)
    int updateProfile(User user);

    @Update("UPDATE sys_user SET password_hash = #{passwordHash} WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    @Select("""
            SELECT COUNT(*) FROM sys_user
            WHERE role = 'ADMIN' AND status = 'ACTIVE' AND id <> #{userId}
            """)
    int countOtherActiveAdmins(@Param("userId") Long userId);

    @Update("UPDATE sys_user SET card_no = #{cardNo} WHERE id = #{id}")
    int updateCardNo(@Param("id") Long id, @Param("cardNo") String cardNo);
}
