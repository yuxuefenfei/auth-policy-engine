package com.example.authengine.domain;

/**
 * 服务端认证器类型：PASSKEY 对应 WebAuthn，而不是上传原始指纹。
 */
public enum FactorType {
    PASSWORD, SMS_OTP, APP_QR_APPROVAL, PASSKEY
}
