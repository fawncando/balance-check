package com.bzd.balance.auth;

public class SessionUser {

    private Long id;
    private String username;
    private String displayName;
    private String role;
    private String cardNo;

    public SessionUser() {
    }

    public SessionUser(Long id, String username, String displayName, String role, String cardNo) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.cardNo = cardNo;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isReviewer() {
        return "REVIEWER".equals(role);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }
}
