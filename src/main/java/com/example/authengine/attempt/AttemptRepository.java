package com.example.authengine.attempt;

import java.util.Optional;

/**
 * CAS 更新必须是原子的。不能使用 get-then-set 覆盖登录状态。
 */
public interface AttemptRepository {
    boolean insert(LoginAttempt attempt);

    Optional<LoginAttempt> find(String id);

    boolean compareAndSet(String id, long expectedRevision, LoginAttempt next);
}
