package com.bzd.balance.user;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

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
}
