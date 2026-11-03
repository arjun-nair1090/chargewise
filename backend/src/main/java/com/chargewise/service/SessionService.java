package com.chargewise.service;

import com.chargewise.dto.PageResponse;
import com.chargewise.dto.SessionResponse;
import com.chargewise.entity.SessionStatus;
import com.chargewise.exception.ApiException;
import com.chargewise.repository.ChargingSessionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    private final ChargingSessionRepository sessions;

    public SessionService(ChargingSessionRepository sessions) {
        this.sessions = sessions;
    }

    /** A driver's own session; anyone else's is reported as missing, never as forbidden. */
    @Transactional(readOnly = true)
    public SessionResponse get(Long userId, Long id) {
        return SessionResponse.from(sessions.findByIdAndBookingUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Session")));
    }

    @Transactional(readOnly = true)
    public PageResponse<SessionResponse> history(Long userId, int page, int size) {
        return PageResponse.from(sessions.findAllByBookingUserIdOrderByIdDesc(userId, PageRequest.of(page, size))
                .map(SessionResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<SessionResponse> listAll(SessionStatus status, int page, int size) {
        PageRequest request = PageRequest.of(page, size);
        return PageResponse.from((status == null ? sessions.findAllByOrderByIdDesc(request)
                : sessions.findAllByStatusOrderByIdDesc(status, request)).map(SessionResponse::from));
    }
}
