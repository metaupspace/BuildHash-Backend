package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.User;

/**
 * ISP: only AuthService (OTP/Google login flows) needs account lookup-or-create — profile
 * read/write callers never see this surface.
 */
public interface UserAccountService {

    User findOrCreateByPhone(String phone);

    User findOrCreateByGoogle(String googleId, String email, String name);
}
