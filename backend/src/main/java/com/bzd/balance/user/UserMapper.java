package com.bzd.balance.user;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
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

    @Update("UPDATE sys_user SET card_no = #{cardNo} WHERE id = #{id}")
    int updateCardNo(@Param("id") Long id, @Param("cardNo") String cardNo);
}
