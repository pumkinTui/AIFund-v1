package com.fund.assistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.assistant.dto.UserDTO;
import com.fund.assistant.util.JwtUtil;
import com.fund.assistant.util.Result;
import com.fund.assistant.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        // 放行 OPTIONS 跨域预检请求
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        // 从请求头获取 token
        String token = request.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            writeErrorResponse(response, 401, "请先登录");
            return false;
        }

        // 去掉 Bearer 前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 校验 token
        if (!jwtUtil.validateToken(token)) {
            writeErrorResponse(response, 401, "token无效或已过期，请重新登录");
            return false;
        }

        // 解析 token 获取用户信息
        Long userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);
        String uid = ""; // 若 JWT 中无 uid，可置空，或后续从数据库查询后补充

        // 构建 UserDTO 并存入 ThreadLocal
        UserDTO userDTO = new UserDTO();
        userDTO.setId(userId);
        userDTO.setUsername(username);
        userDTO.setUid(uid);
        // 如需 nickName、icon 等信息，可在此从数据库加载或扩展 JWT payload
        UserContext.saveUser(userDTO);

        return true;    // 放行请求
    }

    /**
     * 往 HttpServletResponse 中写入 JSON 错误信息
     */
    private void writeErrorResponse(HttpServletResponse response, int httpStatus, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.error(message);
        response.getWriter().write(new ObjectMapper().writeValueAsString(result));
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 必须清除 ThreadLocal，防止内存泄漏
        UserContext.removeUser();
    }
}